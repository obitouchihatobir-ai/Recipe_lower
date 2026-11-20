package app.recetario.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.diary.CookLogEntry
import app.recetario.pairing.data.PairingRatingEntity
import app.recetario.plan.data.MealPlanEntity
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
import java.time.LocalDate
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
    fun export_wipe_import_replace_restoresIdenticalData() = runBlocking<Unit> {
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
    fun export_zipHasTheJsonAndTheImages() = runBlocking<Unit> {
        val a = env()
        a.writePhoto("portada.jpg", fakePhoto(1))
        a.saveAt(fullRecipe("r1", DefaultCategories.FOOD_ID, "portada.jpg", emptyList(), emptyList()), Instant.parse("2026-11-01T10:00:00Z"))

        val entries = unzip(a.exportBytes())

        assertThat(entries.keys).containsExactly("backup.json", "images/portada.jpg").inOrder()
        assertThat(entries.getValue("images/portada.jpg").toList()).isEqualTo(fakePhoto(1).toList())
        val decoded = decodeBackup(String(entries.getValue("backup.json")))
        val file = (decoded as BackupDecodeResult.Ok).file
        assertThat(file.schemaVersion).isEqualTo(2)
        assertThat(file.appVersion).isEqualTo("0.1.0")
        assertThat(file.createdAt).isEqualTo("2026-11-13T20:40:00Z")
        assertThat(file.categories.map { it.id }).containsExactly(DefaultCategories.FOOD_ID, DefaultCategories.DRINK_ID, DefaultCategories.DESSERT_ID)
        // Manifiesto: exactamente las fotos que van en el zip.
        assertThat(file.images).containsExactly("portada.jpg")
    }

    @Test
    fun export_skipsMissingPhotoFiles() = runBlocking<Unit> {
        val a = env()
        a.writePhoto("esta.jpg", fakePhoto(1))
        a.saveAt(fullRecipe("r1", DefaultCategories.FOOD_ID, "esta.jpg", listOf("falta.jpg"), emptyList()), Instant.parse("2026-11-01T10:00:00Z"))

        val zip = a.exportBytes()

        val entries = unzip(zip)
        assertThat(entries.keys).containsExactly("backup.json", "images/esta.jpg")
        // La receta sigue apuntando a la foto que falta (la app muestra un marcador), pero no está en el manifiesto.
        assertThat(String(entries.getValue("backup.json"))).contains("falta.jpg")
        assertThat((decodeBackup(String(entries.getValue("backup.json"))) as BackupDecodeResult.Ok).file.images).containsExactly("esta.jpg")

        val b = env()
        assertThat(b.import(zip, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 1))
        assertThat(b.recipe("r1")!!.steps.first().photos).containsExactly("falta.jpg")
        assertThat(b.photoFiles().keys).containsExactly("esta.jpg")
    }

    @Test
    fun export_unsafePhotoName_isTreatedAsAMissingPhoto() = runBlocking<Unit> {
        val a = env()
        // Un nombre con ruta no puede ser una foto nuestra: no se exporta aunque exista ese archivo.
        java.io.File(a.filesDir, "fuera.jpg").writeBytes(fakePhoto(4))
        a.writePhoto("buena.jpg", fakePhoto(5))
        a.saveAt(fullRecipe("r1", DefaultCategories.FOOD_ID, "../fuera.jpg", listOf("buena.jpg", "../fuera.jpg"), emptyList()), Instant.parse("2026-11-01T10:00:00Z"))

        val zip = a.exportBytes()

        val entries = unzip(zip)
        assertThat(entries.keys).containsExactly("backup.json", "images/buena.jpg")
        val json = String(entries.getValue("backup.json"))
        assertThat(json).contains("../fuera.jpg")
        assertThat((decodeBackup(json) as BackupDecodeResult.Ok).file.images).containsExactly("buena.jpg")
        // La copia se puede importar: la referencia peligrosa se trata como una foto que falta.
        val b = env()
        assertThat(b.import(zip, ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 1, images = 1))
        assertThat(b.recipe("r1")!!.coverPhoto).isNull()
        assertThat(b.recipe("r1")!!.steps.first().photos).containsExactly("buena.jpg")
        // Y la copia previa de este teléfono (que la contiene) se puede restaurar, así que reemplazar no se bloquea.
        assertThat(a.import(backupZip(backupOf()), ImportMode.REPLACE_ALL)).isEqualTo(ImportResult.Ok(recipes = 0, images = 0))
        assertThat(java.io.File(a.filesDir, "fuera.jpg").exists()).isTrue()
    }

    @Test
    fun export_doesNotIncludeBackupStatusOrNotificationFlag() = runBlocking<Unit> {
        val a = env()
        a.settings.updateBackupStatus { it.copy(lastExternal = Instant.parse("2026-11-01T10:00:00Z"), driveUri = "content://drive/x") }
        a.settings.setNotificationPermissionAsked()

        val json = String(unzip(a.exportBytes()).getValue("backup.json"))

        assertThat(json).doesNotContain("content://drive/x")
        assertThat(json).doesNotContain("lastExternal")
        assertThat(json).doesNotContain("notification")
        assertThat(a.settings.backupStatus.first().lastExternal).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"))
    }

    @Test
    fun roundTrip_v2_identical() = runBlocking<Unit> {
        val a = env()
        a.writePhoto("portada.jpg", fakePhoto(1))
        a.saveAt(fullRecipe("r1", DefaultCategories.FOOD_ID, "portada.jpg", emptyList(), listOf("Cena")), Instant.parse("2026-11-01T10:00:00Z"))
        a.saveAt(fullRecipe("r2", DefaultCategories.DRINK_ID, null, emptyList(), emptyList()), Instant.parse("2026-11-02T10:00:00Z"))
        // Diario: con receta y fotos, sin valoración y sin receta (borrada), y una foto compartida con la receta.
        a.writePhoto("diario-1.jpg", fakePhoto(11))
        a.writePhoto("diario-2.jpg", fakePhoto(12, size = 40_000))
        a.diary.restore(
            CookLogEntry(
                "d1", "r1", "Receta r1", LocalDate.of(2026, 11, 10), 5, "Perfecta", listOf("diario-2.jpg", "diario-1.jpg", "portada.jpg"),
                Instant.parse("2026-11-10T20:00:00Z"), Instant.parse("2026-11-11T08:30:00.250Z"),
            ),
        )
        a.diary.restore(
            CookLogEntry(
                "d2", null, "Una que ya no está", LocalDate.of(2026, 9, 3), null, "", emptyList(),
                Instant.parse("2026-09-03T19:00:00Z"), Instant.parse("2026-09-03T19:00:00Z"),
            ),
        )
        a.pantry.add("Tomates")
        a.pantry.add("Aceite de oliva")
        a.db.mealPlanDao().upsert(
            listOf(
                MealPlanEntity("m1", "2026-11-16", "COMIDA", "r1", 4),
                MealPlanEntity("m2", "2026-11-16", "CENA", "r2", 2),
                MealPlanEntity("m3", "2026-11-17", "DESAYUNO", "r1", 1),
            ),
        )
        a.db.pairingRatingDao().upsert(
            listOf(
                PairingRatingEntity("CATALOG:vino|RECIPE:r1", "CATALOG", "vino", "RECIPE", "r1", true, Instant.parse("2026-11-05T10:00:00Z")),
                PairingRatingEntity("RECIPE:r1|RECIPE:r2", "RECIPE", "r1", "RECIPE", "r2", false, Instant.parse("2026-11-06T10:00:00Z")),
            ),
        )
        a.settings.setPantryBasics(listOf("sal", "agua", "caldo"))
        val before = a.snapshot()
        assertThat(before.diary).hasSize(2)
        assertThat(before.pantry).hasSize(2)
        assertThat(before.mealPlan).hasSize(3)
        assertThat(before.ratings).hasSize(2)
        val zip = a.exportBytes()
        val entries = unzip(zip)
        assertThat(entries.keys).containsExactly("backup.json", "images/portada.jpg", "images/diario-2.jpg", "images/diario-1.jpg")
        assertThat((decodeBackup(String(entries.getValue("backup.json"))) as BackupDecodeResult.Ok).file.images)
            .containsExactly("portada.jpg", "diario-2.jpg", "diario-1.jpg").inOrder()

        // Otro teléfono con datos del esquema 2 que deben desaparecer.
        val b = env()
        b.saveAt(fullRecipe("vieja", DefaultCategories.FOOD_ID, null, emptyList(), emptyList()), Instant.parse("2026-10-01T10:00:00Z"))
        b.writePhoto("diario-viejo.jpg", fakePhoto(9))
        b.diary.restore(
            CookLogEntry(
                "d-viejo", "vieja", "Vieja", LocalDate.of(2026, 10, 1), 2, "", listOf("diario-viejo.jpg"),
                Instant.parse("2026-10-01T10:00:00Z"), Instant.parse("2026-10-01T10:00:00Z"),
            ),
        )
        b.pantry.add("Tomate")
        b.pantry.add("Harina")
        b.db.mealPlanDao().upsert(listOf(MealPlanEntity("m-viejo", "2026-11-16", "COMIDA", "vieja", 1)))
        b.db.pairingRatingDao().upsert(
            listOf(PairingRatingEntity("CATALOG:vino|RECIPE:vieja", "CATALOG", "vino", "RECIPE", "vieja", true, Instant.parse("2026-10-02T10:00:00Z"))),
        )

        val result = b.import(zip, ImportMode.REPLACE_ALL)

        assertThat(result).isEqualTo(ImportResult.Ok(recipes = 2, images = 3, diaryEntries = 2, pantryItems = 2, meals = 3, ratings = 2))
        assertThat(b.snapshot()).isEqualTo(before)
        assertThat(b.cacheFiles()).isEmpty()

        // Y combinar la misma copia otra vez no cambia nada.
        assertThat(b.import(zip, ImportMode.MERGE)).isEqualTo(ImportResult.Ok(recipes = 0, images = 0))
        assertThat(b.snapshot()).isEqualTo(before)
    }
}
