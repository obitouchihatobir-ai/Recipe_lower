package app.recetario.backup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

enum class BackupWork { EXPORTING, CHECKING, IMPORTING }

data class BackupUiState(
    /** False hasta leer el estado de las copias (así no parpadea «Todavía no has guardado…»). */
    val statusLoaded: Boolean = false,
    val lastExternal: Instant? = null,
    val work: BackupWork? = null,
    val panel: ImportPanel? = null,
    /** Copias del teléfono; null hasta leerlas. */
    val localBackups: List<LocalBackup>? = null,
)

class BackupViewModel(
    private val manager: BackupManager,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val documents: BackupDocuments,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val work = MutableStateFlow<BackupWork?>(null)
    private val panel = MutableStateFlow<ImportPanel?>(null)
    private val local = MutableStateFlow<List<LocalBackup>?>(null)
    private val _messages = Channel<String>(Channel.BUFFERED)

    /** Copia del teléfono que espera a que el selector cree el documento de «Guardar fuera». */
    private var pendingSaveOutside: String? = null

    /** Avisos para el snackbar («Copia guardada.», el resumen de la importación o el error). */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<BackupUiState> = combine(settings.backupStatus, work, panel, local) { status, w, p, l ->
        BackupUiState(statusLoaded = true, lastExternal = status.lastExternal, work = w, panel = p, localBackups = l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    init {
        refreshLocal()
    }

    private fun refreshLocal() {
        viewModelScope.launch { local.value = manager.localBackups() }
    }

    fun suggestedFileName(): String = manager.suggestedExportName()

    /** Escribe la copia en el documento que creó el selector y anota la fecha. Si falla, lo borra. */
    fun export(uri: String) {
        if (work.value != null) return
        work.value = BackupWork.EXPORTING
        viewModelScope.launch {
            // Aunque se salga de la pantalla, la copia se termina (o se borra si falla).
            val message = withContext(NonCancellable) {
                val ok = writeDocument(uri) { manager.export(it) }
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
        val fileName = pendingSaveOutside ?: return
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

        fun factory(manager: BackupManager, settings: SettingsRepository, clock: Clock, documents: BackupDocuments): ViewModelProvider.Factory =
            viewModelFactory { initializer { BackupViewModel(manager, settings, clock, documents) } }
    }
}
