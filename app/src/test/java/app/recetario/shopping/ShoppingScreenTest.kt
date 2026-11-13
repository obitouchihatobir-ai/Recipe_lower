package app.recetario.shopping

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ShoppingScreenTest {
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
        rule.onNodeWithText("Despensa").assertIsDisplayed()
        rule.onNodeWithText("Pan").performTouchInput { longClick() }
        rule.waitFor(hasText("Bebidas"))
        rule.onNodeWithTag("section-option-DRINKS").performClick()
        rule.waitFor(hasText("Bebidas"))
        rule.waitForGone(hasText("Despensa"))
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
}
