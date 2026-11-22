package app.recetario.share

/**
 * Formatos de la ficha (diseño de la etapa 2, §7.7). Las imágenes miden en píxeles y los PDF en
 * puntos (1/72 de pulgada); la maquetación usa la misma unidad que el formato. [baseBody] es el
 * tamaño de la letra del cuerpo a escala 1.
 */
enum class CardFormat(val width: Float, val height: Float, val isPdf: Boolean, val baseBody: Float) {
    SQUARE(1080f, 1080f, false, 28f),
    STORY(1080f, 1920f, false, 32f),
    LANDSCAPE(1500f, 1000f, false, 28f),
    PDF_A4(595f, 842f, true, 11f),
    PDF_LETTER(612f, 792f, true, 11f),

    /** Tarjeta de 10 × 15 cm en vertical. */
    PDF_CARD(283f, 425f, true, 8.5f),
    ;

    val extension: String get() = if (isPdf) "pdf" else "png"
}

enum class CardDesign { CLASSIC, MINIMAL, BIG_PHOTO, THEME }

/** Las últimas opciones de la ficha; se guardan como texto en `ajustes` ([encode]/[decode]). */
data class CardOptions(
    val format: CardFormat = CardFormat.SQUARE,
    val design: CardDesign = CardDesign.CLASSIC,
    val photo: Boolean = true,
    val steps: Boolean = true,
    val notes: Boolean = true,
    val flavor: Boolean = true,
) {
    /** «SQUARE|CLASSIC|1|1|0|1». */
    fun encode(): String =
        listOf(format.name, design.name, bit(photo), bit(steps), bit(notes), bit(flavor)).joinToString("|")

    companion object {
        private fun bit(b: Boolean) = if (b) "1" else "0"

        private fun flag(s: String): Boolean? = when (s) {
            "1" -> true
            "0" -> false
            else -> null
        }

        /** Un texto ilegible (o de una versión con otros valores) vuelve a las opciones de serie. */
        fun decode(text: String?): CardOptions {
            val p = text?.split('|') ?: return CardOptions()
            if (p.size != 6) return CardOptions()
            val format = CardFormat.entries.firstOrNull { it.name == p[0] } ?: return CardOptions()
            val design = CardDesign.entries.firstOrNull { it.name == p[1] } ?: return CardOptions()
            val flags = p.drop(2).map { flag(it) ?: return CardOptions() }
            return CardOptions(format, design, flags[0], flags[1], flags[2], flags[3])
        }
    }
}
