package app.recetario.recipes.detail

import app.recetario.recipes.list.formatMinutes
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.scaleQuantity

/** Texto de la receta para compartir (WhatsApp, etc.), con las cantidades escaladas a [servings]. */
fun formatRecipeAsText(recipe: Recipe, servings: Int, categoryEmoji: String): String {
    val meta = listOfNotNull(
        recipe.totalMinutes?.let { "⏱ ${formatMinutes(it)}" },
        if (servings == 1) "1 porción" else "$servings porciones",
        recipe.difficulty.label,
    ).joinToString(" · ")
    return buildString {
        append(categoryEmoji).append(' ').append(recipe.title).append('\n')
        append(meta).append("\n\n")
        append("Ingredientes\n")
        for (i in recipe.ingredients) {
            val q = formatQuantity(scaleQuantity(i.quantity, recipe.servings, servings), i.unit, i.customUnit)
            append("• ").append(i.name)
            if (q.isNotEmpty()) append(" — ").append(q)
            append('\n')
        }
        append("\nPasos\n")
        recipe.steps.forEachIndexed { n, s -> append(n + 1).append(". ").append(s.text).append('\n') }
        append("\nCompartido desde mi Recetario")
    }
}
