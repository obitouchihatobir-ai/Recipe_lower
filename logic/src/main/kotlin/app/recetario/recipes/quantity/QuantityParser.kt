package app.recetario.recipes.quantity

private val unicodeFractions = mapOf('¼' to 0.25, '½' to 0.5, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

private val decimalForm = Regex("""(\d*)[.,](\d+)""")
private val wholeForm = Regex("""\d+""")
private val fractionForm = Regex("""(\d+)/(\d+)""")
private val mixedForm = Regex("""(\d+)\s+(\d+)/(\d+)""")
private val unicodeForm = Regex("""(\d*)\s*([¼½¾⅓⅔])""")

/**
 * Entiende cantidades escritas a la española: «1,5», «1.5», «1/2», «1 1/2», «½», «1½».
 * Devuelve null si el texto está vacío, es negativo o no se entiende.
 */
fun parseQuantity(text: String): Double? {
    val t = text.trim()
    if (t.isEmpty()) return null
    mixedForm.matchEntire(t)?.let { m ->
        val den = m.groupValues[3].toDouble()
        if (den == 0.0) return null
        return m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / den
    }
    fractionForm.matchEntire(t)?.let { m ->
        val den = m.groupValues[2].toDouble()
        if (den == 0.0) return null
        return m.groupValues[1].toDouble() / den
    }
    unicodeForm.matchEntire(t)?.let { m ->
        val whole = m.groupValues[1].ifEmpty { "0" }.toDouble()
        return whole + unicodeFractions.getValue(m.groupValues[2][0])
    }
    decimalForm.matchEntire(t)?.let { m ->
        return "${m.groupValues[1].ifEmpty { "0" }}.${m.groupValues[2]}".toDouble()
    }
    if (wholeForm.matches(t)) return t.toDouble()
    return null
}
