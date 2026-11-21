package app.recetario.pairing

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.RecipeDetail
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Pestaña Maridaje: «¿Con qué combina…?», «Tus recetas» e «Ideas», «Menú completo» e «Historial» (§7.5). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class PairingScreenTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        container = installTestContainer()
        seedPairingRecipes(container)
    }

    private fun launch() = rule.setContent {
        nav = rememberNavController()
        RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav) }
    }

    @Test
    fun pairingTab_searchDrink_showsRecipesAndIdeas() {
        val data = pairingData(container)
        val jamaica = data.find("CATALOG", "cat-agua-jamaica")!!
        val all = suggestPairings(jamaica, data.all, data.ratings, PAIRING_TAB_LIMIT, data.weights)
        val firstIdea = all.first { it.item.kind == "CATALOG" }
        assertThat(all.filter { it.item.kind == "RECIPE" }.map { it.item.id }).containsExactly("mole", "flan")

        launch()
        rule.onNodeWithTag("tab-pairing").performClick()
        rule.waitFor(hasText("¿Con qué combina…?"))
        rule.onNodeWithTag("pairing-search").performTextInput("jamaica")
        rule.waitFor(hasTestTag("pairing-match-CATALOG:cat-agua-jamaica"))
        rule.onNodeWithTag("pairing-match-CATALOG:cat-agua-jamaica").performClick()

        rule.waitFor(hasTestTag("pairing-subject"))
        rule.onNodeWithTag("pairing-subject", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Agua de jamaica")))
        rule.scrollUntil("pairing-list", hasText("Tus recetas"))
        rule.scrollUntil("pairing-list", hasTestTag("pairing-row-RECIPE:mole"))
        rule.scrollUntil("pairing-list", hasTestTag("pairing-row-RECIPE:flan"))
        // Receta con bebida del catálogo: se valora.
        rule.onNode(hasContentDescription(LIKE_LABEL) and hasAnyAncestor(hasTestTag("pairing-row-RECIPE:mole"))).assertExists()

        rule.scrollUntil("pairing-list", hasText("Ideas"))
        val ideaTag = "pairing-row-CATALOG:${firstIdea.item.id}"
        rule.scrollUntil("pairing-list", hasTestTag(ideaTag))
        rule.onNodeWithTag(ideaTag, useUnmergedTree = true).assert(hasAnyDescendant(hasText(firstIdea.reasons.first(), substring = true)))
        // Catálogo con catálogo: sin 👍/👎.
        rule.onAllNodes(hasContentDescription(LIKE_LABEL) and hasAnyAncestor(hasTestTag(ideaTag))).assertCountEquals(0)
        rule.onAllNodes(hasContentDescription(DISLIKE_LABEL) and hasAnyAncestor(hasTestTag(ideaTag))).assertCountEquals(0)

        // Tocar un resultado abre la rueda comparativa.
        rule.scrollUntil("pairing-list", hasTestTag("pairing-row-RECIPE:mole"))
        rule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("pairing-row-RECIPE:mole")) and hasText("Mole poblano", substring = true))
            .performClick()
        rule.waitFor(hasTestTag("compare-wheel"))
        rule.onNodeWithTag("compare-wheel").assert(hasContentDescription("Agua de jamaica", substring = true))
    }

    @Test
    fun pairingTab_searchFindsOwnRecipesAndOnlyCatalogDrinks() {
        launch()
        rule.onNodeWithTag("tab-pairing").performClick()
        rule.waitFor(hasTestTag("pairing-search"))
        // «Flan» es una receta propia; en el catálogo hay un postre con «flan»… que no sale: solo bebidas del catálogo.
        rule.onNodeWithTag("pairing-search").performTextInput("fla")
        rule.waitFor(hasTestTag("pairing-match-RECIPE:flan"))
        rule.onAllNodes(hasTestTag("pairing-match-CATALOG:cat-flan")).assertCountEquals(0)
        val data = pairingData(container)
        assertThat(data.find("CATALOG", "cat-flan")!!.role).isEqualTo(PairRole.DESSERT)
        for (item in pairingMatches("fla", data)) assertThat(item.kind == "RECIPE" || item.role == PairRole.DRINK).isTrue()
    }

    @Test
    fun pairingTab_fullMenuAndHistory() {
        launch()
        rule.onNodeWithTag("tab-pairing").performClick()
        rule.waitFor(hasTestTag("pairing-full-menu"))
        rule.onNodeWithTag("pairing-full-menu").performClick()
        rule.waitFor(hasTestTag("full-menu-Principal"))
        rule.onNodeWithTag("full-menu-Principal", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Mole poblano")))
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitFor(hasTestTag("pairing-history"))
        rule.onNodeWithTag("pairing-history").performClick()
        rule.waitFor(hasText(HISTORY_EMPTY_TEXT))
    }

    @Test
    fun detail_seeMore_opensTheTabWithTheRecipe() {
        launch()
        rule.runOnUiThread { nav.navigate(RecipeDetail("mole")) }
        rule.scrollUntil("detail-list", hasTestTag("detail-pairing-more"))
        rule.onNodeWithTag("detail-pairing-more").performClick()
        rule.waitFor(hasTestTag("pairing-subject"))
        rule.onNodeWithTag("pairing-subject", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Mole poblano")))
        rule.onNodeWithTag("tab-pairing").assertIsSelected()
        // Para un plato, «Tus recetas» trae la limonada propia.
        rule.scrollUntil("pairing-list", hasTestTag("pairing-row-RECIPE:limonada"))
        rule.onNodeWithText("Tus recetas").assertExists()
    }
}
