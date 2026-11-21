package app.recetario.pairing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Menú completo: entrada, principal, postre y bebida, con su porqué; sin principal, el estado vacío (§7.5). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class FullMenuScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
        seedPairingRecipes(container)
    }

    @After
    fun tearDown() = container.database.close()

    @Test
    fun fullMenu_showsFourCourses_orEmptyState() {
        val data = pairingData(container)
        val menu = fullMenu(data.recipes, data.catalog, data.ratings, data.weights)!!
        assertThat(menu.main.id).isEqualTo("mole")
        assertThat(menu.drink!!.id).isEqualTo("limonada")
        assertThat(menu.dessert!!.id).isEqualTo("flan")
        // No hay entradas propias: sale una idea del catálogo.
        assertThat(menu.starter!!.kind).isEqualTo("CATALOG")

        var state by mutableStateOf<FullMenuState?>(FullMenuState(menu))
        var opened: String? = null
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) { FullMenuContent(state, onBack = {}, onRecipe = { opened = it }) }
        }
        rule.onNodeWithTag("full-menu-Entrada", useUnmergedTree = true).assert(hasAnyDescendant(hasText(menu.starter!!.name)))
        rule.onNodeWithTag("full-menu-Entrada", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Idea")))
        rule.onNodeWithTag("full-menu-Principal", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Mole poblano")))
        rule.onNodeWithTag("full-menu-Postre", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Flan")))
        rule.onNodeWithTag("full-menu-Bebida", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Limonada de la casa")))
        rule.onNodeWithTag("full-menu-Bebida", useUnmergedTree = true).assert(hasAnyDescendant(hasText("De tu recetario")))
        rule.onNodeWithTag("full-menu-reason").assert(hasText(menu.reason))
        // Una receta propia se abre; una idea del catálogo, no.
        rule.onNodeWithTag("full-menu-Principal").performClick()
        assertThat(opened).isEqualTo("mole")

        state = FullMenuState(null)
        rule.onNodeWithText(FULL_MENU_EMPTY_TEXT).assertExists()
        rule.onAllNodes(hasTestTag("full-menu-Principal")).assertCountEquals(0)
    }

    @Test
    fun fullMenu_route_withoutMain_showsEmptyState() {
        runBlockingDelete("mole")
        rule.setContent {
            RecetarioTheme(ThemeChoice.NOCTURNO) { FullMenuRoute(container, onBack = {}, onRecipe = {}) }
        }
        rule.waitFor(hasText(FULL_MENU_EMPTY_TEXT))
    }

    private fun runBlockingDelete(id: String) = kotlinx.coroutines.runBlocking { container.recipeRepository.delete(id) }
}
