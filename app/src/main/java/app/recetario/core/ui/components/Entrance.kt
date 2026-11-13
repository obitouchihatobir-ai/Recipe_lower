package app.recetario.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.motion.LocalReducedMotion
import kotlinx.coroutines.delay

/** Recuerda qué entradas ya se animaron, para que no se repitan al volver a entrar en una lista perezosa. */
open class EntranceTracker(initial: Collection<String> = emptyList()) {
    private val played = initial.toMutableSet()
    open fun hasPlayed(key: Any): Boolean = key.toString() in played
    open fun markPlayed(key: Any) {
        played += key.toString()
    }
    internal fun snapshot(): List<String> = played.toList()
}

@Composable
fun rememberEntranceTracker(): EntranceTracker {
    val saved = rememberSaveable { arrayListOf<String>() }
    return remember {
        object : EntranceTracker(saved) {
            override fun markPlayed(key: Any) {
                super.markPlayed(key)
                saved.clear()
                saved.addAll(snapshot())
            }
        }
    }
}

/** `true` si la entrada debe pintarse ya en su estado final (sin animar). */
internal fun entranceIsInstant(reduced: Boolean, key: Any?, tracker: EntranceTracker?): Boolean =
    reduced || (key != null && tracker != null && tracker.hasPlayed(key))

/**
 * Sube 14 dp y aparece en 450 ms, con 60 ms de retraso por índice (máximo 6). No bloquea el toque.
 * Con [tracker] y [key], una entrada ya reproducida se pinta al instante (listas perezosas).
 */
fun Modifier.staggeredEntrance(index: Int, key: Any? = null, tracker: EntranceTracker? = null): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val instant = entranceIsInstant(reduced, key, tracker)
    val progress = remember { Animatable(if (instant) 1f else 0f) }
    val offsetPx = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(reduced) {
        if (entranceIsInstant(reduced, key, tracker)) {
            progress.snapTo(1f)
        } else {
            delay(60L * index.coerceIn(0, 6))
            progress.animateTo(1f, tween(450, easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)))
        }
        if (key != null) tracker?.markPlayed(key)
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}
