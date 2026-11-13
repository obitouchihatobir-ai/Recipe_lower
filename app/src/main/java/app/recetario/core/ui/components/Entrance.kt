package app.recetario.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.motion.LocalReducedMotion
import kotlinx.coroutines.delay

/** Sube 14 dp y aparece en 450 ms, con 60 ms de retraso por índice (máximo 6). No bloquea el toque. */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val offsetPx = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(Unit) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            delay(60L * index.coerceIn(0, 6))
            progress.animateTo(1f, tween(450, easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}
