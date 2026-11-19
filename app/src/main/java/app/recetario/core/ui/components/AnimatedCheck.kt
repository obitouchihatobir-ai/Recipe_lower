package app.recetario.core.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.motion.animationProgress
import app.recetario.core.ui.theme.LocalRecetarioColors

/** Duración del relleno de la casilla (crece desde el centro). */
internal const val CHECK_FILL_MS = 200

/**
 * Círculo de 20 dp. Al marcarse, el relleno de acento crece desde el centro (200 ms) y la marca entra con
 * escala y rebote (muelle); al desmarcarse, al revés. Sin animaciones del sistema, el estado final al instante.
 */
@Composable
fun AnimatedCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val target = if (checked) 1f else 0f
    val fillAnim by animateFloatAsState(target, tween(CHECK_FILL_MS, easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)), label = "checkFill")
    val markAnim by animateFloatAsState(
        target,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkMark",
    )
    // Con las animaciones quitadas no se espera ni un fotograma: se pinta el estado final.
    val fill = if (reduced) target else fillAnim
    val mark = if (reduced) target else markAnim
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.8.dp, if (checked) c.accent else c.line, CircleShape)
            .semantics {
                stateDescription = if (checked) "Marcado" else "Sin marcar"
                animationProgress = fill
            },
    ) {
        Canvas(Modifier.size(20.dp)) {
            val s = size.minDimension
            if (fill > 0f) drawCircle(c.accent, radius = s / 2f * fill.coerceIn(0f, 1f))
            if (mark > 0.01f) {
                val path = Path().apply {
                    moveTo(s * 0.28f, s * 0.52f)
                    lineTo(s * 0.44f, s * 0.67f)
                    lineTo(s * 0.72f, s * 0.35f)
                }
                // El muelle pasa un poco de 1 (el rebote); nunca por debajo de 0.
                scale(mark.coerceAtLeast(0f)) {
                    drawPath(path, c.onAccent, style = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
