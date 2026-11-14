package app.recetario.cooking

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.contrastRatio
import app.recetario.core.ui.theme.paletteFor
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.Cooking
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contraste real (píxeles pintados) de lo que el modo cocina da por hecho: el texto de un paso hecho, la ✓ de
 * su círculo y un ingrediente tachado deben llegar a 4,5:1 en los tres temas (nada de transparencias).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class CookingContrastTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        container = installTestContainer()
        runBlocking {
            container.recipeRepository.save(
                sampleRecipe(
                    "r1", "Crema de calabaza",
                    ingredients = listOf(
                        ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
                        ingredient("i2", "Mantequilla", 20.0, MeasureUnit.GRAM),
                    ),
                    steps = listOf(
                        Step("s1", "Pela la calabaza y córtala en cubos.", emptyList(), null, setOf("i1")),
                        Step("s2", "Sofríe la cebolla y el ajo.", emptyList(), null, setOf("i2")),
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() = container.database.close()

    @Test
    fun calido() = checkContrast(ThemeChoice.CALIDO)

    @Test
    fun fresco() = checkContrast(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = checkContrast(ThemeChoice.NOCTURNO)

    private fun checkContrast(theme: ThemeChoice) {
        val c = paletteFor(theme)
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(theme) { AppNavHost(container, navController = nav) }
        }
        rule.runOnIdle { nav.navigate(Cooking("r1", 4)) }
        rule.waitFor(hasText("Mantequilla"))
        rule.onNodeWithText("Mantequilla").performClick() // tachado
        val done = hasContentDescription("Marcar el paso 1 como hecho")
        rule.waitFor(done)
        rule.onNodeWithTag("cooking-list").performScrollToNode(done)
        rule.onNode(done).performClick()
        rule.waitFor(hasContentDescription("Marcar el paso 2 como hecho"))
        rule.mainClock.advanceTimeBy(1_000) // que terminen las transiciones

        val stepText = hasText("Pela la calabaza y córtala en cubos.")
        rule.onNodeWithTag("cooking-list").performScrollToNode(stepText)
        rule.mainClock.advanceTimeBy(500)
        assertWithMessage("$theme: texto de un paso hecho sobre el fondo")
            .that(maxContrast(rule.onNode(stepText, useUnmergedTree = true), c.bg)).isAtLeast(4.5)

        val circle = rule.onNode(hasContentDescription("Paso 1"), useUnmergedTree = true)
        assertWithMessage("$theme: ✓ sobre el círculo de un paso hecho")
            .that(centerContrast(circle)).isAtLeast(4.5)

        val checked = hasText("Mantequilla")
        rule.onNodeWithTag("cooking-list").performScrollToNode(checked)
        rule.mainClock.advanceTimeBy(500)
        assertWithMessage("$theme: ingrediente tachado sobre el fondo")
            .that(maxContrast(rule.onNode(checked, useUnmergedTree = true), c.bg)).isAtLeast(4.5)
    }

    /** El contraste más alto entre un píxel del nodo y [background] (el trazo pleno del texto). */
    private fun maxContrast(node: SemanticsNodeInteraction, background: Color): Double {
        val pixels = node.captureToImage().toPixelMap()
        var best = 1.0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            best = maxOf(best, contrastRatio(pixels[x, y], background))
        }
        return best
    }

    /** En el centro del círculo: el color más frecuente es el relleno; el contraste más alto es el de la ✓. */
    private fun centerContrast(node: SemanticsNodeInteraction): Double {
        val pixels = node.captureToImage().toPixelMap()
        val from = pixels.width / 4
        val to = pixels.width * 3 / 4
        val inner = (from until to).flatMap { x -> (from until to).map { y -> pixels[x, y] } }
        val fill = inner.groupingBy { it }.eachCount().maxBy { it.value }.key
        return inner.maxOf { contrastRatio(it, fill) }
    }
}
