package app.recetario.core.data

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.recetario.backup.BackupMessages
import app.recetario.backup.LocalBackupKind
import app.recetario.backup.UPDATE_COPIES_KEPT
import app.recetario.backup.beforeUpdateFileName
import app.recetario.backup.filesToDelete
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDateTime

/**
 * Al abrir un archivo a mano, si SQLite lo ve dañado no se borra (el de serie de Android lo borraría): aquí se
 * abren la base de datos del usuario y sus copias, y en ese caso solo se da por fallida la operación.
 */
val KEEP_ON_CORRUPTION = DatabaseErrorHandler { }

/** Cómo quedó la base de datos antes de que Room la abra. */
sealed interface GuardResult {
    /** No hay nada que migrar (no existe o ya está en la versión actual): Room puede abrirla. */
    data object Ready : GuardResult

    /** Había que migrar y ya hay copia ([fileName], en la carpeta de copias): Room puede abrirla y migrarla. */
    data class Copied(val fileName: String) : GuardResult

    /** No se pudo hacer la copia: **no se migra** ni se abre la base de datos. */
    data class Failed(val message: String) : GuardResult
}

/**
 * Copia previa a la migración (diseño de la etapa 2, §6.2). Se llama antes de que Room abra la base de datos (en
 * `RecetarioApp.onCreate`, así vale también para WorkManager): si [dbFile] existe y su `user_version` es menor que
 * [targetVersion], la copia tal cual en [backupsDir] como `antes-de-actualizar-v<vieja>-<AAAA-MM-DD-HHmm>[-N].db`.
 *
 * 1. La abre en lectura y escritura (sin cambiar su modo de diario) y pasa el WAL al archivo principal
 *    (`PRAGMA wal_checkpoint(TRUNCATE)`); así el archivo principal lo tiene todo.
 * 2. La copia a un `.tmp` con fsync. La copia se pasa a diario clásico (`journal_mode=DELETE`): así se puede leer
 *    después (al listarla o restaurarla) sin dejar `-wal` ni `-shm` en la carpeta de copias. Se comprueba con
 *    `PRAGMA integrity_check` y solo entonces toma su nombre, sin pisar ninguna (`-2`, `-3`…).
 * 3. Se borran las de este tipo que sobran (se conservan [UPDATE_COPIES_KEPT]; nunca la recién escrita).
 *
 * Ante cualquier fallo borra el temporal y devuelve [GuardResult.Failed]: la base de datos se queda como estaba.
 */
class DatabaseUpgradeGuard(
    private val dbFile: File,
    private val backupsDir: File,
    private val clock: Clock,
    private val targetVersion: Int = AppDatabase.VERSION,
) {
    fun prepare(): GuardResult {
        if (!dbFile.isFile) return GuardResult.Ready
        var tmp: File? = null
        return try {
            val version = checkpoint() ?: return GuardResult.Ready
            if (!backupsDir.isDirectory && !backupsDir.mkdirs()) error("No se pudo crear la carpeta de copias")
            // Restos de una copia que Android cortó a medias.
            backupsDir.listFiles { f -> f.name.contains(".db.tmp") }?.forEach { it.delete() }
            val target = freeFile(beforeUpdateFileName(version, LocalDateTime.now(clock)))
            val partial = File(backupsDir, "${target.name}.tmp").also { tmp = it }
            FileInputStream(dbFile).use { input ->
                FileOutputStream(partial).use { out ->
                    input.copyTo(out, BUFFER)
                    out.flush()
                    out.fd.sync()
                }
            }
            verifyCopy(partial, version)
            // Sin REPLACE_EXISTING: nunca pisa otra copia.
            Files.move(partial.toPath(), target.toPath())
            rotate(target.name)
            Log.i(TAG, "Copia de la base de datos v$version antes de actualizar: ${target.name}")
            GuardResult.Copied(target.name)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo copiar la base de datos antes de actualizar", e)
            GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED)
        } finally {
            tmp?.let { t ->
                t.delete()
                sideFiles(t).forEach { it.delete() }
            }
        }
    }

    /** `user_version` de la base de datos; si hay que migrar, antes pasa el WAL al archivo principal. Null: no hay que migrar. */
    private fun checkpoint(): Int? {
        val db = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            // Con WAL activado Android no cambia el modo de diario del archivo (Room lo usa así).
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
            KEEP_ON_CORRUPTION,
        )
        try {
            val version = db.version
            if (version >= targetVersion) return null
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c ->
                // La primera columna es 1 si no se pudo completar (otra conexión la tenía ocupada).
                if (c.moveToFirst() && c.getInt(0) != 0) error("El punto de control del WAL no se completó")
            }
            return version
        } finally {
            db.close()
        }
    }

    /** La copia se puede abrir, está entera y es de la versión [version]; la deja en diario clásico. */
    private fun verifyCopy(copy: File, version: Int) {
        val db = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE, KEEP_ON_CORRUPTION)
        try {
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { c ->
                check(c.moveToFirst() && c.getString(0).equals("delete", ignoreCase = true)) { "La copia sigue en modo WAL" }
            }
            db.rawQuery("PRAGMA integrity_check", null).use { c ->
                check(c.moveToFirst() && c.getString(0) == "ok" && c.count == 1) { "La copia no está entera" }
            }
            check(db.version == version) { "La copia es de otra versión" }
        } finally {
            db.close()
        }
        sideFiles(copy).forEach { it.delete() }
        // Lo que escribió SQLite al cambiar el modo de diario también va a disco antes de renombrar.
        FileOutputStream(copy, true).use { it.fd.sync() }
    }

    private fun freeFile(name: String): File {
        val stem = name.removeSuffix(".db")
        val sameMinute = Regex("^${Regex.escape(stem)}(?:-(\\d{1,4}))?\\.db$")
        val highest = backupsDir.list().orEmpty().maxOfOrNull { other ->
            sameMinute.matchEntire(other)?.let { it.groupValues[1].toIntOrNull() ?: 1 } ?: 0
        } ?: 0
        return if (highest == 0) File(backupsDir, name) else File(backupsDir, "$stem-${highest + 1}.db")
    }

    private fun rotate(justWritten: String) {
        try {
            val names = backupsDir.list()?.toList().orEmpty()
            for (name in filesToDelete(names, LocalDateTime.now(clock), UPDATE_COPIES_KEPT, LocalBackupKind.BEFORE_UPDATE, setOf(justWritten))) {
                if (!File(backupsDir, name).delete()) Log.w(TAG, "No se pudo borrar la copia antigua $name")
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron borrar las copias antiguas", e)
        }
    }

    private fun sideFiles(file: File) = listOf("-wal", "-shm", "-journal").map { File(file.path + it) }

    private companion object {
        const val TAG = "DatabaseUpgradeGuard"
        const val BUFFER = 64 * 1024
    }
}
