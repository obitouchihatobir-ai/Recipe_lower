package app.recetario.recipes.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Recipe
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class RecipesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    private val limonada = "Limonada con hierbabuena"
    private val bloody = "Bloody Mary"
    private val crema = "Crema de calabaza"

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    private fun openRecipes(vararg recipes: Recipe) {
        runBlocking { recipes.forEach { container.recipeRepository.save(it) } }
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.onNodeWithTag("recipes-search").assertIsDisplayed()
    }

    private fun seedThree() = openRecipes(
        sampleRecipe("limonada", limonada, DefaultCategories.DRINK_ID, minutes = 10, flavor = FlavorProfile(sweet = 1, sour = 3)),
        sampleRecipe("bloody", bloody, DefaultCategories.DRINK_ID, minutes = 5, flavor = FlavorProfile(salty = 2, sour = 1)),
        sampleRecipe("crema", crema, DefaultCategories.FOOD_ID, minutes = 35, flavor = FlavorProfile(sweet = 2, salty = 2)),
    )

    @Test
    fun search_limon_showsOnlyLimonada() {
        seedThree()
        rule.waitFor(hasText(bloody))
        rule.onNodeWithText(limonada).assertIsDisplayed()
        rule.onNodeWithText(crema).assertIsDisplayed()

        rule.onNodeWithTag("recipes-search").performTextInput("limon")

        rule.waitForGone(hasText(bloody))
        rule.onNodeWithText(limonada).assertIsDisplayed()
        rule.onNodeWithText(crema).assertDoesNotExist()
    }

    @Test
    fun categoryChip_filters() {
        seedThree()
        rule.waitFor(hasText(crema))

        rule.onNodeWithText("Bebidas").performClick()

        rule.waitForGone(hasText(crema))
        rule.onNodeWithText(limonada).assertIsDisplayed()
        rule.onNodeWithText(bloody).assertIsDisplayed()

        rule.onNodeWithText("Todas").performClick()
        rule.waitFor(hasText(crema))
    }

    @Test
    fun longTitle_isEllipsizedInCard() {
        val long = "Tarta de queso al horno con base de galleta, frutos rojos del bosque y un toque de limón, receta de la abuela Rosa"
            .padEnd(120, '.')
        assertThat(long).hasLength(120)
        openRecipes(sampleRecipe("larga", long, DefaultCategories.DESSERT_ID))
        rule.waitFor(hasText(long))

        val title = rule.onNodeWithText(long, useUnmergedTree = true)
        title.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        title.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertThat(layouts.single().lineCount).isAtMost(2)
        assertThat(layouts.single().hasVisualOverflow).isTrue()
    }
}
