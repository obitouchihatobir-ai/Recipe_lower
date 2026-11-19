package app.recetario.diary

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CookStats(val count: Int, val lastDate: LocalDate?, val averageRating: Double?)

/** Estadísticas por receta; ignora las entradas sin receta y la media solo cuenta las valoradas. */
fun statsByRecipe(entries: List<CookLogEntry>): Map<String, CookStats> =
    entries.filter { it.recipeId != null }.groupBy { it.recipeId!! }.mapValues { (_, list) ->
        val ratings = list.mapNotNull { it.rating }
        CookStats(
            count = list.size,
            lastDate = list.maxOf { it.date },
            averageRating = if (ratings.isEmpty()) null else ratings.average(),
        )
    }

fun roundToHalfStar(avg: Double): Double = Math.round(avg * 2) / 2.0

private fun starsText(avg: Double): String {
    val half = roundToHalfStar(avg.coerceIn(0.0, 5.0))
    val full = half.toInt()
    val hasHalf = half - full >= 0.5
    return "★".repeat(full) + (if (hasHalf) "⯪" else "") + "☆".repeat(5 - full - if (hasHalf) 1 else 0)
}

internal fun relativeDate(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today).toInt()
    return when {
        days <= 0 -> "hoy"
        days == 1 -> "ayer"
        days < 14 -> "hace $days días"
        days < 63 -> "hace ${days / 7} semanas"
        days < 365 -> "hace ${(days / 30).coerceIn(2, 11)} meses"
        else -> "hace más de un año"
    }
}

fun cookStatsLine(stats: CookStats?, today: LocalDate): String {
    if (stats == null || stats.count == 0 || stats.lastDate == null) return "Aún no la has hecho"
    val times = if (stats.count == 1) "1 vez" else "${stats.count} veces"
    val rel = relativeDate(stats.lastDate, today)
    val date = if (stats.count == 1) rel else "última $rel"
    val base = "La hiciste $times · $date"
    return if (stats.averageRating == null) base else "$base · ${starsText(stats.averageRating)}"
}

/** Ids de recetas hechas, cuya última vez fue hace al menos `minDays`; la más antigua primero. */
fun longTimeNoCook(
    stats: Map<String, CookStats>,
    today: LocalDate,
    minDays: Int = 30,
    limit: Int = 6,
): List<String> = stats.entries
    .filter { (_, s) -> s.lastDate != null && ChronoUnit.DAYS.between(s.lastDate, today) >= minDays }
    .sortedWith(compareBy({ it.value.lastDate }, { it.key }))
    .take(limit)
    .map { it.key }
