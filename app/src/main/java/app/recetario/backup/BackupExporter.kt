package app.recetario.backup

import androidx.room.withTransaction
import app.recetario.core.data.AppDatabase
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.toModel
import app.recetario.recipes.data.toRecipe
import app.recetario.settings.SettingsRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.Clock
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escribe la copia completa como zip: `backup.json` y, en `images/`, las fotos que usan las recetas y existen en
 * disco (una foto que falta no impide la copia; la receta conserva su nombre y la app muestra un marcador).
 * Va en flujo: nunca tiene el zip entero en memoria.
 */
internal class BackupExporter(
    private val db: AppDatabase,
    private val photoStore: PhotoStore,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val appVersion: String,
) {
    /** Escribe el zip en [out] sin cerrarlo (lo cierra quien lo abrió). Lanza la excepción si algo falla. */
    suspend fun write(out: OutputStream) {
        val backup = snapshot()
        val photoNames = backup.recipes.flatMapTo(LinkedHashSet()) { r -> listOfNotNull(r.coverPhoto) + r.steps.flatMap { it.photos } }
        ZipOutputStream(BufferedOutputStream(KeepOpenOutputStream(out), BUFFER)).use { zip ->
            zip.putNextEntry(entry(BACKUP_JSON_ENTRY))
            zip.write(encodeBackup(backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // Las fotos ya son JPEG comprimidos: volver a comprimirlas solo gasta tiempo.
            zip.setLevel(Deflater.NO_COMPRESSION)
            for (name in photoNames) {
                currentCoroutineContext().ensureActive()
                if (!isSafeBackupFileName(name)) continue
                val input = try {
                    photoStore.file(name).inputStream()
                } catch (_: FileNotFoundException) {
                    continue
                }
                input.use {
                    zip.putNextEntry(entry(BACKUP_IMAGES_PREFIX + name))
                    it.copyTo(zip, BUFFER)
                    zip.closeEntry()
                }
            }
        }
    }

    /** Todos los datos en un instante coherente (una transacción de lectura) y los ajustes que viajan con ellos. */
    private suspend fun snapshot(): BackupFile {
        val dao = db.backupDao()
        val (categories, recipes, shopping) = db.withTransaction {
            Triple(dao.categories(), dao.recipes(), dao.shoppingItems())
        }
        return backupFileOf(
            appVersion = appVersion,
            createdAt = clock.instant(),
            categories = categories.map { it.toModel() },
            recipes = recipes.map { it.toRecipe() },
            shoppingItems = shopping.map { it.toModel() },
            themeSettings = settings.themeSettings.first(),
            cupMl = settings.cupMl.first(),
        )
    }

    private fun entry(name: String) = ZipEntry(name).apply { time = clock.millis() }

    /** Cerrar el zip vuelca y termina el archivo, pero deja abierto el flujo del llamador. */
    private class KeepOpenOutputStream(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = out.flush()
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
