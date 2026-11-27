package app.recetario.season

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.Season
import app.recetario.recipes.model.MeasureUnit
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** «De temporada» (diseño de la etapa 2, §7.10). El reloj de prueba es el 13 de noviembre de 2026. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SeasonScreenTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    private fun recipe(id: String, title: String, vararg ingredients: String) = runBlocking {
        container.recipeRepository.save(
            sampleRecipe(
                id, title,
                ingredients = ingredients.mapIndexed { i, name -> ingredient("$id-$i", name, 1.0, MeasureUnit.PIECE) },
            ),
        )
    }

    private fun launch() {
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav) }
        }
        rule.waitFor(hasTestTag("home-diary"))
        rule.runOnIdle { nav.navigate(Season) }
        rule.waitFor(hasTestTag("season-screen"))
    }

    private fun scrollTo(tag: String) {
        rule.waitFor(hasTestTag(tag))
        rule.onNodeWithTag("season-list").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun season_currentMonthSelected() {
        launch()
        // Los 12 meses se componen (la fila no es perezosa) y el actual, noviembre, va elegido.
        for (m in 1..12) rule.onNodeWithTag("season-month-$m", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("season-month-11").assert(isSelected())
        rule.onNodeWithTag("season-month-3").assert(isNotSelected())
        // Guayaba está en su mejor momento en noviembre; la fresa no es temporada.
        scrollTo("season-best-guayaba")
        rule.onNodeWithTag("season-best-title", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("season-kind-best-FRUIT", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("season-best-fresa", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("season-available-fresa", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun season_changeMonth() {
        launch()
        rule.onNodeWithTag("season-month-3").performClick()
        rule.waitFor(hasTestTag("season-month-3") and isSelected())
        rule.onNodeWithTag("season-month-11").assert(isNotSelected())
        // En marzo la fresa está en su mejor momento y la guayaba pasa a «También hay».
        scrollTo("season-best-fresa")
        rule.waitForGone(hasTestTag("season-best-guayaba"))
        scrollTo("season-available-title")
        scrollTo("season-available-guayaba")
        // Sigue al girar: el mes va en el SavedStateHandle (aquí, al volver a componer la lista).
        rule.onNodeWithTag("season-month-3").assert(isSelected())
    }

    @Test
    fun season_recipeCounts() {
        recipe("agua", "Agua de guayaba", "guayabas", "azúcar", "agua")
        recipe("ponche", "Ponche navideño", "guayaba", "tejocotes", "caña")
        recipe("crema", "Crema de calabaza", "calabaza", "leche")
        launch()
        scrollTo("season-best-guayaba")
        // TalkBack lee el nombre del producto (no el del emoji) y sus recetas.
        rule.waitFor(hasTestTag("season-best-guayaba") and hasContentDescription("Guayaba, 2 recetas"))
        scrollTo("season-best-tejocote")
        rule.onNodeWithTag("season-best-tejocote").assert(hasContentDescription("Tejocote, 1 receta"))
        scrollTo("season-best-mandarina")
        rule.onNodeWithTag("season-best-mandarina").assert(hasContentDescription("Mandarina, sin recetas"))
        // Y lo dice a la vista.
        rule.onNodeWithTag("season-best-guayaba", useUnmergedTree = true).assertExists()
        rule.waitFor(hasText("2 recetas"))
    }

    @Test
    fun season_tapProduce_opensRecipesSearch() {
        recipe("agua", "Agua de guayaba", "guayabas", "azúcar")
        recipe("crema", "Crema de calabaza", "calabaza", "leche")
        launch()
        scrollTo("season-best-guayaba")
        rule.waitFor(hasTestTag("season-best-guayaba") and hasContentDescription("Guayaba, 1 receta"))
        rule.onNodeWithTag("season-best-guayaba").performClick()

        // Recetas, con el nombre del producto en el buscador y solo lo que lo lleva.
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitFor(hasTestTag("recipes-search") and hasText("Guayaba"))
        rule.waitFor(hasText("Agua de guayaba"))
        rule.waitForGone(hasText("Crema de calabaza"))
        rule.onNodeWithTag("season-screen").assertDoesNotExist()
        assertThrows(AssertionError::class.java) { rule.onNodeWithTag("season-list").assertExists() }
    }
}
