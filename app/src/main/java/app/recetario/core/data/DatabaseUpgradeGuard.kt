package app.recetario.core.data

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
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

    /**
     * No se pudo hacer la copia: **no se migra** ni se abre la base de datos. [databaseUnreadable]: la base de datos
     * está dañada o no se puede leer (no es falta de espacio); la pantalla de error ofrece guardarla fuera tal cual.
     */
    data class Failed(val message: String, val databaseUnreadable: Boolean = false) : GuardResult
}

/**
 * Copia previa a la migración (diseño de la etapa 2, §6.2). Se llama antes de que Room abra la base de datos (en
 * `RecetarioApp.onCreate`, así vale también para WorkManager): si [dbFile] existe y su `user_version` es menor que
 * [targetVersion], la copia tal cual en [backupsDir] como `antes-de-actualizar-v<vieja>-<AAAA-MM-DD-HHmm>[-N].db`.
 *
 * 0. Lee `user_version` abriéndola en solo lectura (lo único que se hace en un arranque normal; si eso falla sin
 *    estar dañada, como en Android 8 sin `-shm`, la lee en lectura y escritura).
 * 1. Solo si hay que migrar, la abre en lectura y escritura, en WAL como la abre Room, y pasa el WAL al archivo
 *    principal (`PRAGMA wal_checkpoint(TRUNCATE)`); así el archivo principal lo tiene todo.
 * 2. La copia a un `.tmp` con fsync. La copia se pasa a diario clásico (`journal_mode=DELETE`): así se puede leer
 *    después (al listarla o restaurarla) sin dejar `-wal` ni `-shm` en la carpeta de copias. Se comprueba con
 *    `PRAGMA integrity_check` y solo entonces toma su nombre, sin pisar ninguna (`-2`, `-3`…).
 * 3. Se borran las de este tipo que sobran (se conservan [UPDATE_COPIES_KEPT]; nunca la recién escrita).
 *
 * Ante cualquier fallo borra el temporal (y sus `-wal`, `-shm` y `-journal`) y devuelve [GuardResult.Failed]: la
 * base de datos se queda como estaba y nunca se borra. Si no se puede leer (dañada), con
 * [BackupMessages.DATABASE_UNREADABLE]; si falla al copiar o escribir, con [BackupMessages.UPDATE_COPY_FAILED].
 * [beforeRename] se llama con la copia ya comprobada, justo antes de darle su nombre (las pruebas lo usan para
 * fallar después de escribir el temporal).
 */
class DatabaseUpgradeGuard(
    private val dbFile: File,
    private val backupsDir: File,
    private val clock: Clock,
    private val targetVersion: Int = AppDatabase.VERSION,
    private val beforeRename: (File) -> Unit = {},
    /** Apertura en solo lectura para leer `user_version`; las pruebas la hacen fallar. */
    private val openReadOnly: (File) -> SQLiteDatabase = {
        SQLiteDatabase.openDatabase(it.path, null, SQLiteDatabase.OPEN_READONLY, KEEP_ON_CORRUPTION)
    },
) {
    /** La base de datos (o su copia, idéntica) está dañada. */
    private class UnreadableDatabaseException(message: String) : Exception(message)

    fun prepare(): GuardResult {
        if (!dbFile.isFile) return GuardResult.Ready
        val version = try {
            readVersion()
        } catch (e: Exception) {
            return readFailure(e)
        }
        if (version >= targetVersion) return GuardResult.Ready
        try {
            checkpoint()
        } catch (e: Exception) {
            return readFailure(e)
        }
        var tmp: File? = null
        return try {
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
            beforeRename(partial)
            // Sin REPLACE_EXISTING: nunca pisa otra copia.
            Files.move(partial.toPath(), target.toPath())
            rotate(target.name)
            Log.i(TAG, "Copia de la base de datos v$version antes de actualizar: ${target.name}")
            GuardResult.Copied(target.name)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo copiar la base de datos antes de actualizar", e)
            // La copia es idéntica: si está dañada, lo está la base de datos (no es falta de espacio).
            if (e is UnreadableDatabaseException || e is SQLiteDatabaseCorruptException) unreadable() else GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED)
        } finally {
            tmp?.let { t ->
                t.delete()
                sideFiles(t).forEach { it.delete() }
            }
        }
    }

    /** Al leer la base de datos: sin espacio es «No hay espacio…»; cualquier otro fallo, que no se puede leer. */
    private fun readFailure(e: Exception): GuardResult.Failed {
        Log.e(TAG, "No se pudo leer la base de datos antes de actualizar", e)
        return if (e is SQLiteFullException) GuardResult.Failed(BackupMessages.UPDATE_COPY_FAILED) else unreadable()
    }

    private fun unreadable() = GuardResult.Failed(BackupMessages.DATABASE_UNREADABLE, databaseUnreadable = true)

    /**
     * `user_version`, en solo lectura y sin tocar el modo de diario: es lo único que se hace en un arranque normal.
     * Con SQLite anterior a 3.22 (Android 8.0 y 8.1) una base de datos en WAL no se abre en solo lectura si no
     * existe su `-shm`, que es lo normal tras cerrarla bien: si falla por algo que no es un daño claro, se lee una
     * vez más abriéndola en lectura y escritura (en WAL, como Room). Solo si también falla no se puede leer.
     */
    private fun readVersion(): Int {
        try {
            return versionOf(openReadOnly(dbFile))
        } catch (e: SQLiteDatabaseCorruptException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la versión en solo lectura; se intenta en lectura y escritura", e)
        }
        return versionOf(openReadWrite())
    }

    private fun versionOf(db: SQLiteDatabase): Int {
        try {
            return db.version
        } finally {
            db.close()
        }
    }

    // Con ENABLE_WRITE_AHEAD_LOGGING Android la deja en WAL (o la pone), como la abre Room después; sin él la pasaría
    // a su modo de diario por defecto.
    private fun openReadWrite(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        dbFile.path,
        null,
        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        KEEP_ON_CORRUPTION,
    )

    /** Pasa el WAL al archivo principal, para copiar solo ese archivo. */
    private fun checkpoint() {
        val db = openReadWrite()
        try {
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c ->
                // La primera columna es 1 si no se pudo completar (otra conexión la tenía ocupada).
                if (c.moveToFirst() && c.getInt(0) != 0) error("El punto de control del WAL no se completó")
            }
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
                if (!(c.moveToFirst() && c.getString(0) == "ok" && c.count == 1)) throw UnreadableDatabaseException("La copia no está entera")
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
