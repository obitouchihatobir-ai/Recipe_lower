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
    /**
     * Ingredientes que usa el paso. Siempre derivado ([relinked]): los marcados a mano más los que sugiere
     * el texto actual, menos los sugeridos que la persona quitó.
     */
    val ingredientIds: Set<String> = emptySet(),
    /** Marcados a mano. Los borradores y recetas de antes solo traen [ingredientIds]: se cargan como manuales. */
    val manualIngredientIds: Set<String> = ingredientIds,
    /** Sugeridos que la persona quitó de este paso: no se vuelven a marcar solos. */
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

/** Ingredientes del texto del paso según sus nombres (§7: la app los sugiere comparando nombres). */
internal fun StepDraft.suggested(ingredients: List<IngredientDraft>): Set<String> = suggestStepIngredients(
    text,
    ingredients.filter { it.name.isNotBlank() }
        .map { Ingredient(id = it.id, section = null, name = it.name, quantity = null, unit = it.unit, customUnit = null, note = "") },
)

/**
 * Recalcula [StepDraft.ingredientIds] = manuales ∪ (sugeridos por el texto actual − quitados), solo con
 * ingredientes que existen. Se llama al cambiar el texto y al añadir, renombrar o quitar ingredientes,
 * así una palabra a medio escribir («Sal» al escribir «Saltea») no deja un enlace equivocado.
 */
internal fun StepDraft.relinked(ingredients: List<IngredientDraft>): StepDraft {
    val existing = ingredients.mapTo(HashSet()) { it.id }
    val effective = (manualIngredientIds + (suggested(ingredients) - dismissedIngredientIds)).filterTo(LinkedHashSet()) { it in existing }
    return if (effective == ingredientIds) this else copy(ingredientIds = effective)
}

internal fun EditorState.relinkedSteps(): EditorState = copy(steps = steps.map { it.relinked(ingredients) })

/**
 * La persona deja los chips del paso como [newIds]: encender un sugerido solo lo saca de los quitados;
 * encender otro lo marca a mano; apagar uno lo desmarca y, si es sugerido, lo apunta como quitado.
 */
internal fun StepDraft.toggled(newIds: Set<String>, ingredients: List<IngredientDraft>): StepDraft {
    val suggested = suggested(ingredients)
    var manual = manualIngredientIds
    var dismissed = dismissedIngredientIds
    for (id in newIds - ingredientIds) if (id in suggested) dismissed = dismissed - id else manual = manual + id
    for (id in ingredientIds - newIds) {
        manual = manual - id
        if (id in suggested) dismissed = dismissed + id
    }
    return copy(manualIngredientIds = manual, dismissedIngredientIds = dismissed).relinked(ingredients)
}

/**
 * Lo que de verdad es la receta, sin errores ni la contabilidad de sugerencias (manuales y quitados):
 * con eso se decide si hay cambios que merezcan un borrador.
 */
internal fun EditorState.content(): EditorState = withoutErrors().copy(
    steps = steps.map { it.copy(manualIngredientIds = emptySet(), dismissedIngredientIds = emptySet()) },
)

/** Lo necesario para deshacer la eliminación de un ingrediente o de un paso. */
sealed interface Removal

/** [manualInSteps] y [dismissedInSteps]: pasos donde estaba marcado a mano o quitado (se recuperan al deshacer). */
data class RemovedIngredient(
    val index: Int,
    val draft: IngredientDraft,
    val manualInSteps: Set<String>,
    val dismissedInSteps: Set<String>,
) : Removal

/** El paso se guarda entero: texto, fotos, temporizador e ingredientes. */
data class RemovedStep(val index: Int, val draft: StepDraft) : Removal

fun EditorState.removalOfIngredient(id: String): RemovedIngredient? {
    val index = ingredients.indexOfFirst { it.id == id }
    if (index < 0) return null
    return RemovedIngredient(
        index = index,
        draft = ingredients[index],
        manualInSteps = steps.filter { id in it.manualIngredientIds }.mapTo(LinkedHashSet()) { it.id },
        dismissedInSteps = steps.filter { id in it.dismissedIngredientIds }.mapTo(LinkedHashSet()) { it.id },
    )
}

fun EditorState.removalOfStep(id: String): RemovedStep? {
    val index = steps.indexOfFirst { it.id == id }
    return if (index < 0) null else RemovedStep(index, steps[index])
}

/** Vuelve a poner lo quitado en su posición; si ya está (deshacer dos veces), no cambia nada. */
fun EditorState.restored(removal: Removal): EditorState = when (removal) {
    is RemovedIngredient ->
        if (ingredients.any { it.id == removal.draft.id }) this
        else {
            val id = removal.draft.id
            copy(
                ingredients = ingredients.toMutableList().apply { add(removal.index.coerceIn(0, size), removal.draft) },
                steps = steps.map {
                    it.copy(
                        manualIngredientIds = if (it.id in removal.manualInSteps) it.manualIngredientIds + id else it.manualIngredientIds,
                        dismissedIngredientIds = if (it.id in removal.dismissedInSteps) it.dismissedIngredientIds + id else it.dismissedIngredientIds,
                    )
                },
            ).relinkedSteps()
        }
    is RemovedStep ->
        if (steps.any { it.id == removal.draft.id }) this
        else copy(steps = steps.toMutableList().apply { add(removal.index.coerceIn(0, size), removal.draft) }).relinkedSteps()
}
