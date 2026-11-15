package app.recetario.backup

import android.util.Log
import androidx.room.withTransaction
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.toEntity as toCategoryEntity
import app.recetario.recipes.data.writeRecipeAggregate
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Recipe
import app.recetario.settings.SettingsRepository
import app.recetario.shopping.toEntity as toShoppingEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Importa una copia sin arriesgar lo que ya hay:
 * 1. Lee y comprueba todo antes de tocar nada: el zip, `backup.json` (16 MB como mucho), su versión, cada dato,
 *    cada ruta (las entradas que podrían salirse de la carpeta, zip-slip, nunca se extraen y hacen rechazar la
 *    copia), el tamaño de cada foto (25 MB como mucho) y que estén todas las del manifiesto. Las fotos se extraen
 *    a una carpeta temporal de la caché.
 * 2. Si va a sustituir algo («Reemplazar todo», o «Combinar» cuando una receta local se actualiza), guarda antes
 *    una copia de lo actual; si no puede, no sigue.
 * 3. Trae a la carpeta de fotos las que faltan (sin pisar ninguna).
 * 4. Escribe en la base de datos en una sola transacción. Si falla, no cambia nada y se borran las fotos que
 *    acaba de traer (solo esas).
 * 5. Después quita las fotos traídas que al final nadie usa y, al reemplazar, las que usaban las recetas
 *    anteriores y ya no usa nadie, y aplica los ajustes de la copia.
 * Desde el paso 2 no se interrumpe. La carpeta temporal se borra siempre y el flujo del llamador no se cierra.
 * Nunca lanza excepciones salvo la cancelación (antes del paso 2).
 */
