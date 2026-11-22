package app.recetario.share

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import app.recetario.R
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.paletteFor
import java.util.concurrent.ConcurrentHashMap

/** Colores de una ficha (ARGB). El texto va siempre opaco: `ink` y `muted` llegan a 4,5:1 sobre `bg`. */
data class CardPalette(
    val bg: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
)

/**
 * Cómo se ve una ficha: el diseño y, para «Tema», el tema activo (los otros diseños no dependen del tema). Todas
 * las fuentes son las incluidas en la app (`res/font`).
 */
data class CardLook(val design: CardDesign, val theme: ThemeChoice = ThemeChoice.CALIDO) {
    val palette: CardPalette
        get() = when (design) {
            CardDesign.CLASSIC, CardDesign.BIG_PHOTO -> WARM
            CardDesign.MINIMAL -> PLAIN
            CardDesign.THEME -> paletteFor(theme).let { c ->
                CardPalette(c.bg.argb(), c.ink.argb(), c.muted.argb(), c.accent.argb(), c.onAccent.argb())
            }
        }

    /** La fuente de cada papel: serifa en el título de Clásico; las del tema en Tema. */
    @FontRes
    fun font(role: CardTextRole): Int {
        val bold = role.bold
        return when (design) {
            CardDesign.CLASSIC -> when (role) {
                CardTextRole.TITLE -> R.font.fraunces_semibold
                else -> if (bold) R.font.inter_semibold else R.font.inter_regular
            }
            CardDesign.MINIMAL -> if (bold) R.font.inter_semibold else R.font.inter_regular
            CardDesign.BIG_PHOTO -> when (role) {
                CardTextRole.TITLE -> R.font.playfair_display_semibold
                else -> if (bold) R.font.inter_semibold else R.font.inter_regular
            }
            CardDesign.THEME -> when (theme) {
                ThemeChoice.CALIDO -> when (role) {
                    CardTextRole.TITLE -> R.font.fraunces_semibold
                    else -> if (bold) R.font.inter_semibold else R.font.inter_regular
                }
                ThemeChoice.FRESCO -> when (role) {
                    CardTextRole.TITLE -> R.font.nunito_extrabold
                    else -> if (bold) R.font.nunito_bold else R.font.nunito_regular
                }
                ThemeChoice.NOCTURNO -> when (role) {
                    CardTextRole.TITLE -> R.font.playfair_display_semibold
                    else -> if (bold) R.font.inter_semibold else R.font.inter_regular
                }
            }
        }
    }

    private companion object {
        fun Color.argb() = toArgb()

        /** Los de Cálido: crema, tinta y terracota. */
        val WARM = CardPalette(
            bg = 0xFFFFFAF3.toInt(), ink = 0xFF2B2118.toInt(), muted = 0xFF706356.toInt(),
            accent = 0xFFA64932.toInt(), onAccent = 0xFFFFF8F0.toInt(),
        )

        /** Minimalista: blanco, casi negro y una sola línea de acento. */
        val PLAIN = CardPalette(
            bg = 0xFFFFFFFF.toInt(), ink = 0xFF1C1C1C.toInt(), muted = 0xFF5C5C5C.toInt(),
            accent = 0xFFA64932.toInt(), onAccent = 0xFFFFFFFF.toInt(),
        )
    }
}

/** Las fuentes de la app como `Typeface`, cargadas una vez (las usan la vista previa, la imagen y el PDF). */
class CardFonts(private val context: Context) {
    private val cache = ConcurrentHashMap<Int, Typeface>()

    fun get(@FontRes id: Int): Typeface = cache.getOrPut(id) {
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT
    }
}

/** Nombres que se leen en los chips y en TalkBack. */
val CardDesign.label: String
    get() = when (this) {
        CardDesign.CLASSIC -> "Clásico"
        CardDesign.MINIMAL -> "Minimalista"
        CardDesign.BIG_PHOTO -> "Foto grande"
        CardDesign.THEME -> "Tema"
    }

val CardFormat.label: String
    get() = when (this) {
        CardFormat.SQUARE -> "Cuadrada"
        CardFormat.STORY -> "Historia"
        CardFormat.LANDSCAPE -> "Horizontal"
        CardFormat.PDF_A4 -> "PDF A4"
        CardFormat.PDF_LETTER -> "PDF Carta"
        CardFormat.PDF_CARD -> "Tarjeta 10×15"
    }

val CardFormat.mimeType: String get() = if (isPdf) "application/pdf" else "image/png"
