package app.recetario.backup

import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
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
import app.recetario.shopping.ShopSection
import app.recetario.shopping.ShoppingItem
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class BackupModelTest {
    private val created = Instant.parse("2026-11-13T10:00:00Z")

    private val categories = listOf(
        Category("cat-comidas", "Platos", "🍲", "#C8553D", BaseType.FOOD, 0, true),
        Category("cat-salsas", "Salsas", "🥫", "#4A6FA5", BaseType.OTHER, 3, false),
        Category("cat-postres", "Postres", "🍮", "#B5733A", BaseType.DESSERT, 2, true),
    )

    private val recipe = Recipe(
        id = "r1", title = "Tarta de queso", categoryId = "cat-postres", coverPhoto = "portada.jpg",
        description = "Cremosa", totalMinutes = 75, servings = 8, difficulty = Difficulty.HARD,
        flavor = FlavorProfile(sweet = 3, salty = 1, sour = 2, bitter = 0, spicy = 0, intensity = Intensity.INTENSE, rich = true),
        serveTemp = ServeTemp.COLD, moments = setOf(Moment.SNACK, Moment.DINNER), alcoholic = false, course = Course.SIDE,
        ingredients = listOf(
            Ingredient("i1", "Base", "Galletas", 200.0, MeasureUnit.GRAM, null, "trituradas"),
            Ingredient("i2", null, "Queso crema", 1.5, MeasureUnit.CUSTOM, "tarrinas", ""),
            Ingredient("i3", null, "Sal", null, MeasureUnit.PINCH, null, ""),
        ),
        steps = listOf(
            Step("s1", "Tritura las galletas.", listOf("paso-1a.jpg", "paso-1b.jpg"), null, setOf("i1")),
            Step("s2", "Hornea 50 minutos.", emptyList(), 3000, setOf("i2", "i3")),
        ),
        tags = listOf("Cumpleaños", "Horno"), notes = "Mejor de un día para otro.", favorite = true,
        sourceUrl = "https://example.com/tarta", aiGenerated = true,
        createdAt = created, updatedAt = Instant.parse("2026-11-13T11:30:15.123Z"),
    )

    private val orphan = recipe.copy(
        id = "r2", title = "Sin categoría", categoryId = "cat-borrada", coverPhoto = null, ingredients = emptyList(),
        steps = emptyList(), tags = emptyList(), moments = emptySet(), serveTemp = null, alcoholic = null, course = null,
        sourceUrl = null, totalMinutes = null,
    )

    private val shopping = listOf(
        ShoppingItem("c1", "Leche", 1.0, MeasureUnit.LITER, null, ShopSection.DAIRY, true, "r1", created),
        ShoppingItem("c2", "Pan", null, MeasureUnit.CUSTOM, "barra", ShopSection.OTHER, false, null, created.plusMillis(1)),
    )

    private val theme = ThemeSettings(
        auto = false, choice = ThemeChoice.NOCTURNO, dayTheme = ThemeChoice.FRESCO,
        nightStart = LocalTime.of(21, 30), nightEnd = LocalTime.of(6, 45),
    )

    private fun backup() = backupFileOf(
        appVersion = "0.1.0", createdAt = created, categories = categories, recipes = listOf(recipe, orphan),
        shoppingItems = shopping, themeSettings = theme, cupMl = 250,
    )

    @Test
    fun json_roundTrip_restoresTheSameData() {
        val decoded = decodeBackup(encodeBackup(backup()))
        assertThat(decoded).isEqualTo(BackupDecodeResult.Ok(backup()))

        val data = (decoded as BackupDecodeResult.Ok).file.toImportData()
        assertThat(data.categories).isEqualTo(categories)
        assertThat(data.recipes.map { it.recipe }).containsExactly(recipe, orphan).inOrder()
        assertThat(data.shoppingItems).isEqualTo(shopping)
        assertThat(data.themeSettings).isEqualTo(theme)
        assertThat(data.cupMl).isEqualTo(250)
    }

    @Test
    fun json_hasSchemaVersionAndIsoDates() {
        val text = encodeBackup(backup())
        assertThat(text).contains("\"schemaVersion\":1")
        assertThat(text).contains("\"createdAt\":\"2026-11-13T10:00:00Z\"")
        assertThat(text).contains("\"updatedAt\":\"2026-11-13T11:30:15.123Z\"")
        assertThat(text).contains("\"nightStart\":\"21:30\"")
        assertThat(backup().schemaVersion).isEqualTo(BACKUP_SCHEMA_VERSION)
        assertThat(BACKUP_SCHEMA_VERSION).isEqualTo(1)
    }

    @Test
    fun recipes_carryTheirCategoryBaseType_andAnUnknownCategoryHasNone() {
        val data = backup().toImportData()
        assertThat(data.recipes.first { it.recipe.id == "r1" }.categoryBaseType).isEqualTo(BaseType.DESSERT)
        assertThat(data.recipes.first { it.recipe.id == "r2" }.categoryBaseType).isNull()
    }

    @Test
    fun photoNames_areCoverAndStepPhotos() {
        assertThat(backup().toImportData().photoNames)
            .containsExactly("portada.jpg", "paso-1a.jpg", "paso-1b.jpg")
    }

    @Test
    fun decode_newerSchema_isReportedEvenIfTheRestIsUnknown() {
        assertThat(decodeBackup("""{"schemaVersion":2,"recetas":"otro formato"}"""))
            .isEqualTo(BackupDecodeResult.NewerSchema)
    }

    @Test
    fun decode_garbage_isInvalid() {
        for (text in listOf("", "no es json", "[]", "{}", """{"schemaVersion":1}""", """{"schemaVersion":0}""", """{"schemaVersion":"uno"}""")) {
            assertThat(decodeBackup(text)).isEqualTo(BackupDecodeResult.Invalid)
        }
    }

    @Test
    fun decode_ignoresUnknownKeys() {
        val text = encodeBackup(backup()).replaceFirst("{", """{"campoNuevo":{"a":1},""")
        assertThat(decodeBackup(text)).isEqualTo(BackupDecodeResult.Ok(backup()))
    }

    @Test
    fun toImportData_rejectsBadDatesAndTimes() {
        val badRecipe = backup().let { it.copy(recipes = it.recipes.map { r -> r.copy(updatedAt = "ayer") }) }
        assertThrows(InvalidBackupException::class.java) { badRecipe.toImportData() }
        val badTime = backup().let { it.copy(settings = it.settings.copy(themeSettings = it.settings.themeSettings.copy(nightEnd = "25:00"))) }
        assertThrows(InvalidBackupException::class.java) { badTime.toImportData() }
        val badShopping = backup().let { it.copy(shoppingItems = it.shoppingItems.map { s -> s.copy(createdAt = "") }) }
        assertThrows(InvalidBackupException::class.java) { badShopping.toImportData() }
    }

    @Test
    fun toImportData_rejectsUnsafePhotoNames() {
        for (name in listOf("../recetario.db", "fotos/a.jpg", "a\\b.jpg", "..", "", "/a.jpg")) {
            val cover = backup().let { it.copy(recipes = it.recipes.map { r -> r.copy(coverPhoto = name) }) }
            assertThrows(InvalidBackupException::class.java) { cover.toImportData() }
            val step = backup().let { b ->
                b.copy(recipes = b.recipes.map { r -> r.copy(steps = r.steps.map { s -> s.copy(photos = listOf(name)) }) })
            }
            assertThrows(InvalidBackupException::class.java) { step.toImportData() }
        }
    }

    @Test
    fun toImportData_rejectsRepeatedOrBlankIds() {
        val b = backup()
        val repeatedRecipe = b.copy(recipes = b.recipes + b.recipes.first().copy(ingredients = emptyList(), steps = emptyList()))
        assertThrows(InvalidBackupException::class.java) { repeatedRecipe.toImportData() }
        val repeatedIngredient = b.copy(
            recipes = b.recipes.map { r -> if (r.id == "r2") r.copy(ingredients = b.recipes.first().ingredients.take(1)) else r },
        )
        assertThrows(InvalidBackupException::class.java) { repeatedIngredient.toImportData() }
        val repeatedStep = b.copy(
            recipes = b.recipes.map { r -> if (r.id == "r2") r.copy(steps = b.recipes.first().steps.take(1).map { it.copy(photos = emptyList()) }) else r },
        )
        assertThrows(InvalidBackupException::class.java) { repeatedStep.toImportData() }
        assertThrows(InvalidBackupException::class.java) { b.copy(categories = b.categories + b.categories.first()).toImportData() }
        assertThrows(InvalidBackupException::class.java) { b.copy(shoppingItems = b.shoppingItems + b.shoppingItems.first()).toImportData() }
        assertThrows(InvalidBackupException::class.java) { b.copy(recipes = b.recipes.map { it.copy(id = " ") }).toImportData() }
        assertThrows(InvalidBackupException::class.java) { b.copy(categories = b.categories.map { it.copy(id = "") }).toImportData() }
    }

    @Test
    fun toImportData_keepsValuesTheAppCanShow() {
        val b = backup().let { it.copy(recipes = it.recipes.map { r -> r.copy(servings = 0, flavor = r.flavor.copy(sweet = 9, salty = -2)) }) }
        val r = b.toImportData().recipes.first().recipe
        assertThat(r.servings).isEqualTo(1)
        assertThat(r.flavor.sweet).isEqualTo(3)
        assertThat(r.flavor.salty).isEqualTo(0)
    }

    @Test
    fun classifyEntry_acceptsOnlyTheJsonAndFlatImages() {
        assertThat(classifyBackupEntry("backup.json")).isEqualTo(BackupEntry.Json)
        assertThat(classifyBackupEntry("images/1f0c.jpg")).isEqualTo(BackupEntry.Image("1f0c.jpg"))
        assertThat(classifyBackupEntry("images/")).isEqualTo(BackupEntry.Folder)
        assertThat(classifyBackupEntry("LEEME.txt")).isEqualTo(BackupEntry.Other)
        for (name in listOf(
            "../backup.json", "/backup.json", "images/../../recetario.db", "images/..", "images/a/b.jpg",
            "images\\a.jpg", "a/b", "__MACOSX/._backup.json", "images/a..jpg", "..", "", "images//a.jpg",
        )) {
            assertThat(classifyBackupEntry(name)).isEqualTo(BackupEntry.Unsafe)
        }
    }

    @Test
    fun safeFileName_isAPlainName() {
        assertThat(isSafeBackupFileName("6b1e0f3c-1d2e-4c55-9a7b-0f1e2d3c4b5a.jpg")).isTrue()
        for (name in listOf("", ".", "..", "a/b", "a\\b", "a..b", "a\u0000b", "x".repeat(256))) {
            assertThat(isSafeBackupFileName(name)).isFalse()
        }
    }
}
