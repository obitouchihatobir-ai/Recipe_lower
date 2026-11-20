package app.recetario.pantry

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.shopping.ShoppingRoute
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class PantryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    private fun openPantry() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { ShoppingRoute(container) } }
        rule.waitFor(hasTestTag("shopping-tab-pantry"))
        rule.onNodeWithTag("shopping-tab-pantry").performClick()
        rule.waitFor(hasTestTag("pantry-field"))
    }

    private fun pantryNames() = runBlocking { container.pantryRepository.observe().first().map { it.name } }

    @Test
    fun add_withSuggestion() {
        runBlocking {
            container.recipeRepository.save(
                sampleRecipe(
                    "salsa", "Salsa roja",
                    ingredients = listOf(
                        ingredient("i1", "Tomate saladet", 3.0, MeasureUnit.PIECE),
                        ingredient("i2", "Tomillo", null, MeasureUnit.PINCH),
                        ingredient("i3", "Cebolla", 1.0, MeasureUnit.PIECE),
                    ),
                ),
            )
        }
        openPantry()
        rule.waitFor(hasText("Tu despensa está vacía. Añade lo que tienes en casa."))

        rule.onNodeWithTag("pantry-field").performTextInput("tom")
        rule.waitFor(hasTestTag("pantry-suggestion-Tomillo"))
        rule.onNodeWithTag("pantry-suggestion-Cebolla").assertDoesNotExist()
        rule.onNodeWithTag("pantry-suggestion-Tomate saladet").performClick()

        rule.waitFor(hasContentDescription("Quitar «Tomate saladet»"))
        rule.waitForGone(hasTestTag("pantry-suggestion-Tomate saladet"))
        rule.waitUntil(5_000) { pantryNames() == listOf("Tomate saladet") }

        // Escribir y pulsar Intro también guarda.
        rule.onNodeWithTag("pantry-field").performTextInput("Arroz")
        rule.onNodeWithTag("pantry-field").performImeAction()
        rule.waitUntil(5_000) { pantryNames() == listOf("Arroz", "Tomate saladet") }
    }

    @Test
    fun delete_undo() {
        runBlocking { container.pantryRepository.add("Arroz") }
        openPantry()
        rule.waitFor(hasContentDescription("Quitar «Arroz»"))

        rule.onNodeWithContentDescription("Quitar «Arroz»").performClick()

        rule.waitForGone(hasContentDescription("Quitar «Arroz»"))
        rule.waitFor(hasText("Deshacer"))
        rule.onNodeWithText("Deshacer").performClick()
        rule.waitFor(hasContentDescription("Quitar «Arroz»"))
        assertThat(pantryNames()).containsExactly("Arroz")
    }

    @Test
    fun alreadyThere_message() {
        runBlocking { container.pantryRepository.add("Arroz") }
        openPantry()
        rule.waitFor(hasContentDescription("Quitar «Arroz»"))

        rule.onNodeWithTag("pantry-field").performTextInput("ARROZ")
        rule.onNodeWithTag("pantry-add").performClick()

        rule.waitFor(hasText("Ya está en la despensa"))
        assertThat(pantryNames()).containsExactly("Arroz")
    }

    @Test
    fun weekTab_saysItIsComing() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { ShoppingRoute(container) } }
        rule.waitFor(hasTestTag("shopping-tab-week"))
        rule.onNodeWithTag("shopping-tab-week").performClick()
        rule.waitFor(hasText("Llega pronto: el menú de la semana"))
    }

    @Test
    fun whatCanIMake_buttonOpensIt() {
        var opened = 0
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { ShoppingRoute(container, onWhatCanIMake = { opened++ }) } }
        rule.waitFor(hasTestTag("shopping-tab-pantry"))
        rule.onNodeWithTag("shopping-tab-pantry").performClick()
        rule.waitFor(hasText("¿Qué puedo hacer?"))
        rule.onNodeWithText("¿Qué puedo hacer?").performClick()
        rule.runOnIdle { assertThat(opened).isEqualTo(1) }
    }
}
