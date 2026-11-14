package app.recetario.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CategoriesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    private fun launch() = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) { CategoriesRoute(container, onBack = {}) }
    }

    private fun recipeCategory(id: String) =
        runBlocking { container.recipeRepository.observeRecipe(id).first()?.categoryId }

    @Test
    fun categories_deleteWithRecipes_asksReassign() {
        runBlocking {
            container.recipeRepository.saveCategory(Category("cat-cenas", "Cenas", "🌙", "#4A6FA5", BaseType.DESSERT, 3, false))
            container.recipeRepository.save(sampleRecipe("flan", "Flan", categoryId = "cat-cenas"))
        }
        launch()
        rule.waitFor(hasText("Cenas"))
        // Las de serie no se borran.
        rule.onNodeWithTag("category-delete-${DefaultCategories.FOOD_ID}").assertDoesNotExist()

        rule.onNodeWithTag("category-delete-cat-cenas").performClick()
        rule.waitFor(hasText("¿A qué categoría pasan sus recetas?"))
        // Por defecto, la de serie con el mismo tipo base (Postre).
        rule.onNodeWithTag("reassign-${DefaultCategories.DESSERT_ID}").assertIsSelected()
        rule.onNodeWithTag("reassign-${DefaultCategories.DRINK_ID}").performClick()
        rule.onNodeWithTag("reassign-${DefaultCategories.DRINK_ID}").assertIsSelected()
        rule.onNodeWithTag("category-delete-confirm").performClick()

        rule.waitUntil(5_000) { recipeCategory("flan") == DefaultCategories.DRINK_ID }
        rule.waitUntil(5_000) {
            runBlocking { container.recipeRepository.observeCategories().first() }.none { it.id == "cat-cenas" }
        }
        assertEquals(DefaultCategories.DRINK_ID, recipeCategory("flan"))
    }

    @Test
    fun categories_create_savesNewCategory() {
        launch()
        rule.waitFor(hasText("Comidas"))
        rule.onNodeWithTag("category-add").performClick()
        rule.waitFor(hasTestTag("category-name"))
        rule.onNodeWithTag("category-save").assertIsNotEnabled()
        rule.onNodeWithTag("category-name").performTextInput("Cenas")
        rule.onNodeWithTag("category-color-4A6FA5").performClick()
        rule.onNodeWithTag("category-base-DESSERT").performClick()
        rule.onNodeWithTag("category-save").assertIsEnabled().performClick()
        rule.waitUntil(5_000) {
            runBlocking { container.recipeRepository.observeCategories().first() }.any { it.name == "Cenas" }
        }
        val saved = runBlocking { container.recipeRepository.observeCategories().first() }.first { it.name == "Cenas" }
        assertEquals("#4A6FA5", saved.colorHex)
        assertEquals(BaseType.DESSERT, saved.baseType)
        assertEquals("🍽️", saved.emoji)
        assertEquals(false, saved.isDefault)
        rule.waitFor(hasText("Cenas"))
    }

    @Test
    fun categories_saveFailure_showsASnackbarInsteadOfCrashing() {
        val failing = object : RecipeRepository by container.recipeRepository {
            override suspend fun saveCategory(category: Category) = throw IOException("disco lleno")
        }
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { CategoriesRoute(container, onBack = {}, repo = failing) } }
        rule.waitFor(hasText("Comidas"))
        rule.onNodeWithTag("category-add").performClick()
        rule.waitFor(hasTestTag("category-name"))
        rule.onNodeWithTag("category-name").performTextInput("Cenas")
        rule.onNodeWithTag("category-save").performClick()
        rule.waitFor(hasText("No se pudo guardar la categoría."))
    }
}
