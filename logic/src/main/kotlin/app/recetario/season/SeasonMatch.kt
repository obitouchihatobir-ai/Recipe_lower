package app.recetario.season

import app.recetario.pantry.pantryKey
import app.recetario.pantry.wordsWithin
import app.recetario.recipes.model.RecipeSummary
import app.recetario.today.TodayRecipe

/** Nombre y sinónimos de un producto, como se buscan. */
private fun Produce.terms(): List<String> = (listOf(name) + synonyms).filter { pantryKey(it).isNotEmpty() }

/**
 * Productos del calendario que salen en un ingrediente: todas las palabras del nombre o de un sinónimo, completas
 * (con plural básico y sin tildes), en el ingrediente. Si dos coinciden y el término de uno contiene al del otro,
 * gana el más concreto: «tomate verde» es el tomate verde y no el jitomate («tomate»). Un ingrediente que contiene
 * una de las exclusiones del producto no cuenta para él («leche de coco» no es coco de temporada).
 */
private fun producesInIngredient(ingredient: String, calendar: List<Produce>): List<Produce> {
    if (pantryKey(ingredient).isEmpty()) return emptyList()
    val matches = calendar.mapNotNull { p ->
        if (p.excludes.any { wordsWithin(it, ingredient) }) return@mapNotNull null
        val term = p.terms().filter { wordsWithin(it, ingredient) }.maxByOrNull { pantryKey(it).split(' ').size }
        term?.let { p to it }
    }
    return matches.filter { (p, term) ->
        matches.none { (other, otherTerm) ->
            other !== p && pantryKey(otherTerm) != pantryKey(term) && wordsWithin(term, otherTerm)
        }
    }.map { it.first }
}

/** Ids de los productos del calendario que usan unos ingredientes. */
fun producesIn(ingredientNames: List<String>, calendar: List<Produce>): Set<String> =
    ingredientNames.flatMap { producesInIngredient(it, calendar) }.map { it.id }.toSet()

/** Cuántas recetas usan [produce] (comparando con todo [calendar], para que gane el producto más concreto). */
fun recipesUsing(produce: Produce, recipes: List<RecipeSummary>, calendar: List<Produce> = SeasonCalendar.load()): Int {
    val all = if (calendar.any { it.id == produce.id }) calendar else calendar + produce
    return recipes.count { produce.id in producesIn(it.ingredientNames, all) }
}

/**
 * Cuántas recetas usan cada producto (id → recetas; los que no usa ninguna no salen). Da lo mismo que [recipesUsing]
 * con cada producto, pero mira cada receta una sola vez.
 */
fun seasonRecipeCounts(recipes: List<RecipeSummary>, calendar: List<Produce>): Map<String, Int> =
    recipes.flatMap { producesIn(it.ingredientNames, calendar) }.groupingBy { it }.eachCount()

const val SEASON_BEST_BONUS = 4.0
const val SEASON_AVAILABLE_BONUS = 1.0
const val SEASON_MAX_BONUS = 8.0

/** Empujón de Hoy: +4 por cada producto en su mejor momento en [month] y +1 por cada uno que también hay; como mucho +8. */
fun inSeasonBonus(recipe: TodayRecipe, month: Int, calendar: List<Produce>): Double {
    val ids = producesIn(recipe.ingredientNames, calendar)
    val total = calendar.filter { it.id in ids }.sumOf {
        when (it.availabilityIn(month)) {
            Availability.BEST -> SEASON_BEST_BONUS
            Availability.AVAILABLE -> SEASON_AVAILABLE_BONUS
            Availability.OUT -> 0.0
        }
    }
    return minOf(total, SEASON_MAX_BONUS)
}
