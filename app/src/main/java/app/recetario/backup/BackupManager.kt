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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipFile

enum class ImportMode { REPLACE_ALL, MERGE }

sealed interface ImportResult {
    /**
     * [recipes]: recetas añadidas o actualizadas; [images]: fotos copiadas al teléfono; [failedImages]: fotos de la
     * copia que alguna receta usa y no se pudieron copiar (la receta muestra un marcador).
     */
    data class Ok(val recipes: Int, val images: Int, val failedImages: Int = 0) : ImportResult
    data class Error(val message: String) : ImportResult
}

/** Lo que se sabe de una copia antes de importarla: cuántas recetas trae y cuántas hay ahora. */
sealed interface PreviewResult {
    data class Ok(val backupRecipes: Int, val localRecipes: Int) : PreviewResult
    data class Error(val message: String) : PreviewResult
}

/** Una copia guardada en el teléfono; [recipes] es null si está dañada (no se puede restaurar). */
data class LocalBackup(val fileName: String, val recipes: Int?)

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

    /** Comprueba la copia de [input] (sin cerrarlo) como al importar, sin cambiar nada. */
    suspend fun preview(input: InputStream): PreviewResult = mutex.withLock {
        withContext(io) { importer.preview(input) }
    }

    /** Copias del teléfono, las más recientes primero (según su nombre), con sus recetas o dañadas. */
    suspend fun localBackups(): List<LocalBackup> = withContext(io) {
        val names = localBackupsDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }.orEmpty().map { it.name }
        sortLocalBackupsNewestFirst(names).map { LocalBackup(it, inspect(File(localBackupsDir, it))) }
    }

    suspend fun previewLocal(fileName: String): PreviewResult {
        val file = localFile(fileName) ?: return PreviewResult.Error(BackupMessages.NOT_A_BACKUP)
        return mutex.withLock {
            withContext(io) {
                val input = try {
                    FileInputStream(file)
                } catch (e: IOException) {
                    return@withContext PreviewResult.Error(BackupMessages.NOT_A_BACKUP)
                }
                input.use { importer.preview(it) }
            }
        }
    }

    /** «Restaurar»: «Reemplazar todo» con una copia del teléfono (antes se guarda otra de lo actual). */
    suspend fun restoreLocal(fileName: String): ImportResult {
        val file = localFile(fileName) ?: return ImportResult.Error(BackupMessages.NOT_A_BACKUP)
        return mutex.withLock {
            withContext(io) {
                val input = try {
                    FileInputStream(file)
                } catch (e: IOException) {
                    return@withContext ImportResult.Error(BackupMessages.NOT_A_BACKUP)
                }
                input.use { importer.import(it, ImportMode.REPLACE_ALL) }
            }
        }
    }

    /** «Guardar fuera»: copia tal cual una copia del teléfono en [out] (sin cerrarlo). */
    suspend fun copyLocalTo(fileName: String, out: OutputStream): ExportResult {
        val file = localFile(fileName) ?: return ExportResult.Error(BackupMessages.EXPORT_FAILED)
        return mutex.withLock {
            withContext(io) {
                try {
                    FileInputStream(file).use { it.copyTo(out) }
                    out.flush()
                    ExportResult.Ok
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo copiar la copia del teléfono", e)
                    ExportResult.Error(BackupMessages.EXPORT_FAILED)
                }
            }
        }
    }

    /** Solo copias de la carpeta, por su nombre simple (nunca una ruta). */
    private fun localFile(fileName: String): File? =
        if (isSafeBackupFileName(fileName) && fileName.endsWith(".zip")) File(localBackupsDir, fileName).takeIf { it.isFile } else null

    /**
     * Copia de lo actual antes de sustituir nada al importar, en `filesDir/backups/antes-de-importar-<fecha>.zip`
     * (nunca pisa otra: añade `-2`, `-3`…). Se escribe en un temporal y solo toma su nombre si se puede restaurar
     * ([inspect]). La Tarea 16 la cambiará por `writeLocalBackup()`.
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
            inspect(partial) != null && partial.renameTo(target)
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

    /**
     * Número de recetas de la copia si se puede restaurar: el zip abre, `backup.json` se lee y pasa todas las
     * comprobaciones de la importación, y están todas las fotos de su manifiesto. Null si no.
     */
    private fun inspect(file: File): Int? = try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(BACKUP_JSON_ENTRY) ?: return null
            val bytes = zip.getInputStream(entry).use { it.readAtMost(BACKUP_MAX_JSON_BYTES) } ?: return null
            val backup = (decodeBackup(bytes.toString(Charsets.UTF_8)) as? BackupDecodeResult.Ok)?.file ?: return null
            val data = backup.toImportData()
            if (backup.images.any { zip.getEntry(BACKUP_IMAGES_PREFIX + it) == null }) return null
            data.recipes.size
        }
    } catch (e: Exception) {
        Log.w(TAG, "La copia ${file.name} no se puede restaurar", e)
        null
    }

    private companion object {
        const val TAG = "BackupManager"
        const val SAFETY_PREFIX = "antes-de-importar-"
    }
}
