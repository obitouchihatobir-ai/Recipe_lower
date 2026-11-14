package app.recetario.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
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
        rule.onNodeWithTag("category-name").assertIsDisplayed()
    }
}
