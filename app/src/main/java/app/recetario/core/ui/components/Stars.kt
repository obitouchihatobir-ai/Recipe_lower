package app.recetario.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Relleno de cada estrella dibujada (1, 0,5 o 0). Solo lo leen las pruebas; TalkBack lee la descripción del padre. */
val StarFillsKey = SemanticsPropertyKey<List<Float>>("StarFills")
var SemanticsPropertyReceiver.starFills by StarFillsKey

/**
 * Estrellas dibujadas (no un glifo: «⯪» o «☆» pueden faltar en las fuentes de Android 8–10). Cada una se rellena
 * de [color] según su valor en [fills] (de izquierda a derecha, así la media estrella es la mitad izquierda) y
 * lleva el contorno en [color]. No tienen semántica propia salvo [StarFillsKey].
 */
@Composable
fun DrawnStars(fills: List<Float>, color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp, gap: Dp = 1.dp) {
    Row(modifier.semantics { starFills = fills }, horizontalArrangement = Arrangement.spacedBy(gap)) {
        fills.forEach { fill -> DrawnStar(fill, color, Modifier.size(size)) }
    }
}

/** Una estrella con el relleno [fill] (0 a 1, desde la izquierda) y contorno. */
@Composable
fun DrawnStar(fill: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawStar(fill, color) }
}

private fun DrawScope.drawStar(fill: Float, color: Color) {
    val strokeWidth = (size.minDimension * 0.08f).coerceAtLeast(1f)
    val path = starPath(size, inset = strokeWidth / 2)
    if (fill > 0f) {
        clipRect(right = size.width * fill.coerceIn(0f, 1f)) { drawPath(path, color) }
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

private fun starPath(size: Size, inset: Float): Path {
    val center = Offset(size.width / 2, size.height / 2 + size.height * 0.04f)
    val outer = size.minDimension / 2 - inset
    val inner = outer * 0.45f
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val point = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
}
