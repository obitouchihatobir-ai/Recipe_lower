package app.recetario.recipes.quantity

import java.math.BigDecimal
import java.math.RoundingMode

/** Escala una cantidad de `baseServings` a `targetServings` raciones; `null` se queda en `null`. */
fun scaleQuantity(q: Double?, baseServings: Int, targetServings: Int): Double? {
    if (q == null) return null
    if (baseServings <= 0) return q
    return q * targetServings / baseServings
}

private val fractions = listOf(
    0.0 to "", 0.25 to "¼", 1.0 / 3 to "⅓", 0.5 to "½", 2.0 / 3 to "⅔", 0.75 to "¾", 1.0 to "",
)

/** Redondea a la fracción más cercana (¼ ⅓ ½ ⅔ ¾) sobre el entero; nunca da «0» para v > 0. */
fun formatFraction(v: Double): String = fractionParts(v).first

/** Texto de la fracción y el valor redondeado que representa. */
private fun fractionParts(v: Double): Pair<String, Double> {
    if (v <= 0.0) return "0" to 0.0
    var whole = Math.floor(v).toLong()
    val frac = v - whole
    val nearest = fractions.minByOrNull { Math.abs(it.first - frac) }!!
    var symbol = nearest.second
    var value = nearest.first
    if (value == 1.0) { whole += 1; value = 0.0; symbol = "" }
    if (whole == 0L && symbol.isEmpty()) { symbol = "¼"; value = 0.25 }
    val text = if (whole == 0L) symbol else "$whole$symbol"
    return text to (whole + value)
}

/** Número con coma decimal, redondeo half-up y sin ceros finales. */
private fun decimal(v: Double, decimals: Int): String {
    val s = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return s.replace('.', ',')
}

private fun metric(v: Double, small: String, big: String): String {
    val rounded = if (v < 10) BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toDouble()
    else BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP).toDouble()
    return if (rounded >= 1000.0) "${decimal(v / 1000, 2)} $big"
    else if (v < 10) "${decimal(v, 1)} $small"
    else "${decimal(v, 0)} $small"
}

fun formatQuantity(q: Double?, unit: MeasureUnit, customUnit: String?): String {
    when (unit) {
        MeasureUnit.PINCH -> return "una pizca"
        MeasureUnit.TO_TASTE -> return "al gusto"
        else -> Unit
    }
    if (q == null) return ""
    return when (unit) {
        MeasureUnit.GRAM -> metric(q, "g", "kg")
        MeasureUnit.KILOGRAM -> metric(q * 1000, "g", "kg")
        MeasureUnit.MILLILITER -> metric(q, "ml", "l")
        MeasureUnit.LITER -> metric(q * 1000, "ml", "l")
        MeasureUnit.OUNCE, MeasureUnit.POUND, MeasureUnit.FLUID_OUNCE ->
            "${decimal(q, 1)} ${unit.plural}"
        MeasureUnit.PIECE -> formatFraction(q)
        MeasureUnit.CUSTOM -> {
            val n = formatFraction(q)
            if (customUnit.isNullOrBlank()) n else "$n ${customUnit.trim()}"
        }
        else -> {
            val (text, rounded) = fractionParts(q)
            "$text ${if (rounded > 1.0) unit.plural else unit.singular}"
        }
    }
}
