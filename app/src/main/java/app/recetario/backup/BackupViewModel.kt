package app.recetario.backup

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.settings.BackupStatus
import app.recetario.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant

/** De dónde sale la copia que se importa: un documento elegido con el selector o una copia del teléfono. */
sealed interface ImportSource {
    data class Document(val uri: String) : ImportSource
    data class Local(val fileName: String) : ImportSource
}

/**
 * Paneles de «Importar copia» y «Restaurar», ya con la copia comprobada: primero se elige el modo (al restaurar,
 * solo «Reemplazar todo»); «Reemplazar todo» pide una segunda confirmación.
 */
sealed interface ImportPanel {
    val source: ImportSource
    val preview: PreviewResult.Ok

    data class ChooseMode(override val source: ImportSource, override val preview: PreviewResult.Ok) : ImportPanel
    data class ConfirmReplace(override val source: ImportSource, override val preview: PreviewResult.Ok) : ImportPanel
}

/** Trabajo en curso; [DRIVE] es la copia automática («Reintentar» o recién elegido el archivo de Drive). */
enum class BackupWork { EXPORTING, CHECKING, IMPORTING, DRIVE }

data class BackupUiState(
    /** False hasta leer el estado de las copias (así no parpadea «Todavía no has guardado…»). */
    val statusLoaded: Boolean = false,
    val lastExternal: Instant? = null,
    val work: BackupWork? = null,
    val panel: ImportPanel? = null,
    /** Copias del teléfono; null hasta leerlas. */
    val localBackups: List<LocalBackup>? = null,
    /** Última copia semanal en el teléfono. */
    val lastLocal: Instant? = null,
    /** Archivo de Drive de la copia automática (null: aún no se eligió) y su última copia. */
    val driveUri: String? = null,
    val lastDrive: Instant? = null,
    /** Por qué falló la copia en Drive, o null. */
    val driveError: String? = null,
    /** Se perdió el permiso del archivo de Drive: solo se puede elegir otro. */
    val driveAccessLost: Boolean = false,
)

