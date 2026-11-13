package app.recetario.home

import app.recetario.core.text.normalize
import app.recetario.recipes.model.RecipeSummary
import java.time.LocalTime

/** «Buenos días» de 6:00 a 11:59, «Buenas tardes» de 12:00 a 19:59 y «Buenas noches» el resto. */
fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 6..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

/** Las [limit] recetas modificadas más recientemente; a igual fecha, por orden alfabético. */
fun recentRecipes(all: List<RecipeSummary>, limit: Int = 6): List<RecipeSummary> =
    all.sortedWith(
        compareByDescending<RecipeSummary> { it.updatedAt }.thenBy { normalize(it.title) }.thenBy { it.id },
    ).take(limit)

/** Las favoritas, por orden alfabético sin distinguir tildes ni mayúsculas. */
fun favoriteRecipes(all: List<RecipeSummary>): List<RecipeSummary> =
    all.filter { it.favorite }.sortedWith(compareBy<RecipeSummary> { normalize(it.title) }.thenBy { it.id })
