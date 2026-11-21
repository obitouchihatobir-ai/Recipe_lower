package app.recetario.recipes.detail

import app.recetario.recipes.list.formatMinutes
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.scaleQuantity

/** Texto de la receta para compartir (WhatsApp, etc.), con las cantidades escaladas a [servings]. */
fun formatRecipeAsText(recipe: Recipe, servings: Int, categoryEmoji: String): String {
    val meta = shareMetaLine(recipe, servings, clock = true)
    return buildString {
        append(categoryEmoji).append(' ').append(recipe.title).append('\n')
        append(meta).append("\n\n")
        append("Ingredientes\n")
        for (i in recipe.ingredients) append("• ").append(shareIngredientLine(i, recipe.servings, servings)).append('\n')
        append("\nPasos\n")
        recipe.steps.forEachIndexed { n, s -> append(n + 1).append(". ").append(s.text).append('\n') }
        append("\nCompartido desde Sazón")
    }
}


/** «⏱ 35 min · 4 porciones · Fácil» (sin «⏱» si [clock] es false; sin tiempo si no se apuntó). */
fun shareMetaLine(recipe: Recipe, servings: Int, clock: Boolean): String = listOfNotNull(
    recipe.totalMinutes?.let { (if (clock) "⏱ " else "") + formatMinutes(it) },
    if (servings == 1) "1 porción" else "$servings porciones",
    recipe.difficulty.label,
).joinToString(" · ")

/** «Calabaza — 1,2 kg»: la cantidad escalada de [baseServings] a [servings]; sin cantidad, solo el nombre. */
fun shareIngredientLine(i: Ingredient, baseServings: Int, servings: Int): String {
    val q = formatQuantity(scaleQuantity(i.quantity, baseServings, servings), i.unit, i.customUnit)
    return if (q.isEmpty()) i.name else "${i.name} — $q"
}
