package app.recetario.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val envs = mutableListOf<BackupTestEnv>()

    private fun env() = BackupTestEnv(tmp).also { envs += it }

    @After
    fun tearDown() = envs.forEach { it.close() }

    private fun fullRecipe(id: String, categoryId: String, cover: String?, stepPhotos: List<String>, tags: List<String>) = Recipe(
        id = id, title = "Receta $id", categoryId = categoryId, coverPhoto = cover, description = "Descripción de $id",
        totalMinutes = 40, servings = 6, difficulty = Difficulty.MEDIUM,
        flavor = FlavorProfile(sweet = 1, salty = 3, sour = 2, bitter = 1, spicy = 3, intensity = Intensity.INTENSE, rich = true),
        serveTemp = ServeTemp.ROOM, moments = setOf(Moment.BREAKFAST, Moment.NIGHT), alcoholic = true, course = Course.STARTER,
        ingredients = listOf(
            Ingredient("$id-i1", "Para la salsa", "Tomate", 2.5, MeasureUnit.KILOGRAM, null, "maduro"),
            Ingredient("$id-i2", null, "Vino", 0.75, MeasureUnit.CUSTOM, "copas", ""),
            Ingredient("$id-i3", null, "Sal", null, MeasureUnit.TO_TASTE, null, ""),
        ),
        steps = listOf(
            Step("$id-s1", "Primero.", stepPhotos, 90, setOf("$id-i1", "$id-i3")),
            Step("$id-s2", "Después.", emptyList(), null, emptySet()),
        ),
        tags = tags, notes = "Notas de $id", favorite = true, sourceUrl = "https://example.com/$id", aiGenerated = true,
        createdAt = Instant.parse("2026-01-02T03:04:05.678Z"), updatedAt = Instant.EPOCH,
    )

    @Test
    fun export_wipe_import_replace_restoresIdenticalData() = runBlocking {
        val a = env()
        a.recipes.saveCategory(Category("cat-salsas", "Salsas", "🥫", "#4A6FA5", BaseType.OTHER, 3, false))
        // Las de serie van con su nombre actual.
        a.recipes.saveCategory(Category(DefaultCategories.FOOD_ID, "Platos", "🍲", "#C8553D", BaseType.FOOD, 0, true))
        a.writePhoto("portada.jpg", fakePhoto(1))
        a.writePhoto("paso-a.jpg", fakePhoto(2))
        a.writePhoto("paso-b.jpg", fakePhoto(3, size = 50_000))
        a.saveAt(fullRecipe("r1", "cat-salsas", "portada.jpg", listOf("paso-a.jpg", "paso-b.jpg"), listOf("Cena", "Rápido")), Instant.parse("2026-11-01T10:00:00Z"))
        a.saveAt(fullRecipe("r2", DefaultCategories.DRINK_ID, null, emptyList(), listOf("cena", "Verano")), Instant.parse("2026-11-02T10:00:00Z"))
        a.shopping.addManual("Leche", 1.0, MeasureUnit.LITER)
        a.shopping.addManual("Pan", null, MeasureUnit.PIECE)
        a.shopping.toggle(a.db.shoppingDao().getAll().first { it.name == "Pan" }.id)
        a.settings.setAuto(false)
        a.settings.setTheme(ThemeChoice.NOCTURNO)
        a.settings.setDayTheme(ThemeChoice.FRESCO)
        a.settings.setNightWindow(LocalTime.of(21, 30), LocalTime.of(6, 30))
        a.settings.setCupMl(250)
        val before = a.snapshot()
        assertThat(before.recipes).hasSize(2)
        assertThat(before.photos).hasSize(3)
        val zip = a.exportBytes()

        // Otro teléfono, con datos que deben desaparecer.
        val b = env()
        b.writePhoto("vieja.jpg", fakePhoto(9))
        b.saveAt(fullRecipe("vieja", DefaultCategories.FOOD_ID, "vieja.jpg", emptyList(), listOf("Antigua")), Instant.parse("2026-10-01T10:00:00Z"))
        b.recipes.saveCategory(Category("cat-vieja", "Vieja", "🥄", "#000000", BaseType.FOOD, 5, false))
        b.shopping.addManual("Huevos", 6.0, MeasureUnit.PIECE)

        val result = b.import(zip, ImportMode.REPLACE_ALL)

        assertThat(result).isEqualTo(ImportResult.Ok(recipes = 2, images = 3))
        assertThat(b.snapshot()).isEqualTo(before)
        assertThat(b.cacheFiles()).isEmpty()
    }

    @Test
    fun export_zipHasTheJsonAndTheImages() = runBlocking {
        val a = env()
        a.writePhoto("portada.jpg", fakePhoto(1))
        a.saveAt(fullRecipe("r1", DefaultCategories.FOOD_ID, "portada.jpg", emptyList(), emptyList()), Instant.parse("2026-11-01T10:00:00Z"))

        val entries = unzip(a.exportBytes())

        assertThat(entries.keys).containsExactly("backup.json", "images/portada.jpg").inOrder()
        assertThat(entries.getValue("images/portada.jpg").toList()).isEqualTo(fakePhoto(1).toList())
        val decoded = decodeBackup(String(entries.getValue("backup.json")))
        val file = (decoded as BackupDecodeResult.Ok).file
        assertThat(file.schemaVersion).isEqualTo(1)
        assertThat(file.appVersion).isEqualTo("0.1.0")
        assertThat(file.createdAt).isEqualTo("2026-11-13T20:40:00Z")
        assertThat(file.categories.map { it.id }).containsExactly(DefaultCategories.FOOD_ID, DefaultCategories.DRINK_ID, DefaultCategories.DESSERT_ID)
    }

    @Test
    fun export_skipsMissingPhotoFiles() = runBlocking {
        val a = env()
        a.writePhoto("esta.jpg", fakePhoto(1))
        a.saveAt(fullRecipe("r1", DefaultCategories.FOOD_ID, "esta.jpg", listOf("falta.jpg"), emptyList()), Instant.parse("2026-11-01T10:00:00Z"))

        val zip = a.exportBytes()

        val entries = unzip(zip)
        assertThat(entries.keys).containsExactly("backup.json", "images/esta.jpg")
        // La receta sigue apuntando a la foto que falta (la app muestra un marcador).
        assertThat(String(entries.getValue("backup.json"))).contains("falta.jpg")

        val b = env()
        assertThat(b.import(zip, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 1))
        assertThat(b.recipe("r1")!!.steps.first().photos).containsExactly("falta.jpg")
        assertThat(b.photoFiles().keys).containsExactly("esta.jpg")
    }

    @Test
    fun export_doesNotIncludeBackupStatusOrNotificationFlag() = runBlocking {
        val a = env()
        a.settings.updateBackupStatus { it.copy(lastExternal = Instant.parse("2026-11-01T10:00:00Z"), driveUri = "content://drive/x") }
        a.settings.setNotificationPermissionAsked()

        val json = String(unzip(a.exportBytes()).getValue("backup.json"))

        assertThat(json).doesNotContain("content://drive/x")
        assertThat(json).doesNotContain("lastExternal")
        assertThat(json).doesNotContain("notification")
        assertThat(a.settings.backupStatus.first().lastExternal).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"))
    }
}
