package app.recetario.recipes.search

import app.recetario.core.text.normalize
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.model.ServeTemp

enum class Taste { SWEET, SALTY }

enum class SortOrder { RECENT, ALPHABETIC }

/** Dulce si sweet >= 2 y sweet >= salty; salada si salty >= 2 y salty > sweet; si no, null. */
fun classifyTaste(sweet: Int, salty: Int): Taste? = when {
    sweet >= 2 && sweet >= salty -> Taste.SWEET
    salty >= 2 && salty > sweet -> Taste.SALTY
    else -> null
}

data class RecipeFilter(
    val query: String = "",
    val categoryIds: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val taste: Taste? = null,
    val alcoholic: Boolean? = null,
    val serveTemp: ServeTemp? = null,
    val maxMinutes: Int? = null,
    val moment: Moment? = null,
    val difficulty: Difficulty? = null,
    val favoritesOnly: Boolean = false,
    val sort: SortOrder = SortOrder.RECENT,
)

fun List<RecipeSummary>.applyFilter(f: RecipeFilter): List<RecipeSummary> {
    val words = normalize(f.query).split(' ').filter { it.isNotEmpty() }
    val wantedTags = f.tags.map(::normalize).toSet()
    val matching = filter { r ->
        val normTitle = normalize(r.title)
        val normTags = r.tags.map(::normalize)
        val normIngredients = r.ingredientNames.map(::normalize)
        words.all { w ->
            normTitle.contains(w) || normIngredients.any { it.contains(w) } || normTags.any { it.contains(w) }
        } &&
            (f.categoryIds.isEmpty() || r.categoryId in f.categoryIds) &&
            (wantedTags.isEmpty() || normTags.any { it in wantedTags }) &&
            (f.taste == null || classifyTaste(r.flavor.sweet, r.flavor.salty) == f.taste) &&
            (f.alcoholic == null || r.alcoholic == f.alcoholic) &&
            (f.serveTemp == null || r.serveTemp == f.serveTemp) &&
            (f.maxMinutes == null || (r.totalMinutes != null && r.totalMinutes <= f.maxMinutes)) &&
            (f.moment == null || f.moment in r.moments) &&
            (f.difficulty == null || r.difficulty == f.difficulty) &&
            (!f.favoritesOnly || r.favorite)
    }
    return when (f.sort) {
        SortOrder.RECENT -> matching.sortedWith(
            compareByDescending<RecipeSummary> { it.updatedAt }.thenBy { normalize(it.title) },
        )
        SortOrder.ALPHABETIC -> matching.sortedWith(compareBy<RecipeSummary> { normalize(it.title) }.thenBy { it.id })
    }
}
