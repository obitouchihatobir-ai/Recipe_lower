package app.recetario.diary

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")
private val MONTH_YEAR = DateTimeFormatter.ofPattern("LLLL 'de' yyyy", SPANISH)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH)
private val FULL_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)

/** Cabecera de un mes del diario: «noviembre de 2026». */
fun monthTitle(month: YearMonth): String = MONTH_YEAR.format(month).lowercase(SPANISH)

/**
 * Cronología del diario: los meses, del más reciente al más antiguo, y dentro de cada uno las entradas por fecha
 * y, el mismo día, la apuntada más tarde primero.
 */
fun groupByMonth(entries: List<CookLogEntry>): List<Pair<YearMonth, List<CookLogEntry>>> =
    entries
        .sortedWith(compareByDescending<CookLogEntry> { it.date }.thenByDescending { it.createdAt }.thenBy { it.id })
        .groupBy { YearMonth.from(it.date) }
        .toList()

/** Texto para TalkBack de una valoración: «4 de 5 estrellas» o «Sin valoración». */
fun ratingDescription(rating: Int?): String = if (rating == null) "Sin valoración" else "$rating de 5 estrellas"

/** Relleno de las 5 estrellas de una valoración entera (como [starFills]); vacío sin valoración. */
fun ratingFills(rating: Int?): List<Float> = if (rating == null) emptyList() else starFills(rating.toDouble())

/** [cookStatsLine] con la media en palabras («valoración media: 3,5 de 5 estrellas»), para TalkBack. */
fun cookStatsDescription(stats: CookStats?, today: LocalDate): String {
    val line = cookStatsLine(stats, today)
    val average = stats?.averageRating ?: return line
    val half = roundToHalfStar(average.coerceIn(0.0, 5.0))
    val number = if (half % 1.0 == 0.0) half.toInt().toString() else half.toString().replace('.', ',')
    return "$line · valoración media: $number de 5 estrellas"
}

/** Fecha de una entrada en el panel: «Hoy», «Ayer» o «5 de marzo de 2026». */
fun logDateLabel(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(date, today)) {
    0L -> "Hoy"
    1L -> "Ayer"
    else -> FULL_DATE.format(date)
}

/** Fecha de una entrada dentro de su mes: «3 de noviembre». */
fun entryDateLabel(date: LocalDate): String = DAY_MONTH.format(date)
