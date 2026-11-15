package app.recetario.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Relación de contraste WCAG entre dos colores opacos (de 1.0 a 21.0). */
fun contrastRatio(fg: Color, bg: Color): Double {
    val a = fg.luminance().toDouble()
    val b = bg.luminance().toDouble()
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}
