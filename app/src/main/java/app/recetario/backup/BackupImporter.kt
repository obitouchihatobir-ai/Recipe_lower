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
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Importa una copia sin arriesgar lo que ya hay:
 * 1. Lee y comprueba todo antes de tocar nada: el zip, `backup.json`, su versión, cada dato y cada ruta
 *    (las entradas que podrían salirse de la carpeta, zip-slip, nunca se extraen y hacen rechazar la copia).
 *    Las fotos se extraen a una carpeta temporal de la caché.
 * 2. «Reemplazar todo» guarda antes una copia de lo actual; si no puede, no sigue.
 * 3. Escribe en la base de datos en una sola transacción: si algo falla, no cambia nada.
 * 4. Solo después mueve las fotos a su carpeta (sin pisar ninguna) y, al reemplazar, borra las que usaban las
 *    recetas anteriores y ya no usa nadie, y aplica los ajustes de la copia.
 * La carpeta temporal se borra siempre. Nunca lanza excepciones salvo la cancelación (antes del paso 2).
 */
internal class BackupImporter(
    private val db: AppDatabase,
    private val photoStore: PhotoStore,
    private val settings: SettingsRepository,
    private val cacheDir: File,
    /** Copia previa de lo actual antes de «Reemplazar todo»; false si no se pudo hacer. */
    private val writeSafetyCopy: suspend () -> Boolean,
) {
    private class NotABackup : Exception()
    private class NewerBackup : Exception()

    private class Archive(val backup: BackupFile, val images: Map<String, File>)

    suspend fun import(input: InputStream, mode: ImportMode): ImportResult {
        // Restos de una importación que Android cortó a medias (las importaciones van de una en una).
        cacheDir.listFiles { f -> f.name.startsWith(STAGING_PREFIX) }?.forEach { it.deleteRecursively() }
        val staging = File(cacheDir, "$STAGING_PREFIX${UUID.randomUUID()}")
        return try {
            val archive = readArchive(input, staging)
            val data = try {
                archive.backup.toImportData()
            } catch (e: Exception) {
                Log.w(TAG, "Copia no válida", e)
                throw NotABackup()
            }
            // Desde aquí no se interrumpe aunque se salga de la pantalla: o no cambia nada o se termina entero.
            withContext(NonCancellable) { applyImport(data, archive.images, mode) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: NewerBackup) {
            ImportResult.Error(BackupMessages.NEWER_BACKUP)
        } catch (_: NotABackup) {
            ImportResult.Error(BackupMessages.NOT_A_BACKUP)
        } catch (e: ZipException) {
            Log.w(TAG, "Zip dañado", e)
            ImportResult.Error(BackupMessages.NOT_A_BACKUP)
        } catch (e: EOFException) {
            Log.w(TAG, "Zip incompleto", e)
            ImportResult.Error(BackupMessages.NOT_A_BACKUP)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo importar", e)
            ImportResult.Error(BackupMessages.IMPORT_FAILED)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Lee el zip entero: guarda `backup.json` en memoria y las fotos en [staging]. Aún no toca nada de la app. */
    private suspend fun readArchive(input: InputStream, staging: File): Archive {
        if (!staging.mkdirs()) throw IOException("No se pudo crear la carpeta temporal")
        val stagingDir = staging.canonicalFile
        var json: ByteArray? = null
        val images = LinkedHashMap<String, File>()
        val seen = HashSet<String>()
        var unsafe = false
        ZipInputStream(BufferedInputStream(input, BUFFER)).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                if (!seen.add(entry.name)) throw NotABackup()
                when (val kind = classifyBackupEntry(entry.name)) {
                    BackupEntry.Json -> json = zip.readAtMost(MAX_JSON_BYTES) ?: throw NotABackup()
                    is BackupEntry.Image -> {
                        val target = File(stagingDir, kind.fileName)
                        // Doble comprobación: el archivo tiene que quedar justo dentro de la carpeta temporal.
                        if (target.canonicalFile.parentFile != stagingDir) throw NotABackup()
                        target.outputStream().use { zip.copyTo(it, BUFFER) }
                        images[kind.fileName] = target
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
        return Archive(backup, images)
    }

    private suspend fun applyImport(data: ImportData, images: Map<String, File>, mode: ImportMode): ImportResult {
        if (mode == ImportMode.REPLACE_ALL && !writeSafetyCopy()) return ImportResult.Error(BackupMessages.SAFETY_COPY_FAILED)

        val dao = db.backupDao()
        var previousPhotos = emptySet<String>()
        val recipes = db.withTransaction {
            when (mode) {
                ImportMode.REPLACE_ALL -> {
                    previousPhotos = dao.photoNames().toSet()
                    replaceAll(data)
                }
                ImportMode.MERGE -> merge(data)
            }
        }

        // La base de datos ya tiene la copia. Lo que queda no puede deshacerla, así que cada paso va por su cuenta.
        val referenced = try {
            dao.photoNames().toSet()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer las fotos en uso", e)
            null
        }
        val copied = moveImages(images, referenced ?: data.photoNames)
        if (mode == ImportMode.REPLACE_ALL) {
            // Solo las que usaban las recetas sustituidas: las de un borrador del editor no se tocan.
            if (referenced != null) deleteUnused(previousPhotos - referenced)
            try {
                settings.restoreFromBackup(data.themeSettings, data.cupMl)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron aplicar los ajustes de la copia", e)
            }
        }
        return ImportResult.Ok(recipes = recipes, images = copied)
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

    /** Mueve a la carpeta de fotos las que alguna receta usa y aún no están; devuelve cuántas. */
    private fun moveImages(images: Map<String, File>, referenced: Set<String>): Int {
        var moved = 0
        for ((name, source) in images) {
            if (name !in referenced) continue
            val target = photoStore.file(name)
            try {
                // Mismo nombre (UUID) = misma foto: la que ya está no se sobrescribe.
                if (target.exists()) continue
                photoStore.dir.mkdirs()
                if (source.renameTo(target) || copyInto(source, target)) moved++
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo copiar la foto $name", e)
            }
        }
        return moved
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

    private fun deleteUnused(names: Set<String>) {
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

    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "BackupImporter"
        const val BUFFER = 64 * 1024
        const val STAGING_PREFIX = "importar-"

        /** Un `backup.json` mayor no es nuestro (500 recetas ocupan unos pocos MB). */
        const val MAX_JSON_BYTES = 64 * 1024 * 1024
    }
}
