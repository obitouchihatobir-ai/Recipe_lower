package app.recetario.core.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors

/** Círculo de 20 dp; al marcarse se rellena con el acento y la marca entra con escala y rebote. */
@Composable
fun AnimatedCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val target = if (checked) 1f else 0f
    val scale by animateFloatAsState(
        target,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "checkScale",
    )
    val fill by animateFloatAsState(target, tween(if (reduced) 0 else 200), label = "checkFill")
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.8.dp, if (checked) c.accent else c.line, CircleShape)
            .semantics { stateDescription = if (checked) "Marcado" else "Sin marcar" },
    ) {
        Canvas(Modifier.size(20.dp)) {
            if (fill > 0f) drawCircle(c.accent.copy(alpha = fill))
            if (scale > 0f) {
                val s = size.minDimension
                val path = Path().apply {
                    moveTo(s * 0.28f, s * 0.52f)
                    lineTo(s * 0.44f, s * 0.67f)
                    lineTo(s * 0.72f, s * 0.35f)
                }
                val mark: Color = c.onAccent
                drawPath(
                    path, mark.copy(alpha = scale.coerceIn(0f, 1f)),
                    style = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
