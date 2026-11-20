package app.recetario.backup

import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.diary.CookLogEntry
import app.recetario.pantry.PantryItem
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
import java.time.LocalDate
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
        assertThat(text).contains("\"schemaVersion\":2")
        assertThat(text).contains("\"createdAt\":\"2026-11-13T10:00:00Z\"")
        assertThat(text).contains("\"updatedAt\":\"2026-11-13T11:30:15.123Z\"")
        assertThat(text).contains("\"nightStart\":\"21:30\"")
        assertThat(backup().schemaVersion).isEqualTo(BACKUP_SCHEMA_VERSION)
        assertThat(BACKUP_SCHEMA_VERSION).isEqualTo(2)
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
        assertThat(decodeBackup("""{"schemaVersion":3,"recetas":"otro formato"}"""))
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
    fun toImportData_dropsUnsafePhotoReferences_likeAMissingPhoto() {
        // Nunca pueden ser fotos nuestras y, guardadas tal cual, podrían borrar otro archivo de la app.
        for (name in listOf("../recetario.db", "fotos/a.jpg", "a\\b.jpg", "..", "", "/a.jpg")) {
            val b = backup().let { f ->
                f.copy(recipes = f.recipes.map { r -> r.copy(coverPhoto = name, steps = r.steps.map { s -> s.copy(photos = listOf(name, "buena.jpg")) }) })
            }
            val recipes = b.toImportData().recipes.map { it.recipe }
            assertThat(recipes.map { it.coverPhoto }).containsExactly(null, null)
            assertThat(recipes.first().steps.map { it.photos }).containsExactly(listOf("buena.jpg"), listOf("buena.jpg"))
        }
    }

    @Test
    fun imagesManifest_roundTrips_andIsEmptyInOlderCopies() {
        val withImages = backupFileOf(
            appVersion = "0.1.0", createdAt = created, categories = categories, recipes = listOf(recipe),
            shoppingItems = emptyList(), themeSettings = theme, cupMl = 240, images = listOf("portada.jpg", "paso-1a.jpg"),
        )
        assertThat(withImages.images).containsExactly("portada.jpg", "paso-1a.jpg").inOrder()
        assertThat(decodeBackup(encodeBackup(withImages))).isEqualTo(BackupDecodeResult.Ok(withImages))
        assertThat(backup().images).isEmpty()
        val older = encodeBackup(backup()).replace(",\"images\":[]", "")
        assertThat(older).doesNotContain("\"images\"")
        assertThat((decodeBackup(older) as BackupDecodeResult.Ok).file.images).isEmpty()
    }

    @Test
    fun toImportData_rejectsUnsafeNamesInTheImagesManifest() {
        for (name in listOf("../recetario.db", "a/b.jpg", "")) {
            assertThrows(InvalidBackupException::class.java) { backup().copy(images = listOf(name)).toImportData() }
        }
    }

    @Test
    fun limits() {
        assertThat(BACKUP_MAX_JSON_BYTES).isEqualTo(16 * 1024 * 1024)
        assertThat(BACKUP_MAX_IMAGE_BYTES).isEqualTo(25 * 1024 * 1024)
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

    // --- Esquema 2: diario, despensa, menú y valoraciones ---

    private val diary = listOf(
        CookLogEntry(
            id = "d1", recipeId = "r1", recipeTitle = "Tarta de queso", date = LocalDate.of(2026, 11, 12), rating = 4,
            note = "Quedó cremosa", photos = listOf("diario-1.jpg", "diario-2.jpg"), createdAt = created,
            updatedAt = Instant.parse("2026-11-13T12:00:00.5Z"),
        ),
        CookLogEntry(
            id = "d2", recipeId = null, recipeTitle = "Receta borrada", date = LocalDate.of(2026, 10, 1), rating = null,
            note = "", photos = emptyList(), createdAt = created, updatedAt = created,
        ),
    )

    private val pantry = listOf(
        PantryItem("p1", "Tomates", "tomates", created),
        PantryItem("p2", "Arroz", "arroz", created.plusSeconds(1)),
    )

    private val plan = listOf(
        PlannedMeal("m1", LocalDate.of(2026, 11, 16), "COMIDA", "r1", 4),
        PlannedMeal("m2", LocalDate.of(2026, 11, 16), "CENA", "r2", 2),
    )

    private val ratings = listOf(
        PairingRating("CATALOG:vino-tinto|RECIPE:r1", "CATALOG", "vino-tinto", "RECIPE", "r1", true, created),
        PairingRating("RECIPE:r1|RECIPE:r2", "RECIPE", "r1", "RECIPE", "r2", false, created.plusSeconds(60)),
    )

    private fun backupV2() = backupFileOf(
        appVersion = "0.2.0", createdAt = created, categories = categories, recipes = listOf(recipe, orphan),
        shoppingItems = shopping, themeSettings = theme, cupMl = 250, cookLog = diary, pantry = pantry,
        mealPlan = plan, pairingRatings = ratings, pantryBasics = listOf("sal", "aceite de oliva"),
    )

    private fun v1Fixture(): String =
        requireNotNull(javaClass.getResource("/backup/v1-backup.json")) { "Falta la copia v1 de prueba" }.readText()

    @Test
    fun decode_v1_hasEmptyNewLists() {
        // Una copia real de la 0.1.0 (esquema 1), escrita con el modelo de entonces.
        val text = v1Fixture()
        assertThat(text).contains("\"schemaVersion\": 1")
        assertThat(text).doesNotContain("cookLog")
        assertThat(text).doesNotContain("pantry")
        val file = (decodeBackup(text) as BackupDecodeResult.Ok).file
        assertThat(file.schemaVersion).isEqualTo(1)
        assertThat(file.cookLog).isEmpty()
        assertThat(file.pantry).isEmpty()
        assertThat(file.mealPlan).isEmpty()
        assertThat(file.pairingRatings).isEmpty()
        assertThat(file.settings.pantryBasics).isNull()

        val data = file.toImportData()
        assertThat(data.recipes.map { it.recipe.title }).containsExactly("Tortilla de patatas")
        assertThat(data.shoppingItems.map { it.name }).containsExactly("Leche")
        assertThat(data.cookLog).isEmpty()
        assertThat(data.pantry).isEmpty()
        assertThat(data.mealPlan).isEmpty()
        assertThat(data.pairingRatings).isEmpty()
        assertThat(data.pantryBasics).isNull()
        assertThat(data.photoNames).containsExactly("v1-portada.jpg", "v1-paso.jpg")
    }

    @Test
    fun decode_v3_isNewer() {
        assertThat(decodeBackup(encodeBackup(backupV2()).replace("\"schemaVersion\":2", "\"schemaVersion\":3")))
            .isEqualTo(BackupDecodeResult.NewerSchema)
        assertThat(decodeBackup("""{"schemaVersion":3}""")).isEqualTo(BackupDecodeResult.NewerSchema)
        // La 2 es la de ahora: se lee.
        assertThat(decodeBackup(encodeBackup(backupV2()))).isEqualTo(BackupDecodeResult.Ok(backupV2()))
    }

    @Test
    fun json_v2_roundTrip_restoresTheNewData() {
        val file = (decodeBackup(encodeBackup(backupV2())) as BackupDecodeResult.Ok).file
        assertThat(file.schemaVersion).isEqualTo(2)
        val data = file.toImportData()
        assertThat(data.cookLog).isEqualTo(diary)
        assertThat(data.pantry).isEqualTo(pantry)
        assertThat(data.mealPlan).isEqualTo(plan)
        assertThat(data.pairingRatings).isEqualTo(ratings)
        assertThat(data.pantryBasics).containsExactly("sal", "aceite de oliva").inOrder()
        val text = encodeBackup(backupV2())
        assertThat(text).contains("\"date\":\"2026-11-12\"")
        assertThat(text).contains("\"updatedAt\":\"2026-11-13T12:00:00.500Z\"")
    }

    @Test
    fun validate_badRating_rejected() {
        for (bad in listOf(0, 6, -1)) {
            val b = backupV2().let { it.copy(cookLog = it.cookLog.map { e -> e.copy(rating = bad) }) }
            assertThrows(InvalidBackupException::class.java) { b.toImportData() }
        }
        for (good in listOf(1, 5, null)) {
            val b = backupV2().let { it.copy(cookLog = it.cookLog.map { e -> e.copy(rating = good) }) }
            assertThat(b.toImportData().cookLog.map { it.rating }).containsExactly(good, good)
        }
    }

    @Test
    fun validate_badSlot_rejected() {
        for (bad in listOf("MERIENDA", "comida", "", "LUNCH")) {
            val b = backupV2().let { it.copy(mealPlan = it.mealPlan.map { m -> m.copy(slot = bad) }) }
            assertThrows(InvalidBackupException::class.java) { b.toImportData() }
        }
        // Dos platos en el mismo hueco no los puede tener la app.
        val twice = backupV2().let { it.copy(mealPlan = it.mealPlan.map { m -> m.copy(slot = "COMIDA") }) }
        assertThrows(InvalidBackupException::class.java) { twice.toImportData() }
    }

    @Test
    fun validate_badPairingKinds_rejected() {
        val badA = backupV2().let { it.copy(pairingRatings = it.pairingRatings.map { r -> r.copy(aKind = "VINO") }) }
        assertThrows(InvalidBackupException::class.java) { badA.toImportData() }
        val badB = backupV2().let { it.copy(pairingRatings = it.pairingRatings.map { r -> r.copy(bKind = "recipe") }) }
        assertThrows(InvalidBackupException::class.java) { badB.toImportData() }
    }

    @Test
    fun validate_newTables_badDatesAndIds_rejected() {
        val b = backupV2()
        val cases = listOf(
            b.copy(cookLog = b.cookLog.map { it.copy(date = "ayer") }),
            b.copy(cookLog = b.cookLog.map { it.copy(updatedAt = "") }),
            b.copy(cookLog = b.cookLog.map { it.copy(createdAt = "x") }),
            b.copy(cookLog = b.cookLog + b.cookLog.first()),
            b.copy(cookLog = b.cookLog.map { it.copy(id = " ") }),
            b.copy(pantry = b.pantry.map { it.copy(addedAt = "hoy") }),
            b.copy(pantry = b.pantry + b.pantry.first().copy(name = "Lentejas", normalized = "lentejas")),
            b.copy(mealPlan = b.mealPlan.map { it.copy(date = "2026-13-01") }),
            b.copy(mealPlan = b.mealPlan + b.mealPlan.first().copy(date = "2026-11-20")),
            b.copy(mealPlan = b.mealPlan.map { it.copy(recipeId = "") }),
            b.copy(pairingRatings = b.pairingRatings.map { it.copy(ratedAt = "nunca") }),
            b.copy(pairingRatings = b.pairingRatings + b.pairingRatings.first()),
            b.copy(pairingRatings = b.pairingRatings.map { it.copy(pairKey = "") }),
        )
        for (bad in cases) assertThrows(InvalidBackupException::class.java) { bad.toImportData() }
    }

    @Test
    fun validate_newTables_keepValuesTheAppCanShow() {
        val b = backupV2().let { f ->
            f.copy(
                cookLog = f.cookLog.map { it.copy(photos = listOf("../recetario.db", "a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg")) },
                mealPlan = f.mealPlan.map { it.copy(servings = 0) },
                // El mismo artículo dos veces (plural): queda uno.
                pantry = f.pantry + BackupPantryItem("p3", "tomate", "tomate", created.toString()),
            )
        }
        val data = b.toImportData()
        assertThat(data.cookLog.first().photos).containsExactly("a.jpg", "b.jpg", "c.jpg", "d.jpg").inOrder()
        assertThat(data.mealPlan.map { it.servings }).containsExactly(1, 1)
        assertThat(data.pantry.map { it.id }).containsExactly("p1", "p2")
        // `normalized` se recalcula con la clave de la app.
        val odd = backupV2().let { it.copy(pantry = listOf(BackupPantryItem("p9", "Ajo  Negro", "AJO!", created.toString()))) }
        assertThat(odd.toImportData().pantry.single().normalized).isEqualTo("ajo negro")
        // Un artículo sin letras ni números no lo puede crear la app: se descarta.
        val empty = backupV2().let { it.copy(pantry = listOf(BackupPantryItem("p9", "¡!", "", created.toString()))) }
        assertThat(empty.toImportData().pantry).isEmpty()
    }

    @Test
    fun photoNames_includeDiary() {
        assertThat(backupV2().toImportData().photoNames)
            .containsExactly("portada.jpg", "paso-1a.jpg", "paso-1b.jpg", "diario-1.jpg", "diario-2.jpg")
    }
}
