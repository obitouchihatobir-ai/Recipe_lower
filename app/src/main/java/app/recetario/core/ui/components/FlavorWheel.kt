package app.recetario.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.model.FlavorProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val axisLabels = listOf("Dulce", "Salado", "Ácido", "Amargo", "Picante")

private fun axisPoint(i: Int, n: Int, center: Offset, r: Float): Offset {
    val a = -PI / 2 + i * 2 * PI / n
    return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
}

/** Vértices del polígono del perfil: primer eje hacia arriba, en sentido horario; 0 se dibuja al 6 % del radio. */
fun flavorPolygon(values: List<Int>, center: Offset, radius: Float): List<Offset> =
    values.mapIndexed { i, v ->
        val fraction = if (v <= 0) 0.06f else v.coerceAtMost(3) / 3f
        axisPoint(i, values.size, center, fraction * radius)
    }

internal fun flavorDescription(p: FlavorProfile): String =
    "Perfil de sabor: dulce ${p.sweet} de 3, salado ${p.salty} de 3, ácido ${p.sour} de 3, " +
        "amargo ${p.bitter} de 3, picante ${p.spicy} de 3"

@Composable
fun FlavorWheel(
    profile: FlavorProfile,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    animate: Boolean = true,
) {
    val colors = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val animated = animate && !reduced
    val grow = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(animated) {
        if (animated) grow.animateTo(1f, tween(450, easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)))
        else grow.snapTo(1f)
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.muted, fontSize = 8.sp)
    val values = listOf(profile.sweet, profile.salty, profile.sour, profile.bitter, profile.spicy)

    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = flavorDescription(profile) },
    ) {
        val unit = this.size.minDimension / 120f
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = 46f * unit
        for (k in 1..4) {
            val path = Path()
            for (i in 0 until 5) {
                val p = axisPoint(i, 5, c, r * k / 4)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, colors.line, style = Stroke(width = 1f * unit))
        }
        for (i in 0 until 5) {
            drawLine(colors.line, c, axisPoint(i, 5, c, r), strokeWidth = 1f * unit)
            val l = axisPoint(i, 5, c, r + 10f * unit)
            val layout = measurer.measure(axisLabels[i], labelStyle)
            drawText(layout, topLeft = Offset(l.x - layout.size.width / 2f, l.y - layout.size.height / 2f))
        }
        val poly = Path()
        flavorPolygon(values, c, r).forEachIndexed { i, p ->
            if (i == 0) poly.moveTo(p.x, p.y) else poly.lineTo(p.x, p.y)
        }
        poly.close()
        scale(grow.value, pivot = c) {
            drawPath(poly, colors.accent.copy(alpha = 0.28f))
            drawPath(poly, colors.accent, style = Stroke(width = 2f * unit, join = StrokeJoin.Round))
        }
    }
}
