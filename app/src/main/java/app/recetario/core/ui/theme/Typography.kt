package app.recetario.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.recetario.R

private val Fraunces = FontFamily(Font(R.font.fraunces_semibold, FontWeight.W600))

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_medium, FontWeight.W500),
    Font(R.font.inter_semibold, FontWeight.W600),
)

private val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.W400),
    Font(R.font.nunito_semibold, FontWeight.W600),
    Font(R.font.nunito_bold, FontWeight.W700),
    Font(R.font.nunito_extrabold, FontWeight.W800),
)

private val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_medium, FontWeight.W500),
    Font(R.font.playfair_display_semibold, FontWeight.W600),
)

/** Títulos con la fuente y el peso de §6.1 y cuerpo con Inter o Nunito, según el tema. */
fun typographyFor(choice: ThemeChoice): Typography {
    val (head, headWeight, headTracking, body) = when (choice) {
        ThemeChoice.CALIDO -> TypeSpec(Fraunces, FontWeight.W600, -0.01f, Inter)
        ThemeChoice.FRESCO -> TypeSpec(Nunito, FontWeight.W800, -0.015f, Nunito)
        ThemeChoice.NOCTURNO -> TypeSpec(PlayfairDisplay, FontWeight.W600, 0f, Inter)
    }
    fun title(size: Int, line: Int) = TextStyle(
        fontFamily = head,
        fontWeight = headWeight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = headTracking.em,
    )
    fun text(size: Int, line: Int, weight: FontWeight = FontWeight.W400) = TextStyle(
        fontFamily = body,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
    )
    return Typography(
        displayLarge = title(48, 56),
        displayMedium = title(40, 48),
        displaySmall = title(34, 42),
        headlineLarge = title(30, 38),
        headlineMedium = title(26, 34),
        headlineSmall = title(22, 30),
        titleLarge = title(20, 28),
        titleMedium = text(16, 24, FontWeight.W600),
        titleSmall = text(14, 20, FontWeight.W600),
        bodyLarge = text(16, 24),
        bodyMedium = text(14, 20),
        bodySmall = text(12, 16),
        labelLarge = text(14, 20, FontWeight.W600),
        labelMedium = text(12, 16, FontWeight.W600),
        labelSmall = text(11, 16, FontWeight.W600),
    )
}

private data class TypeSpec(
    val head: FontFamily,
    val headWeight: FontWeight,
    val headTracking: Float,
    val body: FontFamily,
)
