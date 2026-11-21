package app.recetario.recipes.detail

import app.recetario.testing.CloseDatabaseRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.contrastRatio
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contraste real (píxeles pintados) de un ingrediente marcado en el detalle: tachado y en `muted`, sin
 * transparencias, debe llegar a 4,5:1 en los tres temas (como en el modo cocina).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class DetailContrastTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer

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
                ),
            )
        }
    }

    @Test
    fun calido() = checkContrast(ThemeChoice.CALIDO)

    @Test
    fun fresco() = checkContrast(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = checkContrast(ThemeChoice.NOCTURNO)

    private fun checkContrast(theme: ThemeChoice) {
        rule.setContent {
            RecetarioTheme(theme) { DetailRoute(container, "r1", onBack = {}, onEdit = {}, onCooking = {}) }
        }
        rule.waitFor(hasText("Mantequilla"))
        rule.onNodeWithText("Mantequilla").performClick() // marcado: tachado
        rule.mainClock.advanceTimeBy(1_000) // que terminen las transiciones

        // En la caja del texto, el color más frecuente es el fondo; el contraste más alto es el del trazo pleno.
        val pixels = rule.onNode(hasText("Mantequilla"), useUnmergedTree = true).captureToImage().toPixelMap()
        val all = (0 until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y] } }
        val background = all.groupingBy { it }.eachCount().maxBy { it.value }.key
        assertWithMessage("$theme: ingrediente marcado sobre su fondo")
            .that(all.maxOf { contrastRatio(it, background) }).isAtLeast(4.5)
    }
}
