package app.recetario.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class ThemeRevealHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val theme = mutableStateOf(ThemeChoice.CALIDO)
    private val origin = mutableStateOf<Offset?>(null)

    private fun launch() = rule.setContent {
        ThemeRevealHost(theme.value, origin.value, onOriginConsumed = { origin.value = null }) { shown ->
            RecetarioTheme(shown) { Box(Modifier.fillMaxSize().background(LocalRecetarioColors.current.bg)) }
        }
    }

    private fun pixel(x: Int, y: Int): Color = rule.onRoot().captureToImage().toPixelMap()[x, y]

    private fun isColor(actual: Color, expected: Color) =
        abs(actual.red - expected.red) < 0.02f && abs(actual.green - expected.green) < 0.02f && abs(actual.blue - expected.blue) < 0.02f

    @Test
    fun circle_keepsTheOldFrameFarFromTheOrigin_thenShowsTheNewTheme() {
        val old = paletteFor(ThemeChoice.CALIDO).bg
        val new = paletteFor(ThemeChoice.NOCTURNO).bg
        launch()
        rule.waitForIdle()
        assertTrue(isColor(pixel(700, 1500), old))

        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            origin.value = Offset(0f, 0f)
            theme.value = ThemeChoice.NOCTURNO
        }
        // La instantánea del fotograma anterior se toma en otro hilo: se avanza hasta que el círculo
        // llega al origen, y entonces la esquina contraria tiene que seguir con el fondo antiguo.
        var elapsed = 0
        while (!isColor(pixel(5, 5), new) && elapsed < 400) {
            rule.mainClock.advanceTimeBy(16)
            Thread.sleep(20)
            elapsed += 16
        }
        assertTrue("el círculo no llegó a empezar", isColor(pixel(5, 5), new))
        assertTrue("la esquina lejana ya cambió de tema", isColor(pixel(700, 1500), old))

        // ~100 ms después (el círculo mide unos 260 px de 1700): lejos sigue el fondo antiguo, junto al origen el nuevo.
        // Si no se pudiera tomar la imagen del fotograma anterior (cambio directo), la esquina lejana ya sería nueva.
        rule.mainClock.advanceTimeBy(100)
        assertTrue("a ~100 ms la esquina lejana sigue antigua", isColor(pixel(700, 1500), old))
        assertTrue("a ~100 ms el origen es nuevo", isColor(pixel(5, 5), new))

        rule.mainClock.advanceTimeBy(700)
        assertTrue("al terminar todo es el tema nuevo", isColor(pixel(700, 1500), new))
    }
}
