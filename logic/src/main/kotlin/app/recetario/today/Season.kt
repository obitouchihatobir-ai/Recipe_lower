package app.recetario.today

import java.time.LocalDate
import java.time.Month
import kotlin.math.abs

enum class Season(val label: String) {
    DRY("secas"),
    RAINY("lluvias"),
    SPRING("primavera"),
    SUMMER("verano"),
    AUTUMN("otoño"),
    WINTER("invierno"),
}

private const val TROPIC_LATITUDE = 23.5

/**
 * Sin ubicación o en el trópico (|lat| < 23,5): secas y lluvias (norte: secas de noviembre a mayo,
 * lluvias de junio a octubre; sur, al revés). Fuera: estaciones meteorológicas del hemisferio.
 * Sin ubicación se supone el trópico norte.
 */
fun seasonFor(date: LocalDate, latitude: Double?): Season {
    val north = latitude == null || latitude >= 0
    if (latitude == null || abs(latitude) < TROPIC_LATITUDE) {
        val rainyMonths = date.monthValue in 6..10
        return if (rainyMonths == north) Season.RAINY else Season.DRY
    }
    val northern = when (date.month) {
        Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.WINTER
        Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
        Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
        else -> Season.AUTUMN
    }
    return if (north) northern else when (northern) {
        Season.WINTER -> Season.SUMMER
        Season.SUMMER -> Season.WINTER
        Season.SPRING -> Season.AUTUMN
        else -> Season.SPRING
    }
}
