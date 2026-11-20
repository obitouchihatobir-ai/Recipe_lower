package app.recetario.pantry

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class WhatCanIMakeTest {
    /** Cierra la base de datos después de desechar la composición (como en ShoppingScreenTest). */
    @get:Rule(order = 0)
    val closeDatabase = object : ExternalResource() {
        override fun after() = container.database.close()
    }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    private fun ingredients(recipe: String, vararg names: String): List<Ingredient> =
        names.mapIndexed { i, n -> ingredient("$recipe-$i", n, 1.0, MeasureUnit.PIECE) }

    @Test
    fun ordersByCoverage_showsMissing() {
        runBlocking {
            container.recipeRepository.save(sampleRecipe("guaca", "Guacamole", ingredients = ingredients("guaca", "Aguacate", "Cebolla", "Cilantro", "Sal")))
            container.recipeRepository.save(sampleRecipe("pozole", "Pozole", ingredients = ingredients("pozole", "Maíz", "Chile guajillo", "Orégano", "Rábano", "Cebolla")))
            container.recipeRepository.save(sampleRecipe("ensalada", "Ensalada", ingredients = ingredients("ensalada", "Tomate", "Cebolla, picada")))
            container.recipeRepository.save(sampleRecipe("agua", "Agua sola", ingredients = ingredients("agua", "Agua")))
            listOf("aguacate", "cebolla", "tomates").forEach { container.pantryRepository.add(it) }
        }
        var opened: String? = null
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) { WhatCanIMakeRoute(container, onBack = {}, onRecipe = { opened = it }) }
        }
        rule.waitFor(hasText("Guacamole"))

        rule.onNodeWithText("¡Tienes todo!").assertExists()
        rule.onNodeWithText("Tienes 2 de 3 · falta: Cilantro").assertExists()
        rule.onNodeWithText("Tienes 1 de 5 · falta: Maíz, Chile guajillo, Orégano…").assertExists()
        // Una receta con solo básicos no cuenta.
        rule.onNodeWithText("Agua sola").assertDoesNotExist()

        val tops = listOf("Ensalada", "Guacamole", "Pozole").map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertThat(tops).isInOrder()

        rule.onNodeWithText("Guacamole").performClick()
        rule.runOnIdle { assertThat(opened).isEqualTo("guaca") }
    }

    @Test
    fun noRecipes_saysSo() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { WhatCanIMakeRoute(container, onBack = {}, onRecipe = {}) } }
        rule.waitFor(hasText("Cuando tengas recetas con ingredientes, aquí verás cuáles puedes hacer."))
    }
}
