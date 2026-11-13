package app.recetario.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class NavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    private fun launch() = rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }

    @Test
    fun bottomBar_navigatesBetweenTabs() {
        launch()
        rule.onNodeWithText("¿Qué cocinamos hoy?").assertIsDisplayed()
        rule.onNodeWithTag("tab-home").assertIsSelected()

        rule.onNodeWithTag("tab-recipes").performClick()
        rule.onNodeWithTag("recipes-search").assertIsDisplayed()
        rule.onNodeWithTag("tab-recipes").assertIsSelected()

        rule.onNodeWithTag("tab-shopping").performClick()
        rule.onNodeWithTag("shopping-placeholder").assertIsDisplayed()
        rule.onNodeWithTag("tab-shopping").assertIsSelected()

        rule.onNodeWithTag("tab-pairing").performClick()
        rule.onNodeWithText("Muy pronto: descubre qué bebida, plato o postre combina con cada receta.").assertIsDisplayed()
        rule.onNodeWithTag("tab-pairing").assertIsSelected()

        rule.onNodeWithTag("tab-home").performClick()
        rule.onNodeWithText("¿Qué cocinamos hoy?").assertIsDisplayed()
        rule.onNodeWithTag("tab-home").assertIsSelected()
    }

    @Test
    fun emptyHome_showsAddFirst_andOpensEditor() {
        launch()
        rule.waitFor(hasText("Aún no tienes recetas"))
        rule.onNodeWithText("Añadir la primera").performClick()
        rule.onNodeWithText("Nueva receta").assertIsDisplayed()
        // La barra inferior solo está en las pestañas.
        rule.onNodeWithTag("tab-home").assertDoesNotExist()
    }
}
