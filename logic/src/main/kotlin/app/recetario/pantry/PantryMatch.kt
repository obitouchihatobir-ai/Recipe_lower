package app.recetario.pantry

import app.recetario.core.text.normalize
import app.recetario.recipes.model.RecipeSummary

val DEFAULT_PANTRY_BASICS = listOf("sal", "agua", "aceite", "pimienta", "azúcar")

private fun words(text: String) = normalize(text).split(' ').filter { it.isNotEmpty() }

private fun sameWord(a: String, b: String) =
    a == b || a == b + "s" || a == b + "es" || b == a + "s" || b == a + "es"

private fun wordsIn(small: List<String>, big: List<String>) =
    small.isNotEmpty() && small.all { w -> big.any { sameWord(w, it) } }

/** Coincide si todas las palabras de uno están, completas (con plural básico), en el otro. */
fun pantryCovers(pantryName: String, ingredientName: String): Boolean {
    val p = words(pantryName)
    val i = words(ingredientName)
    return wordsIn(p, i) || wordsIn(i, p)
}

data class PantryCoverage(val recipeId: String, val have: Int, val total: Int, val missing: List<String>)

/** Ingredientes no básicos de la receta que la despensa no cubre; null si no tiene ningún no básico. */
internal fun missingIngredients(recipe: RecipeSummary, pantry: List<String>, basics: List<String>): Pair<Int, List<String>>? {
    val nonBasic = recipe.ingredientNames.filter { it.isNotBlank() && basics.none { b -> pantryCovers(b, it) } }
    if (nonBasic.isEmpty()) return null
    return nonBasic.size to nonBasic.filter { n -> pantry.none { pantryCovers(it, n) } }
}

fun pantryCoverage(recipes: List<RecipeSummary>, pantry: List<String>, basics: List<String>): List<PantryCoverage> =
    recipes.mapNotNull { r ->
        val (total, missing) = missingIngredients(r, pantry, basics) ?: return@mapNotNull null
        r to PantryCoverage(r.id, total - missing.size, total, missing)
    }.sortedWith(
        compareByDescending<Pair<RecipeSummary, PantryCoverage>> { it.second.have.toDouble() / it.second.total }
            .thenBy { normalize(it.first.title) }
            .thenBy { it.first.id },
    ).map { it.second }