internal class BackupImporter(
    private val db: AppDatabase,
    private val photoStore: PhotoStore,
    private val settings: SettingsRepository,
    private val cacheDir: File,
    /** Copia previa de lo actual antes de sustituir nada; false si no se pudo hacer. */
    private val writeSafetyCopy: suspend () -> Boolean,
) {
    private class NotABackup : Exception()
    private class NewerBackup : Exception()
    private class Incomplete : Exception()

    /** [images]: fotos extraídas a la carpeta temporal (vacío al solo comprobar). */
    private class Checked(val data: ImportData, val images: Map<String, File>)

    suspend fun import(input: InputStream, mode: ImportMode): ImportResult {
        // Restos de una importación que Android cortó a medias (las importaciones van de una en una).
        cacheDir.listFiles { f -> f.name.startsWith(STAGING_PREFIX) }?.forEach { it.deleteRecursively() }
        val staging = File(cacheDir, "$STAGING_PREFIX${UUID.randomUUID()}")
        return try {
            val checked = readAndCheck(input, staging)
            // Desde aquí no se interrumpe aunque se salga de la pantalla: o no cambia nada o se termina entero.
            withContext(NonCancellable) { applyImport(checked, mode) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo importar", e)
            ImportResult.Error(messageFor(e))
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Lee y comprueba la copia entera, igual que al importar, sin escribir nada. */
    suspend fun preview(input: InputStream): PreviewResult = try {
        val checked = readAndCheck(input, staging = null)
        PreviewResult.Ok(backupRecipes = checked.data.recipes.size, localRecipes = db.backupDao().recipeCount())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "La copia no se puede importar", e)
        PreviewResult.Error(messageFor(e))
    }

    private fun messageFor(e: Exception): String = when (e) {
        is NewerBackup -> BackupMessages.NEWER_BACKUP
        is Incomplete -> BackupMessages.INCOMPLETE
        // Un zip dañado o cortado a mitad de una entrada (y la protección de rutas de Android 14) también.
        is NotABackup, is ZipException, is EOFException -> BackupMessages.NOT_A_BACKUP
        else -> BackupMessages.IMPORT_FAILED
    }

    private suspend fun readAndCheck(input: InputStream, staging: File?): Checked {
        val archive = readArchive(input, staging)
        val data = try {
            archive.backup.toImportData()
        } catch (e: Exception) {
            Log.w(TAG, "Copia no válida", e)
            throw NotABackup()
        }
        // Un zip cortado justo entre dos entradas se lee sin errores: el manifiesto dice qué fotos faltan.
        if (archive.backup.images.any { it !in archive.imageNames }) throw Incomplete()
        return Checked(data, archive.images)
    }

    private class Archive(val backup: BackupFile, val images: Map<String, File>, val imageNames: Set<String>)

    /** Lee el zip entero: `backup.json` en memoria y, si hay [staging], las fotos ahí. Aún no toca nada de la app. */
    private suspend fun readArchive(input: InputStream, staging: File?): Archive {
        if (staging != null && !staging.mkdirs()) throw IOException("No se pudo crear la carpeta temporal")
        val stagingDir = staging?.canonicalFile
        var json: ByteArray? = null
        val images = LinkedHashMap<String, File>()
        val imageNames = HashSet<String>()
        val seen = HashSet<String>()
        var unsafe = false
        // El flujo es del llamador: cerrar el zip libera lo suyo pero no lo cierra.
        ZipInputStream(BufferedInputStream(KeepOpenInputStream(input), BUFFER)).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                if (!seen.add(entry.name)) throw NotABackup()
                when (val kind = classifyBackupEntry(entry.name)) {
                    BackupEntry.Json -> json = zip.readAtMost(BACKUP_MAX_JSON_BYTES) ?: throw NotABackup()
                    is BackupEntry.Image -> {
                        if (stagingDir == null) {
                            if (!zip.copyAtMost(null, BACKUP_MAX_IMAGE_BYTES)) throw NotABackup()
                        } else {
                            val target = File(stagingDir, kind.fileName)
                            // Doble comprobación: el archivo tiene que quedar justo dentro de la carpeta temporal.
                            if (target.canonicalFile.parentFile != stagingDir) throw NotABackup()
                            val complete = target.outputStream().use { zip.copyAtMost(it, BACKUP_MAX_IMAGE_BYTES) }
                            if (!complete) throw NotABackup()
                            images[kind.fileName] = target
                        }
                        imageNames += kind.fileName
                    }
                    BackupEntry.Folder, BackupEntry.Other -> Unit
                    // No se extrae. Se rechaza al final, salvo que la copia sea de una versión más nueva
                    // (entonces el aviso útil es «actualiza la app»).
                    BackupEntry.Unsafe -> unsafe = true
                }
            }
        }
        val text = json ?: throw NotABackup()
        val backup = when (val decoded = decodeBackup(text.toString(Charsets.UTF_8))) {
            is BackupDecodeResult.Ok -> decoded.file
            BackupDecodeResult.NewerSchema -> throw NewerBackup()
            BackupDecodeResult.Invalid -> throw NotABackup()
        }
        if (unsafe) throw NotABackup()
        return Archive(backup, images, imageNames)
    }

    private suspend fun applyImport(checked: Checked, mode: ImportMode): ImportResult {
        val data = checked.data
        val dao = db.backupDao()

        // ¿Va a sustituir algo? Entonces, antes, una copia de lo actual.
        val replacesLocal = mode == ImportMode.REPLACE_ALL || data.recipes.any { (recipe, _) ->
            val local = dao.recipeUpdatedAt(recipe.id)
            local != null && recipe.updatedAt.toEpochMilli() > local
        }
        if (replacesLocal && !writeSafetyCopy()) return ImportResult.Error(BackupMessages.SAFETY_COPY_FAILED)

        // Las fotos, antes de la transacción: así nunca hay recetas confirmadas sin sus fotos. Solo se traen las que
        // usan las recetas de la copia y aún no están (mismo nombre, UUID, es la misma foto: no se pisa).
        val wanted = data.photoNames
        val moved = ArrayList<String>()
        val failed = HashSet<String>()
        for ((name, source) in checked.images) {
            if (name !in wanted) continue
            val target = photoStore.file(name)
            try {
                if (target.exists()) continue
                photoStore.dir.mkdirs()
                if (source.renameTo(target) || copyInto(source, target)) moved += name else failed += name
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo copiar la foto $name", e)
                failed += name
            }
        }

        var previousPhotos = emptySet<String>()
        val recipes = try {
            db.withTransaction {
                when (mode) {
                    ImportMode.REPLACE_ALL -> {
                        previousPhotos = dao.photoNames().toSet()
                        replaceAll(data)
                    }
                    ImportMode.MERGE -> merge(data)
                }
            }
        } catch (e: Exception) {
            // La transacción se deshizo: se deshace también lo único que se hizo fuera, las fotos recién traídas.
            deletePhotos(moved)
            throw e
        }

        // La base de datos ya tiene la copia. Lo que queda no puede deshacerla, así que cada paso va por su cuenta.
        val referenced = try {
            dao.photoNames().toSet()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer las fotos en uso", e)
            null
        }
        val copied: Int
        val notCopied: Int
        if (referenced != null) {
            // Al combinar, las de recetas que no se escribieron (la local era más reciente) sobran: son nuestras.
            deletePhotos(moved.filter { it !in referenced })
            copied = moved.count { it in referenced }
            notCopied = failed.count { it in referenced }
        } else {
            copied = moved.size
            notCopied = failed.size
        }
        if (mode == ImportMode.REPLACE_ALL) {
            // Solo las que usaban las recetas sustituidas: las de un borrador del editor no se tocan.
            if (referenced != null) deletePhotos(previousPhotos - referenced)
            try {
                settings.restoreFromBackup(data.themeSettings, data.cupMl)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron aplicar los ajustes de la copia", e)
            }
        }
        return ImportResult.Ok(recipes = recipes, images = copied, failedImages = notCopied)
    }

    /** Sustituye recetas, etiquetas, compra y categorías propias; las de serie se actualizan, nunca se borran. */
    private suspend fun replaceAll(data: ImportData): Int {
        val dao = db.backupDao()
        dao.clearStepIngredients()
        dao.clearStepPhotos()
        dao.clearRecipeTags()
        dao.clearSteps()
        dao.clearIngredients()
        dao.clearRecipes()
        dao.clearTags()
        dao.clearShoppingItems()
        dao.clearCategoriesExcept(DefaultCategories.ids.toList())
        dao.upsertCategories(data.categories.map { it.withDefaultFlag().toCategoryEntity() })
        val categoryIds = dao.categoryIds().toSet()
        for ((recipe, baseType) in data.recipes) {
            writeRecipeAggregate(db, recipe.inExistingCategory(categoryIds, baseType), recipe.updatedAt)
        }
        dao.insertShoppingItems(data.shoppingItems.map { it.toShoppingEntity() })
        return data.recipes.size
    }

    /**
     * Añade lo que falta y actualiza lo más reciente; nunca borra. Una receta de la copia gana (entera) solo si su
     * `updatedAt` es posterior. Categorías y artículos de la compra no tienen fecha de edición: se añaden los que
     * faltan y los que ya existen se dejan como están.
     */
    private suspend fun merge(data: ImportData): Int {
        val dao = db.backupDao()
        val localCategories = dao.categoryIds().toSet()
        dao.insertCategories(data.categories.filter { it.id !in localCategories }.map { it.withDefaultFlag().toCategoryEntity() })
        val categoryIds = dao.categoryIds().toSet()
        var changed = 0
        for ((recipe, baseType) in data.recipes) {
            val localUpdatedAt = dao.recipeUpdatedAt(recipe.id)
            if (localUpdatedAt != null && recipe.updatedAt.toEpochMilli() <= localUpdatedAt) continue
            writeRecipeAggregate(db, recipe.inExistingCategory(categoryIds, baseType), recipe.updatedAt)
            changed++
        }
        // Como al guardar una receta: la etiqueta que se queda sin recetas desaparece.
        db.tagDao().deleteOrphans()
        val localShopping = dao.shoppingIds().toSet()
        dao.insertShoppingItems(data.shoppingItems.filter { it.id !in localShopping }.map { it.toShoppingEntity() })
        return changed
    }

    /** Por si la caché y los archivos están en discos distintos: copia a un temporal y renombra. */
    private fun copyInto(source: File, target: File): Boolean {
        val tmp = File(target.parentFile, ".${target.name}.importando")
        return try {
            source.copyTo(tmp, overwrite = true)
            tmp.renameTo(target)
        } finally {
            tmp.delete()
        }
    }

    private fun deletePhotos(names: Collection<String>) {
        for (name in names) {
            if (!isSafeBackupFileName(name)) continue
            try {
                photoStore.delete(name)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo borrar la foto $name", e)
            }
        }
    }

    /** La marca de serie va con el id, no con lo que diga la copia. */
    private fun Category.withDefaultFlag() = copy(isDefault = id in DefaultCategories.ids)

    /** Si su categoría no existe (ni aquí ni en la copia), la receta va a la de serie de su tipo base. */
    private fun Recipe.inExistingCategory(categoryIds: Set<String>, baseType: BaseType?) =
        if (categoryId in categoryIds) this else copy(categoryId = DefaultCategories.idFor(baseType))

    /** Cerrar el zip no debe cerrar el flujo del llamador. */
    private class KeepOpenInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private companion object {
        const val TAG = "BackupImporter"
        const val BUFFER = 64 * 1024
        const val STAGING_PREFIX = "importar-"
    }
}

/** Lee hasta el final; null si pasa de [limit] bytes. */
internal fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    return if (copyAtMost(out, limit)) out.toByteArray() else null
}

/** Copia hasta el final en [out] (o lo descarta si es null); false si pasa de [limit] bytes (deja de leer ahí). */
internal fun InputStream.copyAtMost(out: OutputStream?, limit: Int): Boolean {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) return true
        total += n
        if (total > limit) return false
        out?.write(buffer, 0, n)
    }
}
