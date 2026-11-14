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
import java.time.Clock
import java.time.Instant

/** Paneles de «Importar copia»: primero se elige el modo; «Reemplazar todo» pide una segunda confirmación. */
sealed interface ImportPanel {
    val uri: String

    data class ChooseMode(override val uri: String) : ImportPanel
    data class ConfirmReplace(override val uri: String) : ImportPanel
}

enum class BackupWork { EXPORTING, IMPORTING }

data class BackupUiState(
    /** False hasta leer el estado de las copias (así no parpadea «Todavía no has guardado…»). */
    val statusLoaded: Boolean = false,
    val lastExternal: Instant? = null,
    val work: BackupWork? = null,
    val panel: ImportPanel? = null,
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
    private val _messages = Channel<String>(Channel.BUFFERED)

    /** Avisos para el snackbar («Copia guardada.», el resumen de la importación o el error). */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<BackupUiState> = combine(settings.backupStatus, work, panel) { status, w, p ->
        BackupUiState(statusLoaded = true, lastExternal = status.lastExternal, work = w, panel = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun suggestedFileName(): String = manager.suggestedExportName()

    /** Escribe la copia en el documento que creó el selector. Si falla, lo borra: nunca queda una copia rota. */
    fun export(uri: String) {
        if (work.value != null) return
        work.value = BackupWork.EXPORTING
        viewModelScope.launch {
            // Aunque se salga de la pantalla, la copia se termina (o se borra si falla).
            val message = withContext(NonCancellable) { exportTo(uri) }
            work.value = null
            _messages.trySend(message)
        }
    }

    private suspend fun exportTo(uri: String): String {
        val result = withContext(io) {
            try {
                // Cerrar también cuenta: es cuando algunos proveedores terminan de escribir.
                documents.openOutput(uri).use { manager.export(it) }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo escribir la copia", e)
                ExportResult.Error(BackupMessages.EXPORT_FAILED)
            }
        }
        return when (result) {
            ExportResult.Ok -> {
                try {
                    settings.updateBackupStatus { it.copy(lastExternal = clock.instant()) }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo anotar la fecha de la copia", e)
                }
                BackupMessages.EXPORTED
            }
            is ExportResult.Error -> {
                withContext(io) { documents.delete(uri) }
                result.message
            }
        }
    }

    fun onImportFilePicked(uri: String) {
        if (work.value == null) panel.value = ImportPanel.ChooseMode(uri)
    }

    fun chooseMerge() {
        val p = panel.value as? ImportPanel.ChooseMode ?: return
        panel.value = null
        runImport(p.uri, ImportMode.MERGE)
    }

    /** Primera confirmación de «Reemplazar todo»: aún no se toca nada, solo se pide la segunda. */
    fun chooseReplace() {
        val p = panel.value as? ImportPanel.ChooseMode ?: return
        panel.value = ImportPanel.ConfirmReplace(p.uri)
    }

    fun confirmReplace() {
        val p = panel.value as? ImportPanel.ConfirmReplace ?: return
        panel.value = null
        runImport(p.uri, ImportMode.REPLACE_ALL)
    }

    fun dismissPanel() {
        panel.value = null
    }

    private fun runImport(uri: String, mode: ImportMode) {
        if (work.value != null) return
        work.value = BackupWork.IMPORTING
        viewModelScope.launch {
            try {
                val result = withContext(io) {
                    val input = try {
                        documents.openInput(uri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo abrir la copia", e)
                        return@withContext ImportResult.Error(BackupMessages.OPEN_FAILED)
                    }
                    try {
                        manager.import(input, mode)
                    } finally {
                        try {
                            input.close()
                        } catch (_: IOException) {
                        }
                    }
                }
                _messages.trySend(
                    when (result) {
                        is ImportResult.Ok -> importSummary(result.recipes, result.images)
                        is ImportResult.Error -> result.message
                    },
                )
            } finally {
                work.value = null
            }
        }
    }

    companion object {
        private const val TAG = "BackupViewModel"

        fun factory(manager: BackupManager, settings: SettingsRepository, clock: Clock, documents: BackupDocuments): ViewModelProvider.Factory =
            viewModelFactory { initializer { BackupViewModel(manager, settings, clock, documents) } }
    }
}
