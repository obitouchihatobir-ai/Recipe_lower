package app.recetario.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.ThemeSettings
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RoomRecipeRepository
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Recipe
import app.recetario.settings.SettingsRepository
import app.recetario.shopping.RoomShoppingRepository
import app.recetario.shopping.ShoppingItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 21:40 en Madrid. */
val backupClock: Clock = Clock.fixed(Instant.parse("2026-11-13T20:40:00Z"), ZoneId.of("Europe/Madrid"))

/** Un teléfono de prueba: base de datos en memoria, carpetas temporales y ajustes propios. */
class BackupTestEnv(private val tmp: TemporaryFolder, val clock: Clock = backupClock) {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .addCallback(DefaultCategories.callback)
        .build()
    val filesDir: File = tmp.newFolder()
    val cacheDir: File = tmp.newFolder()
    val photos = PhotoStore(File(filesDir, "photos"))
    private val settingsFile = File(tmp.newFolder(), "ajustes.preferences_pb")
    val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob()), produceFile = { settingsFile }),
    )
    val recipes = RoomRecipeRepository(db, photos, clock)
    val shopping = RoomShoppingRepository(db, clock, settings.cupMl)
    val manager = BackupManager(db, photos, settings, clock, cacheDir = cacheDir, filesDir = filesDir, appVersion = "0.1.0")
    val backupsDir = File(filesDir, "backups")

    fun close() = db.close()

    fun writePhoto(name: String, bytes: ByteArray) {
        photos.dir.mkdirs()
        photos.file(name).writeBytes(bytes)
    }

    /** Guarda la receta con `updatedAt` = [at] (el repositorio lo fija con su reloj). */
    suspend fun saveAt(recipe: Recipe, at: Instant) =
        RoomRecipeRepository(db, photos, Clock.fixed(at, clock.zone)).save(recipe)

    suspend fun exportBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        check(manager.export(out) == ExportResult.Ok)
        return out.toByteArray()
    }

    suspend fun import(zip: ByteArray, mode: ImportMode): ImportResult = manager.import(ByteArrayInputStream(zip), mode)

    suspend fun preview(zip: ByteArray): PreviewResult = manager.preview(ByteArrayInputStream(zip))

    fun localFile(name: String) = File(backupsDir, name)

    suspend fun recipeIds(): List<String> = recipes.observeSummaries().first().map { it.id }.sorted()

    suspend fun recipe(id: String): Recipe? = recipes.observeRecipe(id).first()

    /** Todo lo que el usuario ve: recetas completas, categorías, etiquetas, compra, ajustes y fotos (byte a byte). */
    suspend fun snapshot() = Snapshot(
        recipes = recipeIds().map { recipe(it) },
        categories = recipes.observeCategories().first(),
        tags = recipes.observeTags().first(),
        shopping = db.shoppingDao().getAll(),
        theme = settings.themeSettings.first(),
        cupMl = settings.cupMl.first(),
        photos = photoFiles(),
    )

    fun photoFiles(): Map<String, List<Byte>> =
        (photos.dir.listFiles() ?: emptyArray()).associate { it.name to it.readBytes().toList() }.toSortedMap()

    /** Archivos que quedan en la caché (la importación debe limpiar su carpeta temporal). */
    fun cacheFiles(): List<String> = cacheDir.walkTopDown().filter { it != cacheDir }.map { it.relativeTo(cacheDir).path }.toList()

    fun backups(): List<String> = (backupsDir.listFiles() ?: emptyArray()).map { it.name }.sorted()
}

data class Snapshot(
    val recipes: List<Recipe?>,
    val categories: List<Category>,
    val tags: List<String>,
    val shopping: List<ShoppingItemEntity>,
    val theme: ThemeSettings,
    val cupMl: Int,
    val photos: Map<String, List<Byte>>,
)

/** Un zip con las entradas dadas, en ese orden. */
fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * Un zip cortado justo después de sus primeras [keep] entradas (sin las demás ni el directorio central): quien lo
 * lee en flujo lo ve completo, solo el manifiesto de fotos delata lo que falta.
 */
fun zipCutAfter(keep: Int, vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    var cut = 0
    ZipOutputStream(out).use { zip ->
        entries.forEachIndexed { i, (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
            zip.flush()
            if (i == keep - 1) cut = out.size()
        }
    }
    return out.toByteArray().copyOf(cut)
}

/** Entradas de un zip: nombre → contenido. */
fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val result = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            result[entry.name] = zip.readBytes()
        }
    }
    return result
}

/** Zip de [file] con [images]; si el manifiesto de [file] está vacío, se rellena con esas fotos (como el exportador). */
fun backupZip(file: BackupFile, vararg images: Pair<String, ByteArray>): ByteArray {
    val withManifest = if (file.images.isEmpty()) file.copy(images = images.map { it.first }) else file
    return zipOf(BACKUP_JSON_ENTRY to encodeBackup(withManifest).toByteArray(), *images.map { (n, b) -> "images/$n" to b }.toTypedArray())
}

/** Una copia con las categorías de serie, sin compra y con los ajustes de serie. */
fun backupOf(vararg recipes: Recipe, categories: List<Category> = defaultCategories()): BackupFile = backupFileOf(
    appVersion = "0.1.0", createdAt = backupClock.instant(), categories = categories, recipes = recipes.toList(),
    shoppingItems = emptyList(), themeSettings = ThemeSettings(), cupMl = 240,
)

fun defaultCategories() = listOf(
    Category(DefaultCategories.FOOD_ID, "Comidas", "🍲", "#C8553D", app.recetario.recipes.model.BaseType.FOOD, 0, true),
    Category(DefaultCategories.DRINK_ID, "Bebidas", "🍹", "#2A7F8E", app.recetario.recipes.model.BaseType.DRINK, 1, true),
    Category(DefaultCategories.DESSERT_ID, "Postres", "🍮", "#B5733A", app.recetario.recipes.model.BaseType.DESSERT, 2, true),
)

/** Unos bytes cualquiera que hacen de foto. */
fun fakePhoto(seed: Int, size: Int = 3_000): ByteArray = ByteArray(size) { ((it * 31 + seed * 7) % 251).toByte() }
