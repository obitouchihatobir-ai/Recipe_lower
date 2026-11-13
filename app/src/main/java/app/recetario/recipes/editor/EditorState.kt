package app.recetario.recipes.editor

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import kotlinx.serialization.Serializable

enum class Field { TITLE, CATEGORY, MINUTES }

/** Claves de `EditorState.rowErrors`: cada error cuelga de una fila concreta. */
fun ingredientNameErrorKey(ingredientId: String) = "ingredient-name:$ingredientId"
fun ingredientQuantityErrorKey(ingredientId: String) = "ingredient-quantity:$ingredientId"
fun stepTextErrorKey(stepId: String) = "step-text:$stepId"

@Serializable
data class IngredientDraft(
    val id: String,
    val section: String = "",
    val name: String = "",
    /** Lo que escribió la persona («1,5», «1/2», «½»…); se interpreta con parseQuantity al guardar. */
    val quantityText: String = "",
    val unit: MeasureUnit = MeasureUnit.GRAM,
    val customUnit: String = "",
    val note: String = "",
)

@Serializable
data class StepDraft(
    val id: String,
    val text: String = "",
    val photos: List<String> = emptyList(),
    val timerSeconds: Int? = null,
    val ingredientIds: Set<String> = emptySet(),
    /** Ingredientes sugeridos que la persona quitó de este paso: no se vuelven a marcar solos. */
    val dismissedIngredientIds: Set<String> = emptySet(),
)

/** Todos los campos editables de una receta, como texto o valor simple, más los errores de validación. */
@Serializable
data class EditorState(
    val recipeId: String,
    /** Marca de creación (milisegundos) de una receta existente; null si es nueva. */
    val createdAtMillis: Long? = null,
    val title: String = "",
    val categoryId: String? = null,
    val coverPhoto: String? = null,
    val description: String = "",
    val cookingMinutesText: String = "",
    val servings: Int = 4,
    val difficulty: Difficulty = Difficulty.EASY,
    val sweet: Int = 0,
    val salty: Int = 0,
    val sour: Int = 0,
    val bitter: Int = 0,
    val spicy: Int = 0,
    val intensity: Intensity = Intensity.MEDIUM,
    val rich: Boolean = false,
    val serveTemp: ServeTemp? = null,
    val moments: Set<Moment> = emptySet(),
    /** Momentos y temperatura vienen de la categoría y la persona aún no los ha tocado. */
    val defaultsSuggested: Boolean = false,
    val alcoholic: Boolean? = null,
    val course: Course? = null,
    val ingredients: List<IngredientDraft> = emptyList(),
    val steps: List<StepDraft> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val favorite: Boolean = false,
    val sourceUrl: String = "",
    val aiGenerated: Boolean = false,
    val errors: Map<Field, String> = emptyMap(),
    /** Errores por fila, con las claves de [ingredientNameErrorKey], [ingredientQuantityErrorKey] y [stepTextErrorKey]. */
    val rowErrors: Map<String, String> = emptyMap(),
) {
    fun withoutErrors() = copy(errors = emptyMap(), rowErrors = emptyMap())

    fun withoutRowErrorsOf(rowId: String) = copy(rowErrors = rowErrors.filterKeys { !it.endsWith(":$rowId") })

    val flavor: FlavorProfile
        get() = FlavorProfile(sweet, salty, sour, bitter, spicy, intensity, rich)

    fun withFlavor(f: FlavorProfile) = copy(
        sweet = f.sweet, salty = f.salty, sour = f.sour, bitter = f.bitter, spicy = f.spicy,
        intensity = f.intensity, rich = f.rich,
    )

    /** Fotos que este estado referencia (portada y pasos). */
    val photoFiles: Set<String>
        get() = (listOfNotNull(coverPhoto) + steps.flatMap { it.photos }).toSet()
}

/**
 * Cambia el texto del paso y marca los ingredientes que el texto nuevo menciona y el anterior no
 * (§7: la app los sugiere comparando nombres), salvo los que la persona quitó de este paso.
 */
internal fun StepDraft.withText(newText: String, ingredients: List<IngredientDraft>): StepDraft {
    val named = ingredients.filter { it.name.isNotBlank() }
        .map { Ingredient(id = it.id, section = null, name = it.name, quantity = null, unit = it.unit, customUnit = null, note = "") }
    val fresh = suggestStepIngredients(newText, named) - suggestStepIngredients(text, named) - dismissedIngredientIds - ingredientIds
    return copy(text = newText, ingredientIds = if (fresh.isEmpty()) ingredientIds else ingredientIds + fresh)
}

/** Lo necesario para deshacer la eliminación de un ingrediente o de un paso. */
sealed interface Removal

/** [linkedStepIds]: pasos que usaban el ingrediente (se vuelven a enlazar al deshacer). */
data class RemovedIngredient(val index: Int, val draft: IngredientDraft, val linkedStepIds: Set<String>) : Removal

/** El paso se guarda entero: texto, fotos, temporizador e ingredientes. */
data class RemovedStep(val index: Int, val draft: StepDraft) : Removal

fun EditorState.removalOfIngredient(id: String): RemovedIngredient? {
    val index = ingredients.indexOfFirst { it.id == id }
    if (index < 0) return null
    return RemovedIngredient(index, ingredients[index], steps.filter { id in it.ingredientIds }.mapTo(LinkedHashSet()) { it.id })
}

fun EditorState.removalOfStep(id: String): RemovedStep? {
    val index = steps.indexOfFirst { it.id == id }
    return if (index < 0) null else RemovedStep(index, steps[index])
}

/** Vuelve a poner lo quitado en su posición; si ya está (deshacer dos veces), no cambia nada. */
fun EditorState.restored(removal: Removal): EditorState = when (removal) {
    is RemovedIngredient ->
        if (ingredients.any { it.id == removal.draft.id }) this
        else copy(
            ingredients = ingredients.toMutableList().apply { add(removal.index.coerceIn(0, size), removal.draft) },
            steps = steps.map { if (it.id in removal.linkedStepIds) it.copy(ingredientIds = it.ingredientIds + removal.draft.id) else it },
        )
    is RemovedStep ->
        if (steps.any { it.id == removal.draft.id }) this
        else copy(steps = steps.toMutableList().apply { add(removal.index.coerceIn(0, size), removal.draft) })
}
