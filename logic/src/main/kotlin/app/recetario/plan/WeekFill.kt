package app.recetario.plan

import app.recetario.today.TodayRecipe
import java.time.LocalDate

/**
 * Propone un plato para cada hueco vacío de [week] desde [today] (los días pasados y los huecos ocupados no se tocan)
 * y devuelve solo los nuevos. A [planFor] le pasa los ids de recetas ya usadas en la semana (las de [existing] y las
 * que va proponiendo) para que no repita mientras haya otras; si devuelve null, el hueco queda vacío.
 */
fun fillWeek(
    week: List<LocalDate>,
    today: LocalDate,
    existing: List<PlannedMeal>,
    planFor: (LocalDate, MealSlot, Set<String>) -> TodayRecipe?,
    defaultServings: (String) -> Int,
    newId: () -> String,
): List<PlannedMeal> {
    val taken = existing.map { it.date to it.slot }.toSet()
    val used = existing.mapTo(linkedSetOf()) { it.recipeId }
    val added = mutableListOf<PlannedMeal>()
    for (date in week) {
        if (date < today) continue
        for (slot in MealSlot.entries) {
            if ((date to slot) in taken) continue
            val pick = planFor(date, slot, used.toSet()) ?: continue
            used += pick.id
            added += PlannedMeal(newId(), date, slot, pick.id, defaultServings(pick.id))
        }
    }
    return added
}
