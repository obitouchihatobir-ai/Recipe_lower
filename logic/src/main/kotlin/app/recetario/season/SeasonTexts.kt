package app.recetario.season

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")

/** «noviembre». */
fun seasonMonthName(month: Int): String {
    require(month in 1..12) { "Mes fuera de rango: $month" }
    return Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, SPANISH).lowercase(SPANISH)
}

const val SEASON_TEASER_COUNT = 3

/**
 * Tarjeta de Inicio: «De temporada en noviembre: guayaba, mandarina, calabaza…». Primero lo que está en su mejor
 * momento y, si no llegan a tres, lo que también hay; «…» solo si quedan más.
 */
fun seasonTeaser(month: Int, calendar: List<Produce>): String {
    val (best, available) = SeasonCalendar.forMonth(month, calendar)
    val all = best + available
    val head = "De temporada en ${seasonMonthName(month)}"
    if (all.isEmpty()) return head
    val names = all.take(SEASON_TEASER_COUNT).joinToString(", ") { it.name.replaceFirstChar { c -> c.lowercase(SPANISH) } }
    return "$head: $names" + if (all.size > SEASON_TEASER_COUNT) "…" else ""
}
