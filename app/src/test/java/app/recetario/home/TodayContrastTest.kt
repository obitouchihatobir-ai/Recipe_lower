package app.recetario.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.contrastRatio
import app.recetario.recipes.model.Moment
import app.recetario.today.TodaySlot
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contraste real (píxeles pintados) de cada texto de la tarjeta Hoy sobre su fondo (el degradado de la tarjeta, una
 * receta o un botón): al menos 4,5:1 en los tres temas, sin transparencias en el texto.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class TodayContrastTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = TodayState(
        loaded = true,
        eyebrow = "Cena de hoy · Otoño",
        reason = "Hace 9 °C y llueve: algo caliente y reconfortante",
        moment = Moment.DINNER,
        items = listOf(
            TodayCardItem(TodaySlot.MAIN, TodayCardRecipe("sopa", "Crema de calabaza", "🍲", "Comidas", "Principal · 35 min", 4)),
            TodayCardItem(TodaySlot.DRINK, TodayCardRecipe("choco", "Chocolate caliente", "🥤", "Bebidas", "Bebida · 10 min", 2)),
            TodayCardItem(TodaySlot.DESSERT, null),
        ),
        weather = TodayWeatherChip("🌧️", "9 °C · Madrid", "El tiempo ahora: lluvia, 9 °C en Madrid"),
    )

    private val texts = listOf(
        "Cena de hoy · Otoño",
        "Hace 9 °C y llueve: algo caliente y reconfortante",
        "Crema de calabaza",
        "Principal · 35 min",
        "Chocolate caliente",
        "Bebida · 10 min",
        "Aún no tienes postres",
        "Empezar",
        "Otra idea",
    )

    @Test
    fun calido() = checkContrast(ThemeChoice.CALIDO)

    @Test
    fun fresco() = checkContrast(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = checkContrast(ThemeChoice.NOCTURNO)

    private fun checkContrast(theme: ThemeChoice) {
        rule.setContent {
            RecetarioTheme(theme) {
                Column(Modifier.padding(16.dp)) {
                    TodayCard(state, onRecipe = {}, onStart = { _, _ -> }, onAnotherIdea = {}, onCreate = {})
                    TodayEmptyCard(onAdd = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        for (text in texts + TODAY_EMPTY_TEXT + "+ Nueva receta") {
            assertWithMessage("$theme: «$text» sobre su fondo").that(contrastOf(text)).isAtLeast(4.5)
        }
        // Estos dos solo se leen por su descripción (sin nodo de texto): se mira el nodo entero. Del tiempo, solo la
        // mitad derecha, donde va el texto y no el emoji.
        assertWithMessage("$theme: «+ Crear uno» sobre su fondo")
            .that(contrastOf(hasTestTag("today-create-dessert"))).isAtLeast(4.5)
        assertWithMessage("$theme: «9 °C · Madrid» sobre su fondo")
            .that(contrastOf(hasTestTag("today-weather"), fromFraction = 0.5f)).isAtLeast(4.5)
    }

    /**
     * Las esquinas de la caja del texto son su fondo (en un degradado, cada una el suyo): el contraste es el del trazo
     * pleno (el píxel más contrastado) frente a la esquina menos favorable.
     */
    private fun contrastOf(text: String): Double = contrastOf(hasText(text))

    /** Igual, con el nodo de [matcher]; solo cuentan los píxeles a la derecha de [fromFraction] de su ancho. */
    private fun contrastOf(matcher: SemanticsMatcher, fromFraction: Float = 0f): Double {
        val pixels = rule.onNode(matcher, useUnmergedTree = true).captureToImage().toPixelMap()
        val from = (pixels.width * fromFraction).toInt()
        val all = (from until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y] } }
        val corners = listOf(
            pixels[0, 0], pixels[pixels.width - 1, 0], pixels[0, pixels.height - 1], pixels[pixels.width - 1, pixels.height - 1],
        )
        return corners.minOf { bg -> all.maxOf { contrastRatio(it, bg) } }
    }
}
