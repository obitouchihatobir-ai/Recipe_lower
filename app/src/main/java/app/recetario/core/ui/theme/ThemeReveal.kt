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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

private class RevealState(var choice: ThemeChoice) {
    /** Dos capas se alternan: una graba el contenido vivo y la otra conserva el último fotograma anterior. */
    var activeIsA = true
    var circle = false
    var origin = Offset.Zero
    var generation = 0
    var progress by mutableFloatStateOf(1f)
    var animating by mutableStateOf(false)
}

/**
 * Al cambiar [choice], el fotograma anterior se queda detrás y el contenido nuevo se dibuja recortado por un
 * círculo que crece desde [origin] en 450 ms. Sin [origin], o con movimiento reducido, hay un fundido de 200 ms.
 * Se coloca fuera de `RecetarioTheme` y ocupa toda la ventana. [onOriginConsumed] avisa de que el origen ya se usó.
 */
@Composable
fun ThemeRevealHost(
    choice: ThemeChoice,
    origin: Offset?,
    onOriginConsumed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val reduced = rememberSystemReducedMotion()
    val layerA = rememberGraphicsLayer()
    val layerB = rememberGraphicsLayer()
    val state = remember { RevealState(choice) }
    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    if (state.choice != choice) {
        state.choice = choice
        state.activeIsA = !state.activeIsA
        state.circle = origin != null && !reduced
        state.origin = (origin ?: Offset.Zero) - hostOffset
        state.generation++
        state.progress = 0f
        state.animating = true
        if (origin != null) onOriginConsumed()
    }
    val generation = state.generation
    LaunchedEffect(generation) {
        if (generation == 0) return@LaunchedEffect
        val spec = if (state.circle) tween<Float>(REVEAL_MS, easing = FastOutSlowInEasing) else tween<Float>(FADE_MS, easing = LinearEasing)
        animate(0f, 1f, animationSpec = spec) { value, _ -> state.progress = value }
        state.animating = false
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOffset = it.positionInRoot() }
            .drawWithContent {
                val live = if (state.activeIsA) layerA else layerB
                val previous = if (state.activeIsA) layerB else layerA
                live.record { this@drawWithContent.drawContent() }
                if (!state.animating) {
                    live.alpha = 1f
                    drawLayer(live)
                } else if (state.circle) {
                    live.alpha = 1f
                    drawLayer(previous)
                    val center = state.origin
                    val reach = maxOf(
                        hypot(center.x, center.y),
                        hypot(size.width - center.x, center.y),
                        hypot(center.x, size.height - center.y),
                        hypot(size.width - center.x, size.height - center.y),
                    )
                    val circle = Path().apply {
                        addOval(
                            Rect(
                                center = center,
                                radius = reach * state.progress,
                            ),
                        )
                    }
                    clipPath(circle) { drawLayer(live) }
                } else {
                    drawLayer(previous)
                    live.alpha = state.progress
                    drawLayer(live)
                }
            },
    ) { content() }
}
