package app.recetario.plan

import app.recetario.diary.entryDateLabel
import app.recetario.recipes.model.Moment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Los huecos del menú de cada día; [key] es el valor de `MEAL_SLOTS` que va a la base de datos y a las copias. */
enum class MealSlot(val key: String, val moment: Moment) {
    BREAKFAST("DESAYUNO", Moment.BREAKFAST),
    LUNCH("COMIDA", Moment.LUNCH),
    DINNER("CENA", Moment.DINNER),
}

/** Un plato del menú: una receta en un día y un hueco, con sus porciones. */
data class PlannedMeal(
    val id: String,
    val date: LocalDate,
    val slot: MealSlot,
    val recipeId: String,
    val servings: Int,
)

/** Los siete días de la semana de [date], del lunes al domingo. */
fun weekOf(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map { monday.plusDays(it) }
}

/** «Esta semana», «Semana que viene», «Semana pasada» o «Semana del 24 de noviembre». */
fun weekTitle(monday: LocalDate, today: LocalDate): String {
    val thisMonday = weekOf(today).first()
    return when (monday) {
        thisMonday -> "Esta semana"
        thisMonday.plusWeeks(1) -> "Semana que viene"
        thisMonday.minusWeeks(1) -> "Semana pasada"
        else -> "Semana del ${entryDateLabel(monday)}"
    }
}
