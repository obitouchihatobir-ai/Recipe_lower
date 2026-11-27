package app.recetario.pairing

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.Pairing
import app.recetario.navigation.PairingHistory
import app.recetario.navigation.RecipeDetail
import app.recetario.recipes.detail.DetailRoute
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** El maridaje en el detalle: las 3 mejores parejas, «Le iría bien…» y una 👎 que oculta la pareja (§7.5). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DetailPairingTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private var more = 0
    private var compared: PairingItem? = null

    @Before
    fun setUp() {
        container = installTestContainer()
        seedPairingRecipes(container)
    }

    private fun launchDetail(id: String) = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) {
            DetailRoute(
                container, id, onBack = {}, onEdit = {}, onCooking = {}, onShare = {},
                onMorePairings = { more++ },
                onComparePairing = { compared = it },
            )
        }
    }

    private fun rowTag(item: PairingItem) = "pairing-row-${item.kind}:${item.id}"

    private fun inRow(tag: String) = hasAnyAncestor(hasTestTag(tag))

    @Test
    fun detail_showsTop3WithReasons() {
        val data = pairingData(container)
        val mole = data.find("RECIPE", "mole")!!
        val expected = suggestPairings(mole, data.all, data.ratings, 3, data.weights)
        assertThat(expected).hasSize(3)
        // Primero las recetas propias: la limonada de la casa.
        assertThat(expected.first().item.id).isEqualTo("limonada")

        launchDetail("mole")
        rule.scrollUntil("detail-list", hasTestTag("detail-pairing"))
        rule.onNodeWithTag("detail-pairing", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Maridaje")))
        rule.onAllNodes(hasTestTagPrefix("pairing-row-")).assertCountEquals(3)
        for (s in expected) {
            val tag = rowTag(s.item)
            rule.scrollUntil("detail-list", hasTestTag(tag))
            rule.onNodeWithTag(tag, useUnmergedTree = true).assert(hasAnyDescendant(hasText(s.item.name)))
            rule.onNodeWithTag(tag, useUnmergedTree = true).assert(hasAnyDescendant(hasText(s.reasons.first(), substring = true)))
            // Receta con receta o receta con catálogo: se puede valorar, con botones de 48 dp.
            assertThat(s.ratable).isTrue()
            rule.onNode(hasContentDescription(LIKE_LABEL) and inRow(tag))
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsNotSelected()
            rule.onNode(hasContentDescription(DISLIKE_LABEL) and inRow(tag))
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }

        // Tocar una pareja abre la rueda comparativa; «Ver más», la pestaña.
        val first = rowTag(expected.first().item)
        rule.scrollUntil("detail-list", hasTestTag(first))
        rule.onNode(hasClickAction() and inRow(first) and hasText(expected.first().item.name, substring = true)).performClick()
        assertThat(compared?.id).isEqualTo("limonada")
        rule.scrollUntil("detail-list", hasTestTag("detail-pairing-more"))
        rule.onNodeWithTag("detail-pairing-more").performClick()
        assertThat(more).isEqualTo(1)

        // 👍 queda marcado y suma la pareja a las valoraciones.
        rule.onNode(hasContentDescription(LIKE_LABEL) and inRow(first)).performClick()
        rule.waitUntil(5_000) { ratings(container)[pairKey(mole, expected.first().item)] == true }
        rule.waitFor(hasContentDescription(LIKE_LABEL) and inRow(first) and androidx.compose.ui.test.isSelected())
    }

    @Test
    fun detail_goesWellWith_chips() {
        val expected = FlavorGuide.goesWellWith(MOLE_INGREDIENTS)
        assertThat(expected).isNotEmpty()
        assertThat(expected.size).isAtMost(6)

        launchDetail("mole")
        rule.scrollUntil("detail-list", hasTestTag("detail-goes-well"))
        rule.onNodeWithTag("detail-goes-well", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Le iría bien…")))
        for (flavor in expected) {
            rule.onNode(hasText(flavor) and hasAnyAncestor(hasTestTag("detail-goes-well"))).assertExists()
        }
    }

    @Test
    fun detail_goesWellWith_hiddenWithoutIngredients() {
        // El flan no tiene ingredientes: no hay nada que sugerir y el bloque no sale (el maridaje sí).
        launchDetail("flan")
        rule.scrollUntil("detail-list", hasTestTag("detail-pairing"))
        rule.onAllNodes(hasTestTag("detail-goes-well")).assertCountEquals(0)
    }

    // ------------------------------------------------------------------ 👎 en los dos sentidos

    private lateinit var nav: NavHostController

    @Test
    fun dislike_hidesBothWays_untilChanged() {
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav) }
        }
        rule.runOnUiThread { nav.navigate(RecipeDetail("mole")) }
        val limonadaRow = "pairing-row-RECIPE:limonada"
        rule.scrollUntil("detail-list", hasTestTag(limonadaRow))

        // 👎 en el detalle del mole: la limonada desaparece.
        rule.onNode(hasContentDescription(DISLIKE_LABEL) and inRow(limonadaRow)).performClick()
        rule.waitForGone(hasTestTag(limonadaRow))
        // La valoración se escribe en applicationScope: la fila puede irse antes de que la lectura la vea.
        rule.waitUntil(5_000) { ratings(container) == mapOf("RECIPE:limonada|RECIPE:mole" to false) }

        // Al revés: partiendo de la limonada, el mole no sale (el flan sí).
        rule.runOnUiThread { nav.navigate(Pairing(subject = "RECIPE:limonada")) }
        rule.waitFor(hasTestTag("pairing-subject"))
        rule.scrollUntil("pairing-list", hasTestTag("pairing-row-RECIPE:flan"))
        // No basta con lo compuesto: recorriendo toda la lista, el mole no aparece.
        assertThrows(AssertionError::class.java) {
            rule.onNodeWithTag("pairing-list").performScrollToNode(hasTestTag("pairing-row-RECIPE:mole"))
        }

        // En el historial se cambia a 👍…
        rule.runOnUiThread { nav.navigate(PairingHistory) }
        val historyRow = "history-RECIPE:limonada|RECIPE:mole"
        rule.waitFor(hasTestTag(historyRow))
        rule.onNode(hasContentDescription(DISLIKE_LABEL) and inRow(historyRow)).assertIsSelected()
        rule.onNode(hasContentDescription(LIKE_LABEL) and inRow(historyRow)).performClick()
        rule.waitUntil(5_000) { ratings(container)["RECIPE:limonada|RECIPE:mole"] == true }

        // …y la pareja vuelve en los dos sentidos.
        rule.runOnUiThread { nav.navigate(RecipeDetail("mole")) }
        rule.scrollUntil("detail-list", hasTestTag(limonadaRow))
        rule.onNode(hasContentDescription(LIKE_LABEL) and inRow(limonadaRow)).assertIsSelected()
        rule.runOnUiThread { nav.navigate(Pairing(subject = "RECIPE:limonada")) }
        rule.waitFor(hasTestTag("pairing-subject"))
        rule.scrollUntil("pairing-list", hasTestTag("pairing-row-RECIPE:mole"))
    }
}
