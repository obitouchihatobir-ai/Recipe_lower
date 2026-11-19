package app.recetario.backup

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.KEEP_ON_CORRUPTION
import app.recetario.recipes.data.PhotoStore
import app.recetario.settings.BackupStatus
import app.recetario.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
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

/** Resultado de restaurar una copia de la base de datos de antes de actualizar ([BackupManager.restoreBeforeUpdate]). */
sealed interface BackupResult {
    data object Ok : BackupResult
    data class Error(val message: String) : BackupResult
}

/** Cómo terminó una copia automática ([BackupManager.backupNow]). */
sealed interface AutoBackupResult {
    /** No hay recetas: no se escribe nada (las copias anteriores se conservan tal cual). */
    data object NothingToSave : AutoBackupResult

    /** No se pudo escribir o comprobar la copia del teléfono: no se ha copiado nada a Drive. */
    data object LocalFailed : AutoBackupResult

    /** [file]: la copia semanal, ya comprobada. [drive]: null si no hay archivo de Drive; si no, si se copió. */
    data class Saved(val file: File, val drive: Boolean?) : AutoBackupResult
}

/**
 * Copias de seguridad: exportar e importar un zip, las copias del teléfono (`filesDir/backups/`: las semanales
 * `Sazon-…zip` y las previas a importar `antes-de-importar-…zip`) y la copia en Drive. Todo el trabajo va en
 * [io] y de una en una: una exportación nunca lee a medias una importación y una rotación nunca borra la copia que
 * se está restaurando. Una copia solo se da por buena (y solo sale del teléfono) si se puede restaurar ([inspect]).
 * Nunca lanza excepciones salvo la cancelación.
 */
