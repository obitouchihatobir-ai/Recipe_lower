package app.recetario.pantry

import app.recetario.core.text.normalize
import app.recetario.recipes.model.RecipeSummary

val DEFAULT_PANTRY_BASICS = listOf("sal", "agua", "aceite", "pimienta", "azúcar")

private val nonWord = Regex("[^\\p{L}\\p{N}]+")

/** Palabras normalizadas; la puntuación («cebolla, picada», «tomate (maduro)») separa como un espacio. */
private fun words(text: String) = normalize(text).split(nonWord).filter { it.isNotEmpty() }

private fun sameWord(a: String, b: String) =
    a == b || a == b + "s" || a == b + "es" || b == a + "s" || b == a + "es"

private fun wordsIn(small: List<String>, big: List<String>) =
    small.isNotEmpty() && small.all { w -> big.any { sameWord(w, it) } }

/** Clave única de un artículo de la despensa: normalizado, sin tildes ni puntuación. */
fun pantryKey(name: String): String = words(name).joinToString(" ")

/** El mismo artículo, salvo tildes, mayúsculas, puntuación y plural básico («tomates» = «tomate»). */
fun samePantryName(a: String, b: String): Boolean {
    val wa = words(a)
    val wb = words(b)
    return wa.isNotEmpty() && wa.size == wb.size && wa.zip(wb).all { (x, y) -> sameWord(x, y) }
}

/**
 * Hasta [limit] ingredientes de las recetas que contienen lo escrito, sin repetir y sin los que ya están en la
 * despensa. Primero los que empiezan por lo escrito; dentro de cada grupo, por orden alfabético.
 */
fun pantrySuggestions(query: String, ingredientNames: List<String>, pantry: List<String>, limit: Int = 6): List<String> {
    val q = pantryKey(query)
    if (q.isEmpty()) return emptyList()
    val unique = mutableListOf<String>()
    for (raw in ingredientNames) {
        val name = raw.trim()
        if (pantryKey(name).isEmpty() || unique.any { samePantryName(it, name) }) continue
        if (pantry.any { samePantryName(it, name) }) continue
        unique += name
    }
    return unique.map { it to pantryKey(it) }
        .filter { (_, key) -> key.contains(q) }
        .sortedWith(compareBy<Pair<String, String>> { if (it.second.startsWith(q)) 0 else 1 }.thenBy { it.second })
        .take(limit)
        .map { it.first }
}

/** «Tienes 5 de 6 · falta: cilantro» (como mucho 3 faltas y «…»), o «¡Tienes todo!». */
fun coverageLine(c: PantryCoverage): String {
    if (c.missing.isEmpty()) return "¡Tienes todo!"
    val shown = c.missing.take(3).joinToString(", ") + if (c.missing.size > 3) "…" else ""
    return "Tienes ${c.have} de ${c.total} · falta: $shown"
}

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