class BackupViewModel(
    private val manager: BackupManager,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val documents: BackupDocuments,
    private val savedState: SavedStateHandle,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val work = MutableStateFlow<BackupWork?>(null)
    private val panel = MutableStateFlow<ImportPanel?>(null)
    private val local = MutableStateFlow<List<LocalBackup>?>(null)
    private val _messages = Channel<String>(Channel.BUFFERED)

    /**
     * Copia del teléfono que espera a que el selector cree el documento de «Guardar fuera». Va en el estado
     * guardado: si Android cierra la app con el selector abierto, al volver se sabe qué copia guardar.
     */
    private var pendingSaveOutside: String?
        get() = savedState[PENDING_SAVE_OUTSIDE]
        set(value) {
            savedState[PENDING_SAVE_OUTSIDE] = value
        }

    /** Avisos para el snackbar («Copia guardada.», el resumen de la importación o el error). */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private class DriveCheck(val status: BackupStatus, val accessLost: Boolean)

    /** El estado de las copias y si se conserva el permiso del archivo de Drive; si se perdió, queda anotado (Inicio lo muestra). */
    private val checkedStatus: Flow<DriveCheck> = settings.backupStatus
        .map { s -> DriveCheck(s, accessLost = s.driveUri != null && !documents.hasPersistedAccess(s.driveUri)) }
        .onEach { c -> if (c.accessLost && c.status.lastError == null) recordLostAccess(c.status.driveUri!!) }
        .flowOn(io)

    val state: StateFlow<BackupUiState> = combine(checkedStatus, work, panel, local) { check, w, p, l ->
        val status = check.status
        BackupUiState(
            statusLoaded = true,
            lastExternal = status.lastExternal,
            work = w,
            panel = p,
            localBackups = l,
            lastLocal = status.lastLocal,
            driveUri = status.driveUri,
            lastDrive = status.lastDrive,
            driveError = status.lastError ?: BackupMessages.DRIVE_FAILED.takeIf { check.accessLost },
            driveAccessLost = check.accessLost,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    private suspend fun recordLostAccess(uri: String) {
        try {
            settings.updateBackupStatus { if (it.driveUri == uri && it.lastError == null) it.copy(lastError = BackupMessages.DRIVE_FAILED) else it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo anotar el error de Drive", e)
        }
    }

    init {
        refreshLocal()
    }

    private fun refreshLocal() {
        viewModelScope.launch { local.value = manager.localBackups() }
    }

    fun suggestedFileName(): String = manager.suggestedExportName()

    /**
     * Escribe la copia en el documento que creó el selector (solo cuando ya está comprobada) y anota la fecha.
     * Si falla al escribirla, lo borra.
     */
    fun export(uri: String) {
        if (work.value != null) return
        work.value = BackupWork.EXPORTING
        viewModelScope.launch {
            // Aunque se salga de la pantalla, la copia se termina (o se borra si falla).
            val message = withContext(NonCancellable) {
                val ok = manager.exportToDocument(uri) == ExportResult.Ok
                if (ok) {
                    try {
                        settings.updateBackupStatus { it.copy(lastExternal = clock.instant()) }
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo anotar la fecha de la copia", e)
                    }
                }
                if (ok) BackupMessages.EXPORTED else BackupMessages.EXPORT_FAILED
            }
            work.value = null
            _messages.trySend(message)
        }
    }

    /** «Guardar fuera» de una copia del teléfono: se anota cuál antes de abrir el selector. */
    fun prepareSaveOutside(fileName: String) {
        pendingSaveOutside = fileName
    }

    /**
     * Copia tal cual la copia del teléfono en el documento creado. No cambia «Última copia guardada fuera del
     * teléfono»: esa fecha es la de una copia de lo actual, y esta puede ser antigua.
     */
    fun saveOutside(uri: String) {
        val fileName = pendingSaveOutside
        if (fileName == null) {
            // No se sabe qué copia guardar: el documento recién creado no se queda vacío por ahí.
            viewModelScope.launch {
                withContext(NonCancellable + io) { deleteDocumentIfEmpty(documents, uri) }
                _messages.trySend(BackupMessages.SAVE_OUTSIDE_LOST)
            }
            return
        }
        pendingSaveOutside = null
        if (work.value != null) return
        work.value = BackupWork.EXPORTING
        viewModelScope.launch {
            val ok = withContext(NonCancellable) { writeDocument(uri) { manager.copyLocalTo(fileName, it) } }
            work.value = null
            _messages.trySend(if (ok) BackupMessages.EXPORTED else BackupMessages.EXPORT_FAILED)
        }
    }

    /** Abre el documento, escribe y lo cierra (cerrar también cuenta: algunos proveedores terminan ahí). Si falla, lo borra. */
    private suspend fun writeDocument(uri: String, write: suspend (OutputStream) -> ExportResult): Boolean = withContext(io) {
        val result = try {
            documents.openOutput(uri).use { write(it) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir la copia", e)
            ExportResult.Error(BackupMessages.EXPORT_FAILED)
        }
        if (result != ExportResult.Ok) documents.delete(uri)
        result == ExportResult.Ok
    }

    /**
     * «Guardar también en Google Drive» o «Elegir otro archivo»: el selector creó [uri]. Se guarda su permiso para
     * siempre (si el proveedor no lo da, se borra el documento vacío y se dice), se suelta el del archivo anterior y
     * se hace ya una copia, así se ve que funciona.
     */
    fun onDriveDocumentCreated(uri: String) {
        if (work.value != null) return
        work.value = BackupWork.DRIVE
        viewModelScope.launch {
            val message = withContext(NonCancellable) {
                if (!withContext(io) { documents.takePersistableAccess(uri) }) {
                    withContext(io) { deleteDocumentIfEmpty(documents, uri) }
                    return@withContext BackupMessages.DRIVE_UNUSABLE
                }
                val previous = try {
                    val before = settings.backupStatus.first().driveUri
                    // El archivo nuevo empieza sin copias ni errores: la copia de ahora dirá cómo va.
                    settings.updateBackupStatus { it.copy(driveUri = uri, lastDrive = null, lastError = null) }
                    before
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo guardar el archivo de Drive", e)
                    withContext(io) { documents.releasePersistableAccess(uri) }
                    return@withContext BackupMessages.EXPORT_FAILED
                }
                if (previous != null && previous != uri) withContext(io) { documents.releasePersistableAccess(previous) }
                autoBackupMessage(manager.backupNow())
            }
            work.value = null
            refreshLocal()
            _messages.trySend(message)
        }
    }

    /** «Reintentar»: la copia automática ahora (la del teléfono y, si sale bien, la de Drive). */
    fun retryDrive() {
        if (work.value != null) return
        work.value = BackupWork.DRIVE
        viewModelScope.launch {
            val message = withContext(NonCancellable) { autoBackupMessage(manager.backupNow()) }
            work.value = null
            refreshLocal()
            _messages.trySend(message)
        }
    }

    private fun autoBackupMessage(result: AutoBackupResult): String = when (result) {
        AutoBackupResult.NothingToSave -> BackupMessages.NOTHING_TO_SAVE
        AutoBackupResult.LocalFailed -> BackupMessages.EXPORT_FAILED
        is AutoBackupResult.Saved -> when (result.drive) {
            true -> BackupMessages.DRIVE_SAVED
            false -> BackupMessages.DRIVE_FAILED
            null -> BackupMessages.EXPORTED
        }
    }

    /** El selector devolvió un archivo: se comprueba entero antes de ofrecer nada; si no vale, se dice ya. */
    fun onImportFilePicked(uri: String) = checkSource(ImportSource.Document(uri))

    /** «Restaurar» una copia del teléfono: se comprueba y se ofrece solo «Reemplazar todo». */
    fun restoreLocal(fileName: String) = checkSource(ImportSource.Local(fileName))

    private fun checkSource(source: ImportSource) {
        if (work.value != null) return
        work.value = BackupWork.CHECKING
        viewModelScope.launch {
            try {
                when (val result = preview(source)) {
                    is PreviewResult.Ok -> panel.value = ImportPanel.ChooseMode(source, result)
                    is PreviewResult.Error -> _messages.trySend(result.message)
                }
            } finally {
                work.value = null
            }
        }
    }

    private suspend fun preview(source: ImportSource): PreviewResult = when (source) {
        is ImportSource.Local -> manager.previewLocal(source.fileName)
        is ImportSource.Document -> withContext(io) {
            val input = try {
                documents.openInput(source.uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo abrir la copia", e)
                return@withContext PreviewResult.Error(BackupMessages.OPEN_FAILED)
            }
            try {
                manager.preview(input)
            } finally {
                closeQuietly(input)
            }
        }
    }

    fun chooseMerge() {
        val p = panel.value as? ImportPanel.ChooseMode ?: return
        if (p.source !is ImportSource.Document) return
        panel.value = null
        runImport(p.source, ImportMode.MERGE)
    }

    /** Primera confirmación de «Reemplazar todo»: aún no se toca nada, solo se pide la segunda. */
    fun chooseReplace() {
        val p = panel.value as? ImportPanel.ChooseMode ?: return
        panel.value = ImportPanel.ConfirmReplace(p.source, p.preview)
    }

    fun confirmReplace() {
        val p = panel.value as? ImportPanel.ConfirmReplace ?: return
        panel.value = null
        runImport(p.source, ImportMode.REPLACE_ALL)
    }

    fun dismissPanel() {
        panel.value = null
    }

    private fun runImport(source: ImportSource, mode: ImportMode) {
        if (work.value != null) return
        work.value = BackupWork.IMPORTING
        viewModelScope.launch {
            try {
                val result = when (source) {
                    is ImportSource.Local -> manager.restoreLocal(source.fileName)
                    is ImportSource.Document -> withContext(io) {
                        val input = try {
                            documents.openInput(source.uri)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo abrir la copia", e)
                            return@withContext ImportResult.Error(BackupMessages.OPEN_FAILED)
                        }
                        try {
                            manager.import(input, mode)
                        } finally {
                            closeQuietly(input)
                        }
                    }
                }
                _messages.trySend(
                    when (result) {
                        is ImportResult.Ok -> importSummary(result.recipes, result.images, result.failedImages)
                        is ImportResult.Error -> result.message
                    },
                )
            } finally {
                work.value = null
                // Puede haber una copia previa nueva.
                refreshLocal()
            }
        }
    }

    private fun closeQuietly(input: java.io.Closeable) {
        try {
            input.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val TAG = "BackupViewModel"
        private const val PENDING_SAVE_OUTSIDE = "pendingSaveOutside"

        fun factory(manager: BackupManager, settings: SettingsRepository, clock: Clock, documents: BackupDocuments): ViewModelProvider.Factory =
            viewModelFactory { initializer { BackupViewModel(manager, settings, clock, documents, createSavedStateHandle()) } }
    }
}