class BackupManager(
    private val db: AppDatabase,
    photoStore: PhotoStore,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val cacheDir: File,
    filesDir: File,
    /** Los documentos de fuera: el de Drive de la copia automática y los que se eligen al exportar. */
    private val documents: BackupDocuments,
    appVersion: String = "",
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Archivo de [db] (`recetario.db`): el que sustituye [restoreBeforeUpdate]. Null si es de memoria (no se puede restaurar). */
    private val databaseFile: File? = null,
    /** Reinicia el proceso después de sustituir la base de datos. Las pruebas pasan uno falso. */
    private val restartApp: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val exporter = BackupExporter(db, photoStore, settings, clock, appVersion)
    private val importer = BackupImporter(db, photoStore, settings, cacheDir, ::writeSafetyCopy)

    /** Copias guardadas en el teléfono (`filesDir/backups/`). */
    val localBackupsDir = File(filesDir, BACKUPS_DIR)

    /** Copia del teléfono que se está restaurando (la rotación no la borra). Solo se usa con [mutex]. */
    private var restoring: String? = null

    /** Nombre que se propone al exportar: `Sazon-AAAA-MM-DD.zip`. */
    fun suggestedExportName(): String = exportFileName(LocalDate.now(clock))

    /**
     * Escribe en [out] (sin cerrarlo) una copia de lo actual ya comprobada: primero se escribe en un temporal y se
     * comprueba como al importar; si no vale, no llega nada a [out].
     */
    suspend fun export(out: OutputStream): ExportResult = mutex.withLock {
        withContext(io) {
            val temp = writeExportTemp() ?: return@withContext ExportResult.Error(BackupMessages.EXPORT_FAILED)
            try {
                FileInputStream(temp).use { it.copyTo(out, BUFFER) }
                out.flush()
                ExportResult.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo exportar", e)
                ExportResult.Error(BackupMessages.EXPORT_FAILED)
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * «Exportar copia» al documento [uri] que creó el selector. El documento solo se abre (con «wt») cuando la
     * copia ya está escrita y comprobada en un temporal: si no vale o no se puede abrir, el documento no se toca
     * (solo se borra si está vacío, es decir, si lo acaba de crear el selector). Si falla después de abrirlo, se
     * borra el documento a medias (si el usuario eligió sobrescribir una copia anterior, esa se pierde: el
     * proveedor ya la vació al abrirlo).
     */
    suspend fun exportToDocument(uri: String): ExportResult = mutex.withLock {
        withContext(io) {
            val temp = writeExportTemp()
            if (temp == null) {
                deleteDocumentIfEmpty(documents, uri)
                return@withContext ExportResult.Error(BackupMessages.EXPORT_FAILED)
            }
            try {
                when (copyToDocument(temp, uri, "wt")) {
                    CopyResult.OK -> ExportResult.Ok
                    // No se llegó a abrir: no se ha tocado, así que solo se borra si está vacío (lo creó el selector).
                    CopyResult.OPEN_FAILED -> {
                        deleteDocumentIfEmpty(documents, uri)
                        ExportResult.Error(BackupMessages.EXPORT_FAILED)
                    }
                    CopyResult.WRITE_FAILED -> {
                        documents.delete(uri)
                        ExportResult.Error(BackupMessages.EXPORT_FAILED)
                    }
                }
            } finally {
                temp.delete()
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

    /**
     * Copias del teléfono, las más recientes primero (según su nombre), con sus recetas o dañadas: los zip y las
     * copias de la base de datos de antes de actualizar (`antes-de-actualizar-…db`; otros `.db` no se listan).
     */
    suspend fun localBackups(): List<LocalBackup> = withContext(io) {
        val names = localBackupsDir.listFiles { f -> f.isFile && (f.name.endsWith(".zip") || isDatabaseCopy(f.name)) }
            .orEmpty().map { it.name }
        sortLocalBackupsNewestFirst(names)
            .map { LocalBackup(it, inspectAny(File(localBackupsDir, it))) }
            // Una que la rotación borró mientras se leía no es una copia dañada: ya no está.
            .filter { File(localBackupsDir, it.fileName).isFile }
    }

    suspend fun previewLocal(fileName: String): PreviewResult {
        val file = localFile(fileName) ?: return PreviewResult.Error(BackupMessages.NOT_A_BACKUP)
        return mutex.withLock {
            withContext(io) {
                if (isDatabaseCopy(fileName)) {
                    val recipes = inspectDatabaseCopy(file) ?: return@withContext PreviewResult.Error(BackupMessages.NOT_A_BACKUP)
                    val local = try {
                        db.backupDao().recipeCount()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudieron contar las recetas", e)
                        return@withContext PreviewResult.Error(BackupMessages.IMPORT_FAILED)
                    }
                    return@withContext PreviewResult.Ok(backupRecipes = recipes, localRecipes = local)
                }
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
        if (isDatabaseCopy(fileName)) return ImportResult.Error(BackupMessages.NOT_A_BACKUP)
        val file = localFile(fileName) ?: return ImportResult.Error(BackupMessages.NOT_A_BACKUP)
        return mutex.withLock {
            withContext(io) {
                val input = try {
                    FileInputStream(file)
                } catch (e: IOException) {
                    return@withContext ImportResult.Error(BackupMessages.NOT_A_BACKUP)
                }
                restoring = fileName
                try {
                    input.use { importer.import(it, ImportMode.REPLACE_ALL) }
                } finally {
                    restoring = null
                }
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

    /**
     * «Guardar fuera» de una copia del teléfono directamente en el documento [uri] (la pantalla de error de la
     * actualización, que no tiene ViewModel). Como [exportToDocument]: si no se pudo abrir, solo se borra si está
     * vacío; si falló después de abrirlo, se borra.
     */
    suspend fun copyLocalToDocument(fileName: String, uri: String): ExportResult {
        val file = localFile(fileName)
        return mutex.withLock {
            withContext(io + NonCancellable) {
                if (file == null) {
                    deleteDocumentIfEmpty(documents, uri)
                    return@withContext ExportResult.Error(BackupMessages.EXPORT_FAILED)
                }
                when (copyToDocument(file, uri, "wt")) {
                    CopyResult.OK -> ExportResult.Ok
                    CopyResult.OPEN_FAILED -> {
                        deleteDocumentIfEmpty(documents, uri)
                        ExportResult.Error(BackupMessages.EXPORT_FAILED)
                    }
                    CopyResult.WRITE_FAILED -> {
                        documents.delete(uri)
                        ExportResult.Error(BackupMessages.EXPORT_FAILED)
                    }
                }
            }
        }
    }

    /**
     * «Restaurar» una copia de la base de datos de antes de actualizar (§6.2): primero la copia previa a importar
     * habitual (si no se puede, no se toca nada); después cierra la base de datos, la sustituye por la copia
     * (borrando `-wal` y `-shm`, que son de la anterior) y reinicia la app, que vuelve a migrar. Las fotos no se
     * tocan: la base de datos vieja apunta a fotos que siguen ahí. No se interrumpe a medias.
     */
    suspend fun restoreBeforeUpdate(fileName: String): BackupResult {
        val file = localFile(fileName)?.takeIf { isDatabaseCopy(fileName) } ?: return BackupResult.Error(BackupMessages.NOT_A_BACKUP)
        val target = databaseFile ?: return BackupResult.Error(BackupMessages.IMPORT_FAILED)
        return mutex.withLock {
            withContext(io + NonCancellable) {
                if (inspectDatabaseCopy(file) == null) return@withContext BackupResult.Error(BackupMessages.NOT_A_BACKUP)
                if (!writeSafetyCopy()) return@withContext BackupResult.Error(BackupMessages.SAFETY_COPY_FAILED)
                // Primero junto a la base de datos, con fsync: si no cabe, la actual sigue abierta y sin tocar.
                val staged = File(target.path + RESTORE_SUFFIX)
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(staged).use { out ->
                            input.copyTo(out, BUFFER)
                            out.flush()
                            out.fd.sync()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo preparar la copia para restaurarla", e)
                    staged.delete()
                    return@withContext BackupResult.Error(BackupMessages.IMPORT_FAILED)
                }
                db.close()
                val result = try {
                    // El WAL y la memoria compartida son de la base de datos anterior: con la nueva la estropearían.
                    for (suffix in listOf("-wal", "-shm", "-journal")) File(target.path + suffix).delete()
                    Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    BackupResult.Ok
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo sustituir la base de datos", e)
                    staged.delete()
                    BackupResult.Error(BackupMessages.IMPORT_FAILED)
                }
                // La base de datos ya está cerrada: en los dos casos la app tiene que volver a abrirla.
                restartApp()
                result
            }
        }
    }

    /**
     * Copia semanal en `filesDir/backups/Sazon-AAAA-MM-DD-HHmm.zip`: se escribe en un temporal (con fsync), se
     * comprueba que se puede restaurar y solo entonces toma su nombre (nunca pisa otra: añade `-2`, `-3`…). Después
     * se borran las semanales que sobran (la más reciente de cada una de las 4 últimas semanas; nunca la recién
     * escrita, una «del futuro» ni otro tipo de copia) y se anota `lastLocal`. Null si no se pudo escribir o
     * comprobar (y queda anotado `localError`).
     */
    suspend fun writeLocalBackup(): File? = mutex.withLock { withContext(io) { writeWeeklyLocked() } }

    /**
     * Copia [local] (una copia del teléfono) en el documento de Drive [uri] con «wt» (o «w» si el proveedor no
     * admite «wt»), solo si se puede restaurar, y comprueba el tamaño de lo escrito. Si sale bien anota
     * `lastExternal` y `lastDrive` y borra `lastError`; si no, anota `lastError` y la copia del teléfono se queda
     * como está. No se interrumpe a medias.
     */
    suspend fun copyToExternal(local: File, uri: String): Boolean = mutex.withLock { withContext(io) { copyToExternalLocked(local, uri) } }

    /**
     * La copia automática (la semanal y «Reintentar»): sin recetas no escribe nada; si no, primero la copia del
     * teléfono, comprobada, y solo entonces, si hay archivo de Drive, la copia allí. Todo de una vez, sin que otra
     * operación de copias se cuele en medio.
     */
    suspend fun backupNow(): AutoBackupResult = mutex.withLock {
        withContext(io) {
            val recipes = try {
                db.backupDao().recipeCount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron contar las recetas", e)
                withContext(NonCancellable) { record { it.copy(localError = BackupMessages.LOCAL_FAILED) } }
                return@withContext AutoBackupResult.LocalFailed
            }
            if (recipes == 0) return@withContext AutoBackupResult.NothingToSave
            val file = writeWeeklyLocked() ?: return@withContext AutoBackupResult.LocalFailed
            val uri = try {
                settings.backupStatus.first().driveUri
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo leer el archivo de Drive", e)
                null
            }
            AutoBackupResult.Saved(file, drive = uri?.let { copyToExternalLocked(file, it) })
        }
    }

    /** La copia semanal: si falla queda anotado `localError` (Inicio y Copias lo muestran); si sale bien, se borra. */
    private suspend fun writeWeeklyLocked(): File? {
        val file = writeLocalCopy(weeklyBackupFileName(LocalDateTime.now(clock)))
        withContext(NonCancellable) {
            if (file == null) {
                record { it.copy(localError = BackupMessages.LOCAL_FAILED) }
            } else {
                rotate(LocalBackupKind.AUTOMATIC, WEEKLY_BACKUPS_KEPT, file)
                record { it.copy(lastLocal = clock.instant(), localError = null) }
            }
        }
        return file
    }

    private suspend fun copyToExternalLocked(local: File, uri: String): Boolean = withContext(NonCancellable) {
        // Nunca se sube una copia que no se pueda restaurar.
        val ok = inspect(local) != null && copyToDrive(local, uri)
        val now = clock.instant()
        record { status ->
            when {
                // Mientras tanto se eligió otro archivo: esta copia no dice nada del nuevo.
                status.driveUri != uri -> status
                ok -> status.copy(lastExternal = now, lastDrive = now, lastError = null)
                else -> status.copy(lastError = BackupMessages.DRIVE_FAILED)
            }
        }
        ok
    }

    /** Solo copias de la carpeta, por su nombre simple (nunca una ruta). */
    private fun localFile(fileName: String): File? =
        if (isSafeBackupFileName(fileName) && (fileName.endsWith(".zip") || isDatabaseCopy(fileName))) {
            File(localBackupsDir, fileName).takeIf { it.isFile }
        } else {
            null
        }

    private fun inspectAny(file: File): Int? = if (isDatabaseCopy(file.name)) inspectDatabaseCopy(file) else inspect(file)

    /**
     * Número de recetas de una copia de la base de datos de antes de actualizar si se puede restaurar: se abre en
     * solo lectura (está en diario clásico: no deja `-wal` ni `-shm`), pasa `PRAGMA quick_check` y tiene la tabla de
     * recetas. Null si no.
     */
    private fun inspectDatabaseCopy(file: File): Int? = try {
        val copy = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY, KEEP_ON_CORRUPTION)
        try {
            val ok = copy.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            if (!ok) null else copy.rawQuery("SELECT COUNT(*) FROM recipes", null).use { if (it.moveToFirst()) it.getInt(0) else null }
        } finally {
            copy.close()
        }
    } catch (e: Exception) {
        Log.w(TAG, "La copia ${file.name} no se puede restaurar", e)
        null
    }

    /**
     * Copia de lo actual antes de sustituir nada al importar, en `filesDir/backups/antes-de-importar-<fecha>.zip`,
     * comprobada como la semanal. Después se borran las previas que sobran (se conservan 3; nunca la recién escrita,
     * la que se está restaurando ni las semanales).
     */
    private suspend fun writeSafetyCopy(): Boolean {
        val file = writeLocalCopy(safetyCopyFileName(LocalDateTime.now(clock))) ?: return false
        withContext(NonCancellable) { rotate(LocalBackupKind.BEFORE_IMPORT, SAFETY_COPIES_KEPT, file) }
        return true
    }

    /**
     * Escribe una copia comprobada en la carpeta de copias con el nombre [name] (o `-2`, `-3`… si ya existe): en un
     * temporal `.tmp` que solo toma su nombre si se puede restaurar. Null si no se pudo.
     */
    private suspend fun writeLocalCopy(name: String): File? {
        var partial: File? = null
        return try {
            val dir = localBackupsDir
            if (!dir.isDirectory && !dir.mkdirs()) {
                Log.w(TAG, "No se pudo crear la carpeta de copias")
                return null
            }
            // Restos de una copia que Android cortó a medias (las copias van de una en una: nunca son de otra en curso).
            dir.listFiles { f -> f.isFile && f.name.endsWith(".zip.tmp") }?.forEach { it.delete() }
            val target = freeFile(dir, name)
            val tmp = File(dir, "${target.name}.tmp").also { partial = it }
            if (!writeChecked(tmp)) return null
            // Sin REPLACE_EXISTING: si (por lo que sea) ya existe, falla en vez de pisarla.
            Files.move(tmp.toPath(), target.toPath())
            target
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar la copia en el teléfono", e)
            null
        } finally {
            partial?.takeIf { it.exists() }?.delete()
        }
    }

    /** Copia comprobada en un temporal de la caché (para exportar); null si no se pudo. Quien la recibe la borra. */
    private suspend fun writeExportTemp(): File? {
        return try {
            // Restos de una exportación que Android cortó a medias (van de una en una).
            cacheDir.listFiles { f -> f.name.startsWith(EXPORT_PREFIX) }?.forEach { it.delete() }
            if (!cacheDir.isDirectory && !cacheDir.mkdirs()) return null
            val temp = File(cacheDir, "$EXPORT_PREFIX${UUID.randomUUID()}.zip")
            if (writeChecked(temp)) temp else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo preparar la copia", e)
            null
        }
    }

    /** Escribe la copia de lo actual en [file] (con fsync) y comprueba que se puede restaurar. Si no, la borra. */
    private suspend fun writeChecked(file: File): Boolean {
        try {
            FileOutputStream(file).use { out ->
                exporter.write(out)
                out.fd.sync()
            }
            if (inspect(file) != null) return true
            Log.w(TAG, "La copia recién escrita no se puede restaurar")
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir la copia", e)
        }
        file.delete()
        return false
    }

    private enum class CopyResult { OK, OPEN_FAILED, WRITE_FAILED }

    /**
     * Copia [file] en el documento [uri] abierto con [mode] y lo cierra (cerrar también cuenta: algunos proveedores
     * terminan ahí). Distingue si ni siquiera se pudo abrir (el documento no se ha tocado) de si falló después.
     */
    private fun copyToDocument(file: File, uri: String, mode: String): CopyResult {
        val out = try {
            documents.openOutput(uri, mode)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo abrir el documento con «$mode»", e)
            return CopyResult.OPEN_FAILED
        }
        return try {
            out.use {
                FileInputStream(file).use { input -> input.copyTo(out, BUFFER) }
                out.flush()
            }
            CopyResult.OK
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir en el documento", e)
            CopyResult.WRITE_FAILED
        }
    }

    /**
     * Copia [local] en el documento de Drive: con «wt» y, si el proveedor no lo admite, una vez más con «w». Con «w»
     * algunos proveedores no vacían el archivo y dejan al final bytes de la copia anterior: si lo que ya hay es más
     * largo que la copia nueva, no se usa «w» (la copia anterior se queda entera y se anota el fallo). Después lee el
     * documento y comprueba que mide lo mismo que la copia.
     */
    private fun copyToDrive(local: File, uri: String): Boolean {
        var result = copyToDocument(local, uri, "wt")
        if (result == CopyResult.OPEN_FAILED) {
            val old = documentLength(uri)
            if (old != null && old > local.length()) {
                Log.w(TAG, "Sin «wt» y con una copia anterior más larga ($old bytes): no se usa «w»")
                return false
            }
            result = copyToDocument(local, uri, "w")
        }
        if (result != CopyResult.OK) return false
        val length = documentLength(uri)
        if (length != local.length()) {
            Log.w(TAG, "La copia en Drive mide $length bytes y debería medir ${local.length()}")
            return false
        }
        return true
    }

    /** Bytes del documento leyéndolo entero, o null si no se puede leer. */
    private fun documentLength(uri: String): Long? = try {
        documents.openInput(uri).use { input ->
            val buffer = ByteArray(BUFFER)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
            }
            total
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la copia en Drive", e)
        null
    }

    /** Borra las copias de [kind] que sobran. Nunca [justWritten] ni la que se está restaurando. */
    private fun rotate(kind: LocalBackupKind, keep: Int, justWritten: File) {
        try {
            val names = localBackupsDir.list()?.toList().orEmpty()
            val now = LocalDateTime.now(clock)
            for (name in filesToDelete(names, now, keep, kind, protect = setOfNotNull(justWritten.name, restoring))) {
                if (!File(localBackupsDir, name).delete()) Log.w(TAG, "No se pudo borrar la copia antigua $name")
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron borrar las copias antiguas", e)
        }
    }

    /** Anota el estado de las copias; si no se puede, solo se registra (la copia ya está hecha o ya falló). */
    private suspend fun record(transform: (BackupStatus) -> BackupStatus) {
        try {
            settings.updateBackupStatus(transform)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo anotar el estado de las copias", e)
        }
    }

    /**
     * Nombre libre para [name] en [dir]: el mismo si no hay ninguna de ese minuto; si las hay, el número siguiente
     * al mayor (`-2`, `-3`…), nunca uno que la rotación dejó libre. Así la más nueva siempre ordena como la más
     * reciente (si no, la rotación podría quedarse con una más antigua del mismo minuto).
     */
    private fun freeFile(dir: File, name: String): File {
        val stem = name.removeSuffix(".zip")
        val sameMinute = Regex("^${Regex.escape(stem)}(?:-(\\d{1,4}))?\\.zip$")
        val highest = dir.list().orEmpty().maxOfOrNull { other ->
            sameMinute.matchEntire(other)?.let { it.groupValues[1].toIntOrNull() ?: 1 } ?: 0
        } ?: 0
        return if (highest == 0) File(dir, name) else File(dir, "$stem-${highest + 1}.zip")
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

    companion object {
        /** Carpeta de las copias del teléfono, dentro de `filesDir`. */
        const val BACKUPS_DIR = "backups"

        private const val RESTORE_SUFFIX = ".restaurar"
        private const val TAG = "BackupManager"
        private const val EXPORT_PREFIX = "exportar-"
        private const val BUFFER = 64 * 1024
    }
}
