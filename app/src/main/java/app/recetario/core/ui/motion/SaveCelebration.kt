package app.recetario.core.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.theme.LocalRecetarioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull

/** Lo que tarda en dibujarse el ✓ del aviso «Receta guardada». */
const val SAVED_CHECK_MS = 350

/** Lo que el aviso «Receta guardada» se queda a la vista. */
const val SAVED_TOAST_MS = 1_800L

/** Escala desde la que rebota la tarjeta de la receta recién guardada. */
const val SAVED_BOUNCE_FROM = 0.92f

/**
 * Lo que celebra que se ha guardado una receta: el aviso «✓ Receta guardada» (lo pinta [SavedToast]) y el rebote
 * de su tarjeta en Inicio o Recetas ([savedBounce]), una sola vez. Uno solo, en `AppNavHost`.
 */
@Stable
class SaveCelebration {
    /** Cambia cada vez que se guarda: el aviso vuelve a salir. 0 = nunca. */
    var toastKey by mutableIntStateOf(0)
        private set

    /** La receta cuya tarjeta aún debe rebotar (null: ninguna). */
    var bounceId by mutableStateOf<String?>(null)
        private set

    fun recipeSaved(id: String) {
        bounceId = id
        toastKey++
    }

    /** La tarjeta de [id] la reclama al empezar a rebotar: true solo la primera vez. */
    fun claimBounce(id: String): Boolean {
        if (bounceId != id) return false
        bounceId = null
        return true
    }

    /** Si la tarjeta no llegó a verse mientras estaba el aviso, ya no rebota más tarde. */
    internal fun expireBounce() {
        bounceId = null
    }
}

val LocalSaveCelebration = compositionLocalOf<SaveCelebration?> { null }

/**
 * La tarjeta de la receta recién guardada entra con un pequeño rebote (escala 0,92 → 1 con muelle), una sola
 * vez: la primera tarjeta que la reclama. No bloquea el toque ni retrasa nada. Sin animaciones, no se mueve.
 */
fun Modifier.savedBounce(recipeId: String): Modifier = composed {
    val celebration = LocalSaveCelebration.current
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    if (celebration != null) {
        LaunchedEffect(celebration, recipeId, reduced) {
            snapshotFlow { celebration.bounceId?.takeIf { it == recipeId } }.filterNotNull().collect {
                if (celebration.claimBounce(recipeId) && !reduced) {
                    scale.snapTo(SAVED_BOUNCE_FROM)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Aviso «Receta guardada» con un ✓ que se dibuja solo (trazo de [SAVED_CHECK_MS] ms). Sale abajo durante
 * [SAVED_TOAST_MS] ms y no se puede tocar: los toques pasan a lo que hay debajo (no es un `Surface`). TalkBack lo
 * lee una vez (región viva). Sin animaciones, el ✓ sale ya dibujado y el aviso aparece y se va sin moverse.
 */
@Composable
fun SavedToast(celebration: SaveCelebration, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var visible by remember { mutableStateOf(false) }
    val key = celebration.toastKey
    LaunchedEffect(key) {
        if (key == 0) return@LaunchedEffect
        visible = true
        delay(SAVED_TOAST_MS)
        visible = false
        celebration.expireBounce()
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
        exit = if (reduced) ExitTransition.None else fadeOut(tween(200)),
    ) {
        val c = LocalRecetarioColors.current
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .background(c.ink, CircleShape)
                .padding(horizontal = 18.dp)
                .testTag("saved-toast"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawnCheck(key, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Receta guardada",
                color = c.bg,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** ✓ que se dibuja de izquierda a derecha en [SAVED_CHECK_MS] ms (de nuevo con cada [key]); decorativo. */
@Composable
private fun DrawnCheck(key: Int, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val progress = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key, reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(SAVED_CHECK_MS, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
    }
    val drawn = progress.value
    Canvas(modifier.semantics { animationProgress = drawn }) {
        val s = size.minDimension
        val path = Path().apply {
            moveTo(s * 0.18f, s * 0.54f)
            lineTo(s * 0.42f, s * 0.76f)
            lineTo(s * 0.84f, s * 0.26f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * drawn, part, true)
        drawPath(part, c.bg, style = Stroke(width = s * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
