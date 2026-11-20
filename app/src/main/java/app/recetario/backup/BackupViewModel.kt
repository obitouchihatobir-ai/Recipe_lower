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

/**
 * El archivo elegido para Drive ya tiene algo: antes de sobrescribirlo se pregunta. [ExistingCopy] es una copia que
 * se puede importar (con sus recetas y las de ahora); [NotACopy], un archivo que no es una copia de Sazón (se puede
 * reemplazar). [Unusable] es una copia que no se puede comprobar ([message]: de una versión más nueva, incompleta) o
 * un archivo que ahora no se puede leer (Drive sin conexión): puede ser la única copia fuera del teléfono, así que
 * no se ofrece reemplazarlo, solo elegir otro archivo o cancelar.
 */
sealed interface DrivePanel {
    val uri: String

    data class ExistingCopy(override val uri: String, val backupRecipes: Int, val localRecipes: Int) : DrivePanel
    data class NotACopy(override val uri: String) : DrivePanel
    data class Unusable(override val uri: String, val message: String) : DrivePanel
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
    /** El archivo elegido para Drive ya tiene algo: se pregunta antes de sobrescribirlo. */
    val drivePanel: DrivePanel? = null,
    /** Por qué falló la última copia semanal en el teléfono, o null. */
    val localError: String? = null,
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
    private val drivePanel = MutableStateFlow<DrivePanel?>(null)
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

    val state: StateFlow<BackupUiState> = combine(checkedStatus, work, panel, local, drivePanel) { check, w, p, l, dp ->
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
            drivePanel = dp,
            localError = status.localError,
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

    /**
     * Abre el documento, escribe y lo cierra (cerrar también cuenta: algunos proveedores terminan ahí). Como
     * `exportToDocument`: si ni siquiera se pudo abrir, no se ha tocado y solo se borra si está vacío (el usuario
     * pudo elegir un archivo que ya existía); si falla después de abrirlo, se borra (quedaría a medias).
     */
    private suspend fun writeDocument(uri: String, write: suspend (OutputStream) -> ExportResult): Boolean = withContext(io) {
        val out = try {
            documents.openOutput(uri)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo abrir el documento", e)
            deleteDocumentIfEmpty(documents, uri)
            return@withContext false
        }
        val result = try {
            out.use { write(it) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir la copia", e)
            ExportResult.Error(BackupMessages.EXPORT_FAILED)
        }
        if (result != ExportResult.Ok) documents.delete(uri)
        result == ExportResult.Ok
    }

    /**
     * «Guardar también en Google Drive» o «Elegir otro archivo»: el selector devolvió [uri]. Si está vacío (lo acaba de
     * crear), se usa ya ([setUpDrive]). Si tiene algo (el usuario eligió un archivo que ya existía), antes de
     * sobrescribirlo se pregunta: si es una copia, con sus recetas y la opción de importarla primero. Si hay otra
     * operación en curso, no se usa y, si está vacío, se borra.
     */
    fun onDriveDocumentCreated(uri: String) {
        if (work.value != null) {
            viewModelScope.launch { withContext(NonCancellable + io) { deleteDocumentIfEmpty(documents, uri) } }
            return
        }
        work.value = BackupWork.CHECKING
        viewModelScope.launch {
            val existing = withContext(NonCancellable + io) { inspectDriveDocument(uri) }
            work.value = null
            if (existing == null) setUpDrive(uri) else drivePanel.value = existing
        }
    }

    /** Null si el documento está vacío; si no, qué tiene (una copia que se puede importar u otra cosa). */
    private suspend fun inspectDriveDocument(uri: String): DrivePanel? {
        val empty = try {
            documents.openInput(uri).use { it.read() == -1 }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer el archivo de Drive", e)
            false
        }
        if (empty) return null
        val preview = try {
            documents.openInput(uri).use { manager.preview(it) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo comprobar el archivo de Drive", e)
            PreviewResult.Error(BackupMessages.OPEN_FAILED)
        }
        return when (preview) {
            is PreviewResult.Ok -> DrivePanel.ExistingCopy(uri, preview.backupRecipes, preview.localRecipes)
            // Solo lo que seguro no es una copia de Sazón se ofrece reemplazar; cualquier otro error (versión más
            // nueva, copia incompleta, archivo que ahora no se puede leer) se muestra tal cual y el archivo no se toca.
            is PreviewResult.Error ->
                if (preview.message == BackupMessages.NOT_A_BACKUP) DrivePanel.NotACopy(uri) else DrivePanel.Unusable(uri, preview.message)
        }
    }

    /** «Reemplazar»: el archivo que ya tenía algo se usa para la copia automática (y se sobrescribe ya). */
    fun confirmDriveReplace() {
        val p = drivePanel.value ?: return
        if (p is DrivePanel.Unusable) return // no se ofrece: podría ser la única copia fuera del teléfono
        drivePanel.value = null
        setUpDrive(p.uri)
    }

    /** «Importar primero»: la importación de siempre con ese archivo; el archivo de Drive no se guarda todavía. */
    fun importDriveFileFirst() {
        val p = drivePanel.value as? DrivePanel.ExistingCopy ?: return
        drivePanel.value = null
        onImportFilePicked(p.uri)
    }

    /** «Cancelar»: el archivo se queda como estaba y no se usa. */
    fun dismissDrivePanel() {
        drivePanel.value = null
    }

    /**
     * Usa [uri] para la copia automática: guarda su permiso para siempre (si el proveedor no lo da, se borra el
     * documento si está vacío y se dice), suelta el del archivo anterior y hace ya una copia, así se ve que funciona.
     */
    private fun setUpDrive(uri: String) {
        if (work.value != null) {
            viewModelScope.launch { withContext(NonCancellable + io) { deleteDocumentIfEmpty(documents, uri) } }
            return
        }
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
                    withContext(io) {
                        documents.releasePersistableAccess(uri)
                        deleteDocumentIfEmpty(documents, uri)
                    }
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
                if (source is ImportSource.Local && isDatabaseCopy(source.fileName)) {
                    // Copia de la base de datos de antes de actualizar: si sale bien, la app se reinicia.
                    _messages.trySend(
                        when (val restored = manager.restoreBeforeUpdate(source.fileName)) {
                            BackupResult.Ok -> BackupMessages.DATABASE_RESTORED
                            is BackupResult.Error -> restored.message
                        },
                    )
                    return@launch
                }
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
                        is ImportResult.Ok -> importSummary(result.recipes, result.images, result.failedImages, result.diaryEntries)
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
