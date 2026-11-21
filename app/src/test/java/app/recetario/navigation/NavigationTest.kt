package app.recetario.navigation

import app.recetario.home.TODAY_EMPTY_TEXT
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.recetario.AppContainer
import app.recetario.core.ui.motion.SAVED_TOAST_MS
import app.recetario.core.ui.motion.SaveCelebration
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
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
        rule.onNodeWithTag("shopping-screen").assertIsDisplayed()
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
        rule.waitFor(hasText(TODAY_EMPTY_TEXT))
        rule.onNodeWithText("+ Nueva receta").performClick()
        rule.onNodeWithText("Nueva receta").assertIsDisplayed()
        // La barra inferior solo está en las pestañas.
        rule.onNodeWithTag("tab-home").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ rebote de la receta guardada

    private val celebration = SaveCelebration()
    private lateinit var nav: NavHostController

    private fun launchWithCelebration() = rule.setContent {
        nav = rememberNavController()
        RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav, saveCelebration = celebration) }
    }

    private val card = hasText("Crema de calabaza") and hasClickAction()

    private fun cardWidth(): Float? =
        rule.onAllNodes(card).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.width

    /** Con el reloj parado, avanza fotograma a fotograma (dejando trabajar a Room) hasta [done]. */
    private fun pumpUntil(done: () -> Boolean) {
        repeat(500) {
            rule.mainClock.advanceTimeByFrame()
            if (done()) return
            Thread.sleep(10)
        }
        error("condición no cumplida")
    }

    /** Vuelve atrás con el reloj parado y devuelve el ancho más pequeño de la tarjeta en los fotogramas siguientes. */
    private fun backToTheListAndMeasure(): Float {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { nav.popBackStack() }
        pumpUntil { cardWidth() != null }
        var smallest = Float.MAX_VALUE
        repeat(60) {
            cardWidth()?.let { smallest = minOf(smallest, it) }
            rule.mainClock.advanceTimeByFrame()
        }
        rule.mainClock.autoAdvance = true
        return smallest
    }

    @Test
    fun editedRecipe_bouncesOnce_whenItsCardShowsUpAgainInTheList() {
        runBlocking { container.recipeRepository.save(sampleRecipe("r1", "Crema de calabaza")) }
        launchWithCelebration()
        rule.onNodeWithTag("tab-recipes").performClick()
        // La tarjeta es el nodo fusionado (texto + toque); waitFor mira el árbol sin fusionar.
        rule.waitUntil(5_000) { cardWidth() != null }
        rule.mainClock.advanceTimeBy(1_500)
        val full = cardWidth()!!

        // Recetas → detalle → editar → Guardar: se vuelve al detalle, donde no hay tarjeta.
        rule.onNode(card).performClick()
        rule.waitFor(hasTestTag("detail-list"))
        rule.runOnIdle { nav.navigate(RecipeEditor(id = "r1")) }
        rule.waitFor(hasTestTag("editor-title"))
        rule.onNodeWithText("Guardar").performClick()
        rule.waitForGone(hasTestTag("editor-title"))
        rule.waitFor(hasTestTag("detail-list"))
        // El aviso se va a su hora, pero el rebote sigue pendiente.
        rule.mainClock.advanceTimeBy(SAVED_TOAST_MS + 500)
        rule.onNodeWithTag("saved-toast", useUnmergedTree = true).assertDoesNotExist()
        assertThat(celebration.bounceId).isEqualTo("r1")

        // De vuelta en la lista, la tarjeta rebota (una vez).
        assertThat(backToTheListAndMeasure()).isLessThan(full * 0.97f)
        assertThat(cardWidth()!!).isWithin(0.5f).of(full)
        assertThat(celebration.bounceId).isNull()

        // Otra visita al detalle y vuelta: ya no rebota.
        rule.onNode(card).performClick()
        rule.waitFor(hasTestTag("detail-list"))
        assertThat(backToTheListAndMeasure()).isWithin(0.5f).of(full)
    }

    @Test
    fun pendingBounce_isDropped_whenGoingSomewhereElse() {
        launchWithCelebration()
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))
        rule.runOnUiThread { celebration.recipeSaved("r1") }
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitForIdle()
        // Recetas es una de las pantallas con tarjetas: sigue pendiente.
        assertThat(celebration.bounceId).isEqualTo("r1")
        rule.onNodeWithTag("tab-shopping").performClick()
        rule.waitFor(hasTestTag("shopping-screen"))
        assertThat(celebration.bounceId).isNull()
    }
}
