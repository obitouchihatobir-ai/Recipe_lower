package app.recetario.recipes.list

import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.search.RecipeFilter
import app.recetario.recipes.search.Taste
import app.recetario.recipes.search.classifyTaste

/** «10 min», «1 h» o «1 h 30 min». */
fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/** Duración de un temporizador: «45 s», «20 min», «1 min 30 s» o «1 h 30 min». */
fun formatDurationSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it h" },
        m.takeIf { it > 0 }?.let { "$it min" },
        s.takeIf { it > 0 || seconds == 0 }?.let { "$it s" },
    ).joinToString(" ")
}

/** Sabor que define la receta: dulce o salada (§7 del spec) y, si no, el más fuerte de ácida, picante o amarga (≥ 2). */
fun tasteWord(flavor: FlavorProfile): String? = when (classifyTaste(flavor.sweet, flavor.salty)) {
    Taste.SWEET -> "dulce"
    Taste.SALTY -> "salada"
    null -> listOf("ácida" to flavor.sour, "picante" to flavor.spicy, "amarga" to flavor.bitter)
        .filter { it.second >= 2 }
        .maxByOrNull { it.second }
        ?.first
}

/** Línea de datos de una tarjeta: «Bebida · ácida · 10 min». */
fun recipeMeta(kindLabel: String?, flavor: FlavorProfile, totalMinutes: Int?): String =
    listOfNotNull(kindLabel, tasteWord(flavor), totalMinutes?.let(::formatMinutes)).joinToString(" · ")

/** Nombre de la unidad en el selector del editor. */
fun unitLabel(unit: MeasureUnit): String = when (unit) {
    MeasureUnit.PIECE -> "unidad"
    MeasureUnit.CUSTOM -> "Otra…"
    else -> unit.singular
}

fun resultsLabel(n: Int): String = if (n == 1) "1 receta" else "$n recetas"

/** Grupos de filtros de la hoja que están activos (no cuentan la búsqueda, las categorías ni el orden). */
fun RecipeFilter.sheetFilterCount(): Int =
    (if (tags.isNotEmpty()) 1 else 0) +
        listOf(taste, alcoholic, serveTemp, maxMinutes, moment, difficulty).count { it != null } +
        (if (favoritesOnly) 1 else 0)

/** Quita los filtros de la hoja y conserva la búsqueda, las categorías y el orden. */
fun RecipeFilter.withoutSheetFilters(): RecipeFilter =
    RecipeFilter(query = query, categoryIds = categoryIds, sort = sort)
