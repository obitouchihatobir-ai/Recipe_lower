package app.recetario.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.shopping.ShopSection
import app.recetario.testing.ingredient
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BackupImporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var local: BackupTestEnv
    private val others = mutableListOf<BackupTestEnv>()

    private val t1 = Instant.parse("2026-11-01T10:00:00Z")
    private val t2 = Instant.parse("2026-11-02T10:00:00Z")
    private val t3 = Instant.parse("2026-11-03T10:00:00Z")

    @Before
    fun setUp() {
        local = BackupTestEnv(tmp)
    }

    @After
    fun tearDown() {
        local.close()
        others.forEach { it.close() }
    }

    private fun recipe(id: String, title: String, updatedAt: Instant, cover: String? = null, categoryId: String = DefaultCategories.FOOD_ID) =
        sampleRecipe(
            id, title, categoryId = categoryId,
            ingredients = listOf(ingredient("$id-$title-i1", "Ingrediente de $title", 1.0, MeasureUnit.GRAM)),
            steps = listOf(Step("$id-$title-s1", "Paso de $title", emptyList(), null, setOf("$id-$title-i1"))),
            tags = listOf("Etiqueta $title"),
        ).copy(coverPhoto = cover, createdAt = t1, updatedAt = updatedAt)

    /** Datos locales con una foto, para comprobar que nada cambia. */
    private suspend fun seedLocal() {
        local.writePhoto("local.jpg", fakePhoto(1))
        local.saveAt(recipe("local", "Tortilla", t1, cover = "local.jpg"), t1)
        local.recipes.saveCategory(Category("cat-local", "Cenas", "🌙", "#4A6FA5", BaseType.FOOD, 3, false))
        local.shopping.addManual("Huevos", 6.0, MeasureUnit.PIECE)
        local.settings.setAuto(false)
        local.settings.setTheme(ThemeChoice.FRESCO)
        local.settings.setCupMl(250)
    }

    private val validZip: ByteArray
        get() = backupZip(backupOf(recipe("copia", "Paella", t2, cover = "copia.jpg")), "copia.jpg" to fakePhoto(5))

    private suspend fun assertRejectedWithoutChanges(zip: ByteArray, message: String) {
        val before = local.snapshot()
        for (mode in ImportMode.entries) {
            assertThat(local.import(zip, mode)).isEqualTo(ImportResult.Error(message))
            assertThat(local.snapshot()).isEqualTo(before)
            assertThat(local.backups()).isEmpty()
            assertThat(local.cacheFiles()).isEmpty()
        }
    }

    @Test
    fun merge_newerUpdatedAtWins() = runBlocking {
        local.saveAt(recipe("r1", "Local", t1), t1)
        local.saveAt(recipe("r2", "Local nueva", t3), t3)
        val incomingR1 = recipe("r1", "Copia", t2)
        val backup = backupOf(incomingR1, recipe("r2", "Copia vieja", t2), recipe("r3", "Nueva", t2))

        val result = local.import(backupZip(backup), ImportMode.MERGE)

        assertThat(result).isEqualTo(ImportResult.Ok(recipes = 2, images = 0))
        // La de la copia es más reciente: se sustituye entera (ingredientes, pasos y etiquetas incluidos).
        assertThat(local.recipe("r1")).isEqualTo(incomingR1)
        // La local es más reciente: se queda tal cual.
        assertThat(local.recipe("r2")!!.title).isEqualTo("Local nueva")
        assertThat(local.recipe("r2")!!.updatedAt).isEqualTo(t3)
        assertThat(local.recipe("r3")!!.title).isEqualTo("Nueva")
        assertThat(local.recipes.observeTags().first()).containsExactly("Etiqueta Copia", "Etiqueta Local nueva", "Etiqueta Nueva")
    }

    @Test
    fun merge_sameUpdatedAt_keepsLocal() = runBlocking {
        local.saveAt(recipe("r1", "Local", t2), t2)

        val result = local.import(backupZip(backupOf(recipe("r1", "Copia", t2))), ImportMode.MERGE)

        assertThat(result).isEqualTo(ImportResult.Ok(recipes = 0, images = 0))
        assertThat(local.recipe("r1")!!.title).isEqualTo("Local")
    }

    @Test
    fun merge_neverDeletesLocalOnly() = runBlocking {
        seedLocal()
        local.saveAt(recipe("en-cenas", "Crema", t1, categoryId = "cat-local"), t1)
        val before = local.snapshot()
        val renamedDefaults = defaultCategories().map { if (it.id == DefaultCategories.FOOD_ID) it.copy(name = "Platos") else it }
        val backup = backupOf(
            recipe("copia", "Paella", t2, categoryId = "cat-copia"),
            categories = renamedDefaults + Category("cat-copia", "Arroces", "🍚", "#B5733A", BaseType.FOOD, 4, false),
        ).copy(
            shoppingItems = listOf(
                BackupShoppingItem("compra-copia", "Pan", null, MeasureUnit.PIECE, null, ShopSection.OTHER, false, null, t2.toString()),
            ),
        )

        assertThat(local.import(backupZip(backup), ImportMode.MERGE)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))

        val after = local.snapshot()
        assertThat(after.recipes).containsAtLeastElementsIn(before.recipes)
        assertThat(local.recipeIds()).containsExactly("local", "en-cenas", "copia")
        assertThat(after.categories.map { it.id }).containsAtLeastElementsIn(before.categories.map { it.id })
        assertThat(after.categories.map { it.id }).contains("cat-copia")
        // Sin fecha de edición en las categorías, la local se respeta.
        assertThat(after.categories.first { it.id == DefaultCategories.FOOD_ID }.name).isEqualTo("Comidas")
        assertThat(after.shopping).containsAtLeastElementsIn(before.shopping)
        assertThat(after.shopping.map { it.name }).containsExactly("Huevos", "Pan")
        assertThat(after.photos).isEqualTo(before.photos)
        assertThat(local.recipe("copia")!!.categoryId).isEqualTo("cat-copia")
    }

    @Test
    fun merge_doesNotChangeLocalSettings() = runBlocking {
        seedLocal()
        val theme = local.settings.themeSettings.first()
        val backup = backupOf(recipe("copia", "Paella", t2)).copy(
            settings = BackupSettings(BackupThemeSettings(true, ThemeChoice.NOCTURNO, ThemeChoice.CALIDO, "19:00", "08:00"), 240),
        )

        assertThat(local.import(backupZip(backup), ImportMode.MERGE)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))

        assertThat(local.settings.themeSettings.first()).isEqualTo(theme)
        assertThat(local.settings.cupMl.first()).isEqualTo(250)
    }

    @Test
    fun merge_copiesOnlyMissingImagesThatSomeRecipeUses() = runBlocking {
        local.writePhoto("comun.jpg", fakePhoto(1))
        local.saveAt(recipe("local", "Tortilla", t1, cover = "comun.jpg"), t1)
        val backup = backupOf(recipe("copia", "Paella", t2, cover = "nueva.jpg"), recipe("otra", "Gazpacho", t2, cover = "comun.jpg"))
        val zip = backupZip(backup, "comun.jpg" to fakePhoto(2), "nueva.jpg" to fakePhoto(3), "suelta.jpg" to fakePhoto(4))

        assertThat(local.import(zip, ImportMode.MERGE)).isEqualTo(ImportResult.Ok(recipes = 2, images = 1))

        val files = local.photoFiles()
        assertThat(files.keys).containsExactly("comun.jpg", "nueva.jpg")
        // Mismo nombre (UUID) es la misma foto: la que ya estaba no se sobrescribe.
        assertThat(files.getValue("comun.jpg")).isEqualTo(fakePhoto(1).toList())
        assertThat(files.getValue("nueva.jpg")).isEqualTo(fakePhoto(3).toList())
        assertThat(local.cacheFiles()).isEmpty()
    }

    @Test
    fun merge_recipeWithUnknownCategory_goesToDefaultOfSameBaseType() = runBlocking {
        val backup = backupOf(
            recipe("bebida", "Mojito", t2, categoryId = "cat-perdida-1"),
            recipe("postre", "Flan", t2, categoryId = "cat-perdida-2"),
            recipe("otro", "Salsa", t2, categoryId = "cat-perdida-3"),
            recipe("sin-tipo", "Algo", t2, categoryId = "cat-perdida-4"),
        )
        val types = mapOf("bebida" to BaseType.DRINK, "postre" to BaseType.DESSERT, "otro" to BaseType.OTHER, "sin-tipo" to null)
        val withTypes = backup.copy(recipes = backup.recipes.map { it.copy(categoryBaseType = types.getValue(it.id)) })

        assertThat(local.import(backupZip(withTypes), ImportMode.MERGE)).isEqualTo(ImportResult.Ok(recipes = 4, images = 0))

        assertThat(local.recipe("bebida")!!.categoryId).isEqualTo(DefaultCategories.DRINK_ID)
        assertThat(local.recipe("postre")!!.categoryId).isEqualTo(DefaultCategories.DESSERT_ID)
        assertThat(local.recipe("otro")!!.categoryId).isEqualTo(DefaultCategories.FOOD_ID)
        assertThat(local.recipe("sin-tipo")!!.categoryId).isEqualTo(DefaultCategories.FOOD_ID)
        assertThat(local.recipes.observeCategories().first().map { it.id })
            .containsExactly(DefaultCategories.FOOD_ID, DefaultCategories.DRINK_ID, DefaultCategories.DESSERT_ID)
    }

    @Test
    fun replaceAll_recipeWithUnknownCategory_goesToDefaultOfSameBaseType_andDefaultsAlwaysExist() = runBlocking {
        val backup = backupOf(recipe("postre", "Flan", t2, categoryId = "cat-perdida"), categories = emptyList())
        val withType = backup.copy(recipes = backup.recipes.map { it.copy(categoryBaseType = BaseType.DESSERT) })

        assertThat(local.import(backupZip(withType), ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))

        assertThat(local.recipe("postre")!!.categoryId).isEqualTo(DefaultCategories.DESSERT_ID)
        val categories = local.recipes.observeCategories().first()
        assertThat(categories.map { it.id }).containsExactly(DefaultCategories.FOOD_ID, DefaultCategories.DRINK_ID, DefaultCategories.DESSERT_ID)
        assertThat(categories.all { it.isDefault }).isTrue()
    }

    @Test
    fun corruptZip_returnsError_andDataUnchanged() = runBlocking {
        seedLocal()
        val valid = validZip
        val badDate = backupOf(recipe("copia", "Paella", t2)).let { it.copy(recipes = it.recipes.map { r -> r.copy(updatedAt = "ayer") }) }
        for (zip in listOf(
            ByteArray(0),
            "esto no es un zip".toByteArray(),
            fakePhoto(7, size = 10_000),
            zipOf("images/copia.jpg" to fakePhoto(5)),
            zipOf("backup.json" to "{roto".toByteArray()),
            zipOf("backup.json" to "{}".toByteArray()),
            backupZip(badDate),
            valid.copyOf(valid.size / 2),
        )) {
            assertRejectedWithoutChanges(zip, "Este archivo no es una copia del Recetario.")
        }
    }

    @Test
    fun newerSchema_returnsError_andDataUnchanged() = runBlocking {
        seedLocal()
        val newer = """{"schemaVersion":2,"appVersion":"9.0.0","recetas":[{"id":"x"}]}"""
        assertRejectedWithoutChanges(
            zipOf("backup.json" to newer.toByteArray(), "images/copia.jpg" to fakePhoto(5), "videos/a.mp4" to fakePhoto(6)),
            "Esta copia es de una versión más nueva de la app. Actualiza la app e inténtalo de nuevo.",
        )
    }

    @Test
    fun zipSlipEntry_isRejected_andDataUnchanged() = runBlocking {
        seedLocal()
        val json = "backup.json" to encodeBackup(backupOf(recipe("copia", "Paella", t2))).toByteArray()
        for (evil in listOf("../evil.jpg", "/evil.jpg", "images/../../evil.jpg", "images/../evil.jpg", "images/sub/evil.jpg", "images\\evil.jpg", "fotos/evil.jpg")) {
            assertRejectedWithoutChanges(zipOf(json, evil to fakePhoto(8)), "Este archivo no es una copia del Recetario.")
            assertRejectedWithoutChanges(zipOf(evil to fakePhoto(8), json), "Este archivo no es una copia del Recetario.")
        }
        assertThat(tmp.root.walkTopDown().filter { it.name == "evil.jpg" }.toList()).isEmpty()
    }

    @Test
    fun replaceAll_writesSafetyBackupFirst() = runBlocking {
        seedLocal()
        val before = local.snapshot()

        assertThat(local.import(validZip, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 1))

        assertThat(local.recipeIds()).containsExactly("copia")
        assertThat(local.backups()).containsExactly("antes-de-importar-2026-11-13-2140.zip")
        val safety = File(local.backupsDir, "antes-de-importar-2026-11-13-2140.zip").readBytes()
        assertThat(unzip(safety).keys).containsExactly("backup.json", "images/local.jpg")

        // La copia previa devuelve exactamente lo que había.
        val restored = BackupTestEnv(tmp).also { others += it }
        assertThat(restored.import(safety, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 1))
        assertThat(restored.snapshot()).isEqualTo(before)

        // Otra importación en el mismo minuto no pisa la copia anterior.
        assertThat(local.import(validZip, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))
        assertThat(local.backups()).containsExactly("antes-de-importar-2026-11-13-2140.zip", "antes-de-importar-2026-11-13-2140-2.zip")
        assertThat(File(local.backupsDir, "antes-de-importar-2026-11-13-2140.zip").readBytes().toList()).isEqualTo(safety.toList())
    }

    @Test
    fun replaceAll_safetyCopyFailure_abortsWithoutChanges() = runBlocking {
        seedLocal()
        // «backups» es un archivo: no se puede crear la carpeta de la copia previa.
        local.backupsDir.writeText("no soy una carpeta")
        val before = local.snapshot()

        val result = local.import(validZip, ImportMode.REPLACE_ALL)

        assertThat(result).isEqualTo(ImportResult.Error("No se pudo hacer una copia de seguridad previa. No se ha cambiado nada."))
        assertThat(local.snapshot()).isEqualTo(before)
        assertThat(local.cacheFiles()).isEmpty()
        assertThat(local.backupsDir.readText()).isEqualTo("no soy una carpeta")
    }

    @Test
    fun replaceAll_deletesOnlyPhotosThatTheOldRecipesUsed() = runBlocking {
        seedLocal()
        // Foto de un borrador del editor: no es de ninguna receta guardada y no se toca.
        local.writePhoto("borrador.jpg", fakePhoto(3))

        assertThat(local.import(validZip, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 1))

        assertThat(local.photoFiles().keys).containsExactly("borrador.jpg", "copia.jpg")
    }

    @Test
    fun replaceAll_appliesSettingsFromTheBackup() = runBlocking {
        seedLocal()
        val backup = backupOf(recipe("copia", "Paella", t2)).copy(
            settings = BackupSettings(BackupThemeSettings(true, ThemeChoice.CALIDO, ThemeChoice.FRESCO, "22:00", "07:30"), 240),
        )

        assertThat(local.import(backupZip(backup), ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))

        val theme = local.settings.themeSettings.first()
        assertThat(theme.auto).isTrue()
        assertThat(theme.dayTheme).isEqualTo(ThemeChoice.FRESCO)
        assertThat(theme.nightStart.toString()).isEqualTo("22:00")
        assertThat(theme.nightEnd.toString()).isEqualTo("07:30")
        assertThat(local.settings.cupMl.first()).isEqualTo(240)
    }

    @Test
    fun importingTwice_isStable() = runBlocking {
        seedLocal()
        val zip = local.exportBytes()
        val before = local.snapshot()

        assertThat(local.import(zip, ImportMode.MERGE)).isEqualTo(ImportResult.Ok(recipes = 0, images = 0))
        assertThat(local.snapshot()).isEqualTo(before)
        assertThat(local.import(zip, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))
        assertThat(local.snapshot()).isEqualTo(before)
    }
}
