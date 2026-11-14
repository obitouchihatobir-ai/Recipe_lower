package app.recetario.backup

import android.util.Log
import app.recetario.core.data.AppDatabase
import app.recetario.recipes.data.PhotoStore
import app.recetario.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipFile

enum class ImportMode { REPLACE_ALL, MERGE }

sealed interface ImportResult {
    /** [recipes]: recetas añadidas o actualizadas; [images]: fotos copiadas al teléfono. */
    data class Ok(val recipes: Int, val images: Int) : ImportResult
    data class Error(val message: String) : ImportResult
}

sealed interface ExportResult {
    data object Ok : ExportResult
    data class Error(val message: String) : ExportResult
}

/**
 * Copias de seguridad manuales (exportar e importar un zip). Todo el trabajo va en [io] y de una en una: una
 * exportación nunca lee a medias una importación. Nunca lanza excepciones salvo la cancelación.
 */
class BackupManager(
    db: AppDatabase,
    photoStore: PhotoStore,
    settings: SettingsRepository,
    private val clock: Clock,
    cacheDir: File,
    filesDir: File,
    appVersion: String = "",
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val exporter = BackupExporter(db, photoStore, settings, clock, appVersion)
    private val importer = BackupImporter(db, photoStore, settings, cacheDir, ::writeSafetyCopy)

    /** Copias guardadas en el teléfono (`filesDir/backups/`). */
    val localBackupsDir = File(filesDir, "backups")

    /** Nombre que se propone al exportar: `Recetario-AAAA-MM-DD.zip`. */
    fun suggestedExportName(): String = exportFileName(LocalDate.now(clock))

    /** Escribe la copia en [out] (sin cerrarlo). */
    suspend fun export(out: OutputStream): ExportResult = mutex.withLock {
        withContext(io) {
            try {
                exporter.write(out)
                ExportResult.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo exportar", e)
                ExportResult.Error(BackupMessages.EXPORT_FAILED)
            }
        }
    }

    /** Importa la copia de [input] (sin cerrarlo). Ver [BackupImporter] para las garantías. */
    suspend fun import(input: InputStream, mode: ImportMode): ImportResult = mutex.withLock {
        withContext(io) { importer.import(input, mode) }
    }

    /**
     * Copia de lo actual antes de «Reemplazar todo», en `filesDir/backups/antes-de-importar-<fecha>.zip` (nunca pisa
     * otra: añade `-2`, `-3`…). Se escribe en un temporal, se comprueba que el zip abre y que `backup.json` se lee,
     * y solo entonces toma su nombre. La Tarea 16 la cambiará por `writeLocalBackup()`, que entra en la rotación.
     */
    private suspend fun writeSafetyCopy(): Boolean {
        var tmp: File? = null
        return try {
            val dir = localBackupsDir
            if (!dir.isDirectory && !dir.mkdirs()) return false
            // Temporales de una copia previa que Android cortó a medias (nunca son copias terminadas).
            dir.listFiles { f -> f.name.startsWith(SAFETY_PREFIX) && f.name.endsWith(".zip.tmp") }?.forEach { it.delete() }
            val target = freeFile(dir, safetyCopyFileName(LocalDateTime.now(clock)))
            val partial = File(dir, "${target.name}.tmp").also { tmp = it }
            FileOutputStream(partial).use { out ->
                exporter.write(out)
                out.fd.sync()
            }
            isReadableBackup(partial) && partial.renameTo(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo hacer la copia previa", e)
            false
        } finally {
            tmp?.takeIf { it.exists() }?.delete()
        }
    }

    private fun freeFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val stem = name.removeSuffix(".zip")
        return generateSequence(2) { it + 1 }.map { File(dir, "$stem-$it.zip") }.first { !it.exists() }
    }

    private fun isReadableBackup(file: File): Boolean = try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(BACKUP_JSON_ENTRY) ?: return false
            val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            decodeBackup(text) is BackupDecodeResult.Ok
        }
    } catch (e: Exception) {
        Log.w(TAG, "La copia previa no se puede leer", e)
        false
    }

    private companion object {
        const val TAG = "BackupManager"
        const val SAFETY_PREFIX = "antes-de-importar-"
    }
}
