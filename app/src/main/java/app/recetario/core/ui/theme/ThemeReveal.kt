package app.recetario.core.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import app.recetario.core.ui.motion.rememberSystemReducedMotion
import kotlin.math.hypot

private const val REVEAL_MS = 450
private const val FADE_MS = 200

/**
 * Quien cambia el tema a propósito (la pantalla Apariencia) avisa aquí desde dónde se tocó, en coordenadas de
 * la ventana, para que el cambio se abra en círculo desde ese punto. Sin aviso, el cambio es un fundido.
 */
val LocalThemeRevealOrigin = staticCompositionLocalOf<(Offset?) -> Unit> { {} }

private class RevealState(displayed: ThemeChoice) {
    /** El tema con el que se compone el contenido: cambia solo cuando ya hay una imagen del fotograma anterior. */
    var displayed by mutableStateOf(displayed)
    var frozen by mutableStateOf<ImageBitmap?>(null)
    var circle by mutableStateOf(false)
    var origin by mutableStateOf(Offset.Zero)
    var progress by mutableFloatStateOf(1f)
    var animating by mutableStateOf(false)
}

/**
 * Al cambiar [choice], primero se toma una imagen real del contenido actual (con el tema anterior); luego el
 * contenido se compone con el tema nuevo y la imagen se dibuja encima, recortada fuera de un círculo que crece
 * desde [origin] en 450 ms. Sin [origin], o con movimiento reducido, la imagen se desvanece en 200 ms.
 * Se coloca fuera de `RecetarioTheme`, ocupa toda la ventana y recibe el tema con el que debe componer su
 * contenido. [onOriginConsumed] avisa de que el origen ya se usó.
 */
@Composable
fun ThemeRevealHost(
    choice: ThemeChoice,
    origin: Offset?,
    onOriginConsumed: () -> Unit = {},
    content: @Composable (ThemeChoice) -> Unit,
) {
    val reduced = rememberSystemReducedMotion()
    val layer = rememberGraphicsLayer()
    val state = remember { RevealState(choice) }
    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    val latestOrigin by rememberUpdatedState(origin)
    LaunchedEffect(choice) {
        if (choice == state.displayed) return@LaunchedEffect
        val tapped = latestOrigin
        // La capa aún tiene lo grabado con el tema anterior: se convierte en imagen antes de cambiar.
        // Si el dispositivo no puede hacerlo, el tema cambia de golpe en vez de fallar.
        val snapshot = try {
            layer.toImageBitmap()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            state.displayed = choice
            if (tapped != null) onOriginConsumed()
            return@LaunchedEffect
        }
        state.frozen = snapshot
        state.circle = tapped != null && !reduced
        state.origin = (tapped ?: Offset.Zero) - hostOffset
        state.progress = 0f
        state.animating = true
        state.displayed = choice
        if (tapped != null) onOriginConsumed()
        val spec = if (state.circle) tween<Float>(REVEAL_MS, easing = FastOutSlowInEasing) else tween<Float>(FADE_MS, easing = LinearEasing)
        animate(0f, 1f, animationSpec = spec) { value, _ -> state.progress = value }
        state.animating = false
        state.frozen = null
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOffset = it.positionInRoot() }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val frozen = state.frozen
                if (state.animating && frozen != null) {
                    if (state.circle) {
                        val center = state.origin
                        val reach = maxOf(
                            hypot(center.x, center.y),
                            hypot(size.width - center.x, center.y),
                            hypot(center.x, size.height - center.y),
                            hypot(size.width - center.x, size.height - center.y),
                        )
                        val circle = Path().apply { addOval(Rect(center = center, radius = reach * state.progress)) }
                        clipPath(circle, clipOp = ClipOp.Difference) { drawImage(frozen) }
                    } else {
                        drawImage(frozen, alpha = 1f - state.progress)
                    }
                }
            },
    ) { content(state.displayed) }
}
