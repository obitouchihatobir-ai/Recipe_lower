package app.recetario.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class RecetarioColors(
    val bg: Color,
    val surface: Color,
    val soft: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val todayStops: List<Color>,
    val todayBrush: Brush,
    val todayInk: Color,
    val todayBorder: Color?,
    /** Fondo de cada receta de la tarjeta Hoy y de su icono (en Cálido, más oscuros: el crema sobre un terracota más claro no llega a 4,5:1). */
    val todayItem: Color,
    val todayIcon: Color,
    /** Botón principal de la tarjeta Hoy («Empezar») y la línea del secundario («Otra idea»). */
    val todayButton: Color,
    val todayButtonInk: Color,
    val todayGhostLine: Color,
    val heroBrush: Brush,
)

@Immutable
data class RecetarioShapes(val radius: Dp)

// Valores de §6.1 del spec; `muted` y `accent` de Cálido y Fresco están oscurecidos
// (mismo tono) para llegar a 4,5:1 de contraste.
private val Calido = RecetarioColors(
    bg = Color(0xFFF6EFE6),
    surface = Color(0xFFFFFAF3),
    soft = Color(0xFFF1E1D2),
    ink = Color(0xFF2B2118),
    muted = Color(0xFF706356),
    line = Color(0xFFE8DACB),
    accent = Color(0xFFA64932),
    onAccent = Color(0xFFFFF8F0),
    todayStops = listOf(Color(0xFFA64932), Color(0xFFB5562E)),
    todayBrush = Brush.linearGradient(listOf(Color(0xFFA64932), Color(0xFFB5562E))),
    todayInk = Color(0xFFFFF8F0),
    todayBorder = null,
    todayItem = Color(0x1F000000), // negro al 12 %
    todayIcon = Color(0x14FFF8F0),
    todayButton = Color(0xFFFFF8F0),
    todayButtonInk = Color(0xFFA64932),
    todayGhostLine = Color(0x80FFF8F0),
    heroBrush = Brush.linearGradient(listOf(Color(0xFFF6C58F), Color(0xFFE58A4E))),
)

private val Fresco = RecetarioColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF3F7F2),
    soft = Color(0xFFE8F2E7),
    ink = Color(0xFF1E2A22),
    muted = Color(0xFF647068),
    line = Color(0xFFE2EAE2),
    accent = Color(0xFF447850),
    onAccent = Color(0xFFFFFFFF),
    todayStops = listOf(Color(0xFFEAF3E9)),
    todayBrush = SolidColor(Color(0xFFEAF3E9)),
    todayInk = Color(0xFF1E2A22),
    todayBorder = null,
    todayItem = Color(0xFFFFFFFF),
    todayIcon = Color(0xFFEAF3E9),
    todayButton = Color(0xFF447850),
    todayButtonInk = Color(0xFFFFFFFF),
    todayGhostLine = Color(0xFFBCD3BE),
    heroBrush = Brush.linearGradient(listOf(Color(0xFFFFE3B8), Color(0xFFF9C08A))),
)

private val Nocturno = RecetarioColors(
    bg = Color(0xFF141416),
    surface = Color(0xFF1E1E22),
    soft = Color(0xFF2A2620),
    ink = Color(0xFFF2EDE4),
    muted = Color(0xFF9A948A),
    line = Color(0xFF2C2C31),
    accent = Color(0xFFE0A458),
    onAccent = Color(0xFF1A1408),
    todayStops = listOf(Color(0xFF2A241B), Color(0xFF1C1C20)),
    todayBrush = Brush.linearGradient(listOf(Color(0xFF2A241B), Color(0xFF1C1C20))),
    todayInk = Color(0xFFF2EDE4),
    todayBorder = Color(0x59E0A458), // rgba(224,164,88,.35)
    todayItem = Color(0x0AFFFFFF), // blanco al 4 %
    todayIcon = Color(0x24E0A458),
    todayButton = Color(0xFFE0A458),
    todayButtonInk = Color(0xFF1A1408),
    todayGhostLine = Color(0x40F2EDE4),
    heroBrush = Brush.linearGradient(listOf(Color(0xFF4A3420), Color(0xFF2A2018))),
)

fun paletteFor(choice: ThemeChoice): RecetarioColors = when (choice) {
    ThemeChoice.CALIDO -> Calido
    ThemeChoice.FRESCO -> Fresco
    ThemeChoice.NOCTURNO -> Nocturno
}

fun shapesFor(choice: ThemeChoice): RecetarioShapes = when (choice) {
    ThemeChoice.CALIDO -> RecetarioShapes(18.dp)
    ThemeChoice.FRESCO -> RecetarioShapes(24.dp)
    ThemeChoice.NOCTURNO -> RecetarioShapes(16.dp)
}

val LocalRecetarioColors = staticCompositionLocalOf { Calido }
val LocalRecetarioShapes = staticCompositionLocalOf { RecetarioShapes(18.dp) }
