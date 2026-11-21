package app.recetario.plan

import app.recetario.pantry.pantryCovers
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.scaleQuantity
import app.recetario.shopping.SectionClassifier
import app.recetario.shopping.ShoppingItem
import app.recetario.shopping.mergeInto
import java.time.Instant
import java.time.LocalDate

/**
 * Los ingredientes de los platos desde [today], escalados a sus porciones y sumados, sin lo que cubren la despensa
 * ([pantry]) y los básicos ([basics]). Cada línea tiene la forma de las de «Lo que necesito» (el mismo escalado y
 * la misma sección), lista para `mergeInto` con la compra; el id y la fecha los pone quien las guarda.
 */
fun weekIngredients(
    meals: List<PlannedMeal>,
    today: LocalDate,
    recipes: Map<String, Recipe>,
    pantry: List<String>,
    basics: List<String>,
): List<ShoppingItem> {
    val have = pantry + basics
    val incoming = meals.filter { it.date >= today }.flatMap { meal ->
        val recipe = recipes[meal.recipeId] ?: return@flatMap emptyList()
        recipe.ingredients
            .filter { ing -> have.none { pantryCovers(it, ing.name) } }
            .map { ing ->
                ShoppingItem(
                    id = "${meal.id}:${ing.id}",
                    name = ing.name.trim(),
                    quantity = scaleQuantity(ing.quantity, recipe.servings, meal.servings),
                    unit = ing.unit,
                    customUnit = ing.customUnit,
                    section = SectionClassifier.classify(ing.name),
                    checked = false,
                    sourceRecipeId = recipe.id,
                    createdAt = Instant.EPOCH,
                )
            }
    }
    return mergeInto(emptyList(), incoming)
}
