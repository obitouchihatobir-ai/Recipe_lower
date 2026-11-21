package app.recetario.today

import app.recetario.recipes.model.Moment
import java.time.LocalTime

/** De [start] incluida a [end] excluida. */
data class TimeRange(val start: LocalTime, val end: LocalTime) {
    operator fun contains(t: LocalTime): Boolean = t >= start && t < end
}

/** Lo que queda fuera de las cuatro franjas es noche. */
data class MealTimes(
    val breakfast: TimeRange = TimeRange(LocalTime.of(7, 0), LocalTime.of(11, 0)),
    val lunch: TimeRange = TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0)),
    val snack: TimeRange = TimeRange(LocalTime.of(16, 0), LocalTime.of(19, 0)),
    val dinner: TimeRange = TimeRange(LocalTime.of(19, 0), LocalTime.of(23, 0)),
)

/** Entre el fin del desayuno y el inicio de la comida cuenta como comida (decisión del plan 2B). */
fun MealTimes.momentAt(time: LocalTime): Moment = when {
    time in breakfast -> Moment.BREAKFAST
    time in lunch -> Moment.LUNCH
    time in snack -> Moment.SNACK
    time in dinner -> Moment.DINNER
    time >= breakfast.end && time < lunch.start -> Moment.LUNCH
    else -> Moment.NIGHT
}
