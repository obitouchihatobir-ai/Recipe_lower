package app.recetario.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Lo que dura el confeti de «¡Listo!» (más que las demás animaciones: es la celebración del final). */
const val CONFETTI_MS = 1_200

private val INGREDIENTS = listOf("🌿", "🍋", "🧄", "🌶️", "🧀", "🍅", "🥕", "🫒", "🍄", "🥑")

/** Una pieza del confeti: dónde cae (fracción del ancho), cuándo empieza y cuánto se mece y gira. */
private class Piece(val emoji: String, val x: Float, val delay: Float, val fall: Float, val sway: Float, val spin: Float, val size: Float)

/**
 * Confeti suave de emojis de ingredientes que cae de arriba abajo en [CONFETTI_MS] ms y se desvanece al final.
 * Va detrás del contenido y no tiene `pointerInput` ni nada que se pueda tocar: los toques pasan a lo que hay
 * debajo. No tiene semántica: TalkBack no lo anuncia. Llama a [onFinished] al terminar. Quien lo usa no lo compone si las
 * animaciones están quitadas.
 */
@Composable
fun IngredientConfetti(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    val done by rememberUpdatedState(onFinished)
    val pieces = remember {
        // Semilla fija: siempre el mismo dibujo (las capturas se pueden comparar).
        val random = Random(18)
        List(18) { i ->
            Piece(
                emoji = INGREDIENTS[i % INGREDIENTS.size],
                x = (i + random.nextFloat()) / 18f,
                delay = random.nextFloat() * 0.3f,
                fall = 0.55f + random.nextFloat() * 0.15f,
                sway = 6f + random.nextFloat() * 10f,
                spin = (random.nextFloat() - 0.5f) * 160f,
                size = 18f + random.nextFloat() * 8f,
            )
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(CONFETTI_MS, easing = LinearEasing))
        done()
    }
    Canvas(modifier) {
        val t = progress.value
        for (p in pieces) {
            // Cada pieza cae durante `fall` del total, a partir de su `delay`.
            val local = ((t - p.delay) / p.fall).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val layout = measurer.measure(p.emoji, TextStyle(fontSize = p.size.sp))
            val fallen = local * (0.6f + 0.4f * local) // empieza despacio y cae algo más deprisa al final
            val y = -layout.size.height + (size.height + layout.size.height) * fallen
            val x = size.width * p.x - layout.size.width / 2f + sin(local * 2f * PI.toFloat()) * p.sway * density
            val alpha = if (local > 0.75f) (1f - local) / 0.25f else 1f
            rotate(p.spin * local, Offset(x + layout.size.width / 2f, y + layout.size.height / 2f)) {
                drawText(layout, topLeft = Offset(x, y), alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
}
