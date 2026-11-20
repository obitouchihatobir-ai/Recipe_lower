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

/**
 * Relleno de cada una de las 5 estrellas (1, 0,5 o 0) para una media redondeada a media estrella. Las estrellas
 * se dibujan (no se escriben con un glifo: el de media estrella falta en algunas fuentes de Android).
 */
fun starFills(average: Double): List<Float> {
    val half = roundToHalfStar(average.coerceIn(0.0, 5.0))
    return (1..5).map { n -> (half - (n - 1)).coerceIn(0.0, 1.0).toFloat() }
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

/** «La hiciste 3 veces · última hace 2 semanas» (o «Aún no la has hecho»); la media va aparte, con [starFills]. */
fun cookStatsLine(stats: CookStats?, today: LocalDate): String {
    if (stats == null || stats.count == 0 || stats.lastDate == null) return "Aún no la has hecho"
    val times = if (stats.count == 1) "1 vez" else "${stats.count} veces"
    val rel = relativeDate(stats.lastDate, today)
    val date = if (stats.count == 1) rel else "última $rel"
    return "La hiciste $times · $date"
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
