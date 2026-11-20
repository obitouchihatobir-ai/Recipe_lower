package app.recetario.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.backup.SwitchableDataStore
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.pantry.DEFAULT_PANTRY_BASICS
import app.recetario.pantry.WhatCanIMakeRoute
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testSettingsDataStore
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsBasicsTest {
    /** Cierra la base de datos después de desechar la composición (como en ShoppingScreenTest). */
    @get:Rule(order = 0)
    val closeDatabase = object : ExternalResource() {
        override fun after() {
            container?.database?.close()
        }
    }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private var container: AppContainer? = null

    @Test
    fun editBasics_changesCoverage() {
        val container = installTestContainer().also { container = it }
        runBlocking {
            assertThat(container.settingsRepository.pantryBasics.first()).isEqualTo(DEFAULT_PANTRY_BASICS)
            container.recipeRepository.save(
                sampleRecipe(
                    "sopa", "Sopa de tomate",
                    ingredients = listOf(
                        ingredient("i1", "Tomate", 4.0, MeasureUnit.PIECE),
                        ingredient("i2", "Sal", null, MeasureUnit.PINCH),
                    ),
                ),
            )
            container.pantryRepository.add("tomate")
        }
        var basics by mutableStateOf(false)
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                if (basics) PantryBasicsRoute(container, onBack = {}) else WhatCanIMakeRoute(container, onBack = {}, onRecipe = {})
            }
        }
        // La sal es un básico: no cuenta.
        rule.waitFor(hasText("¡Tienes todo!"))

        rule.runOnIdle { basics = true }
        rule.waitFor(hasContentDescription("Quitar «sal»"))
        rule.onNodeWithContentDescription("Quitar «sal»").performClick()
        rule.waitForGone(hasContentDescription("Quitar «sal»"))
        rule.onNodeWithTag("basics-field").performTextInput("Comino")
        rule.onNodeWithTag("basics-add").performClick()
        rule.waitFor(hasContentDescription("Quitar «Comino»"))
        rule.waitUntil(5_000) {
            runBlocking { container.settingsRepository.pantryBasics.first() } == listOf("agua", "aceite", "pimienta", "azúcar", "Comino")
        }

        rule.runOnIdle { basics = false }
        rule.waitFor(hasText("Tienes 1 de 2 · falta: Sal"))
    }

    @Test
    fun basics_whenTheyCannotBeSaved_saySo() {
        val settings = SwitchableDataStore(testSettingsDataStore()).apply { failWrites = true }
        val container = installTestContainer(settingsDataStore = settings).also { container = it }
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { PantryBasicsRoute(container, onBack = {}) } }
        rule.waitFor(hasContentDescription("Quitar «sal»"))

        rule.onNodeWithContentDescription("Quitar «sal»").performClick()

        rule.waitFor(hasText("No se pudieron guardar los básicos."))
    }

    @Test
    fun settings_hasTheBasicsRow() {
        val container = installTestContainer().also { container = it }
        var opened = 0
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                SettingsRoute(
                    container, onBack = {}, onAppearance = {}, onCategories = {}, onTags = {}, onBackup = {}, onAbout = {},
                    onPantryBasics = { opened++ },
                )
            }
        }
        rule.waitFor(hasTestTag("settings-pantry-basics"))
        rule.onNodeWithTag("settings-pantry-basics").performClick()
        rule.runOnIdle { assertThat(opened).isEqualTo(1) }
    }
}
