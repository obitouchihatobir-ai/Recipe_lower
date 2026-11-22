package app.recetario.shopping

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import kotlinx.coroutines.flow.first
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import kotlinx.coroutines.runBlocking
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ShoppingScreenTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    /** La cabecera de una sección de la lista (la pestaña «Despensa» se llama igual que la sección). */
    private fun section(name: String) = hasText(name).and(hasAnyAncestor(hasTestTag("shopping-list")))

    private fun launch() = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) { ShoppingRoute(container) }
    }

    @Test
    fun emptyList_showsTheHint() {
        launch()
        rule.waitFor(hasText("Tu lista está vacía. Desde una receta, pulsa «Lo que necesito»."))
    }

    @Test
    fun itemsAreGroupedUnderSectionHeaders() {
        runBlocking {
            container.shoppingRepository.addManual("Calabaza", 800.0, MeasureUnit.GRAM)
            container.shoppingRepository.addManual("Leche", 1.0, MeasureUnit.LITER)
        }
        launch()
        rule.waitFor(hasText("Calabaza"))
        rule.onNodeWithText("Frutas y verduras").assertIsDisplayed()
        rule.onNodeWithText("Lácteos").assertIsDisplayed()
        rule.onNodeWithText("800 g").assertIsDisplayed()
    }

    @Test
    fun checkingAnItem_movesItUnderComprado_andBackWhenUnchecked() {
        runBlocking {
            container.shoppingRepository.addManual("Pan", null, MeasureUnit.PIECE)
            container.shoppingRepository.addManual("Leche", 1.0, MeasureUnit.LITER)
        }
        launch()
        rule.waitFor(hasText("Pan"))
        rule.onNodeWithText("Comprado").assertDoesNotExist()
        rule.onNodeWithText("Pan").performClick()
        rule.waitFor(hasText("Comprado"))
        // «Pan» está ahora debajo de «Comprado», y su sección («Despensa») ya no sale: no queda nada por comprar ahí.
        rule.waitForGone(section("Despensa"))
        rule.mainClock.advanceTimeBy(1_000)
        val header = rule.onNodeWithText("Comprado").fetchSemanticsNode().boundsInRoot
        val pan = rule.onNodeWithText("Pan").fetchSemanticsNode().boundsInRoot
        assertThat(pan.top).isGreaterThan(header.top)
        rule.onNodeWithText("Lácteos").assertIsDisplayed()

        rule.onNodeWithText("Pan").performClick()
        rule.waitFor(section("Despensa"))
        rule.waitForGone(hasText("Comprado"))
    }

    @Test
    fun addByHand_fromTheDialog() {
        launch()
        rule.waitFor(hasText("Tu lista está vacía. Desde una receta, pulsa «Lo que necesito»."))
        rule.onNodeWithTag("shopping-add").performClick()
        rule.onNodeWithTag("shopping-add-name").performTextInput("Tomates")
        rule.onNodeWithTag("shopping-add-quantity").performTextInput("1,5")
        rule.onNodeWithTag("shopping-add-confirm").performClick()
        rule.waitFor(hasText("Tomates"))
        rule.onNodeWithText("1½").assertIsDisplayed()
    }

    @Test
    fun clearChecked_offersUndo() {
        runBlocking {
            container.shoppingRepository.addManual("Pan", null, MeasureUnit.PIECE)
        }
        launch()
        rule.waitFor(hasText("Pan"))
        rule.onNodeWithText("Pan").performClick()
        rule.waitFor(hasText("Vaciar marcados"))
        rule.onNodeWithText("Vaciar marcados").performClick()
        rule.waitForGone(hasText("Pan"))
        rule.waitFor(hasText("Deshacer"))
        rule.onNodeWithText("Deshacer").performClick()
        rule.waitFor(hasText("Pan"))
    }

    @Test
    fun longPress_changesTheSection() {
        runBlocking { container.shoppingRepository.addManual("Pan", null, MeasureUnit.PIECE) }
        launch()
        rule.waitFor(hasText("Pan"))
        rule.onNode(section("Despensa")).assertIsDisplayed()
        rule.onNodeWithText("Pan").performTouchInput { longClick() }
        rule.waitFor(hasText("Bebidas"))
        rule.onNodeWithTag("section-option-DRINKS").performClick()
        rule.waitFor(hasText("Bebidas"))
        rule.waitForGone(section("Despensa"))
    }

    @Test
    fun deleteAction_thenUndo_restoresTheCurrentVersion() {
        runBlocking {
            container.shoppingRepository.addManual("Pan", 1.0, MeasureUnit.PIECE)
        }
        launch()
        rule.waitFor(hasText("Pan"))
        rule.onNodeWithText("Pan").performClick()
        rule.waitFor(hasText("Vaciar marcados"))
        val node = rule.onNode(hasText("Pan").and(hasClickAction()))
        val action = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Borrar" }
        rule.runOnUiThread { action.action() }
        rule.waitForGone(hasText("Pan"))
        rule.waitFor(hasText("Deshacer"))
        rule.onNodeWithText("Deshacer").performClick()
        rule.waitFor(hasText("Pan"))
        rule.waitFor(hasText("Vaciar marcados"))
    }

    @Test
    fun back_closesThePanel_andKeepsTheTypedText() {
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        rule.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            RecetarioTheme(ThemeChoice.CALIDO) { ShoppingRoute(container) }
        }
        rule.waitFor(hasText("Tu lista está vacía. Desde una receta, pulsa «Lo que necesito»."))
        rule.onNodeWithTag("shopping-add").performClick()
        rule.onNodeWithTag("shopping-add-name").performTextInput("Tomates")
        rule.runOnUiThread { dispatcher!!.onBackPressed() }
        rule.waitForGone(hasTestTag("shopping-add-panel"))
        rule.onNodeWithTag("shopping-add").performClick()
        rule.onNodeWithText("Tomates").assertIsDisplayed()
    }

    @Test
    fun listIsTheDefaultTab() {
        launch()
        rule.waitFor(hasTestTag("shopping-tab-list"))
        rule.onNodeWithTag("shopping-tab-list").assertIsSelected()
        rule.onNodeWithTag("shopping-tab-pantry").assertIsNotSelected()
    }

    @Test
    fun itemInPantry_showsYaLoTienes() {
        runBlocking {
            container.pantryRepository.add("tomate")
            container.shoppingRepository.addManual("Tomates, maduros", 3.0, MeasureUnit.PIECE)
            container.shoppingRepository.addManual("Leche", 1.0, MeasureUnit.LITER)
        }
        launch()
        rule.waitFor(hasText("Leche"))
        rule.waitFor(hasText("ya lo tienes"))
        // Solo los tomates: la leche no está en la despensa.
        assertThat(rule.onAllNodes(hasText("ya lo tienes"), useUnmergedTree = true).fetchSemanticsNodes()).hasSize(1)
        rule.onNode(hasText("Tomates, maduros").and(hasClickAction())).assert(hasText("ya lo tienes"))
    }

    @Test
    fun check_offersToPantry_addsIt() {
        runBlocking { container.shoppingRepository.addManual("Arroz", 1.0, MeasureUnit.KILOGRAM) }
        launch()
        rule.waitFor(hasText("Arroz"))

        rule.onNodeWithText("Arroz").performClick()
        rule.waitFor(hasText("A la despensa"))
        rule.onNodeWithText("A la despensa").performClick()

        rule.waitUntil(5_000) {
            runBlocking { container.pantryRepository.observe().first().map { it.name } } == listOf("Arroz")
        }
        // Desmarcar no ofrece nada.
        rule.waitForGone(hasText("A la despensa"))
        rule.onNodeWithText("Arroz").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("A la despensa").assertDoesNotExist()
    }

    @Test
    fun check_whileUndoIsShowing_keepsTheUndo() {
        runBlocking {
            container.shoppingRepository.addManual("Pan", 1.0, MeasureUnit.PIECE)
            container.shoppingRepository.addManual("Leche", 1.0, MeasureUnit.LITER)
        }
        launch()
        rule.waitFor(hasText("Pan"))
        rule.waitFor(hasText("Leche"))
        val node = rule.onNode(hasText("Pan").and(hasClickAction()))
        val action = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Borrar" }
        rule.runOnUiThread { action.action() }
        rule.waitForGone(hasText("Pan"))
        rule.waitFor(hasText("Deshacer"))

        // Marcar otro mientras se ve «Deshacer»: no se ofrece «A la despensa» y «Deshacer» sigue.
        rule.onNodeWithText("Leche").performClick()
        rule.waitFor(hasText("Vaciar marcados"))
        rule.onNodeWithText("A la despensa").assertDoesNotExist()
        rule.onNodeWithText("Deshacer").performClick()
        rule.waitFor(hasText("Pan"))
    }

    @Test
    fun uncheck_whileTheOfferIsShowing_closesIt() {
        runBlocking { container.shoppingRepository.addManual("Arroz", 1.0, MeasureUnit.KILOGRAM) }
        launch()
        rule.waitFor(hasText("Arroz"))
        rule.onNodeWithText("Arroz").performClick()
        rule.waitFor(hasText("A la despensa"))

        // Con el reloj quieto: si se cerrara solo por tiempo (4 s), seguiría ahí tras medio segundo.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Arroz").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithText("A la despensa").assertDoesNotExist()
        rule.mainClock.autoAdvance = true
        assertThat(runBlocking { container.pantryRepository.observe().first() }).isEmpty()
    }
}
