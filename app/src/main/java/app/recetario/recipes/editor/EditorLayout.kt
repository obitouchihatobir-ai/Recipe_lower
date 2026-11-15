package app.recetario.recipes.editor

/** Secciones plegables del editor (§8.1). */
enum class EditorSection(val title: String) {
    BASICS("Lo básico"),
    FLAVOR("Sabor"),
    INGREDIENTS("Ingredientes"),
    STEPS("Pasos"),
    NOTES("Notas"),
}

internal const val INGREDIENT_PREFIX = "ing:"
internal const val STEP_PREFIX = "step:"

/** Cada elemento de la lista perezosa del editor, con su clave estable. */
internal sealed interface EditorRow {
    val key: String
    val contentType: String get() = this::class.simpleName.orEmpty()

    data object Cover : EditorRow {
        override val key = "cover"
    }

    data class Header(val section: EditorSection, val count: Int?, val expanded: Boolean) : EditorRow {
        override val key = "header-${section.name}"
    }

    data object Basics : EditorRow {
        override val key = "basics"
    }

    data object Flavor : EditorRow {
        override val key = "flavor"
    }

    data object Notes : EditorRow {
        override val key = "notes"
    }

    /** [sectionTitle]: la sección («Para la salsa») empieza en este ingrediente. */
    data class IngredientItem(val index: Int, val draft: IngredientDraft, val sectionTitle: String?) : EditorRow {
        override val key = INGREDIENT_PREFIX + draft.id
    }

    data object AddIngredient : EditorRow {
        override val key = "add-ingredient"
    }

    data class StepItem(val index: Int, val draft: StepDraft) : EditorRow {
        override val key = STEP_PREFIX + draft.id
    }

    data object AddStep : EditorRow {
        override val key = "add-step"
    }
}

/** Filas del editor en orden; las secciones plegadas solo muestran su cabecera. */
internal fun editorRows(state: EditorState, collapsed: Set<EditorSection>): List<EditorRow> = buildList {
    add(EditorRow.Cover)
    for (section in EditorSection.entries) {
        val expanded = section !in collapsed
        val count = when (section) {
            EditorSection.INGREDIENTS -> state.ingredients.size
            EditorSection.STEPS -> state.steps.size
            else -> null
        }
        add(EditorRow.Header(section, count, expanded))
        if (!expanded) continue
        when (section) {
            EditorSection.BASICS -> add(EditorRow.Basics)
            EditorSection.FLAVOR -> add(EditorRow.Flavor)
            EditorSection.INGREDIENTS -> {
                state.ingredients.forEachIndexed { i, draft ->
                    val section = draft.section.trim()
                    val previous = state.ingredients.getOrNull(i - 1)?.section?.trim().orEmpty()
                    add(EditorRow.IngredientItem(i, draft, section.takeIf { it.isNotEmpty() && it != previous }))
                }
                add(EditorRow.AddIngredient)
            }
            EditorSection.STEPS -> {
                state.steps.forEachIndexed { i, draft -> add(EditorRow.StepItem(i, draft)) }
                add(EditorRow.AddStep)
            }
            EditorSection.NOTES -> add(EditorRow.Notes)
        }
    }
}

internal fun sectionsWithErrors(state: EditorState): Set<EditorSection> = buildSet {
    if (state.errors.isNotEmpty()) add(EditorSection.BASICS)
    if (state.ingredients.any { hasIngredientError(state, it.id) }) add(EditorSection.INGREDIENTS)
    if (state.steps.any { stepTextErrorKey(it.id) in state.rowErrors }) add(EditorSection.STEPS)
}

/** Clave de la fila del primer error, en el orden de la pantalla. */
internal fun firstErrorKey(state: EditorState): String? = when {
    state.errors.isNotEmpty() -> EditorRow.Basics.key
    else -> state.ingredients.firstOrNull { hasIngredientError(state, it.id) }?.let { INGREDIENT_PREFIX + it.id }
        ?: state.steps.firstOrNull { stepTextErrorKey(it.id) in state.rowErrors }?.let { STEP_PREFIX + it.id }
}

private fun hasIngredientError(state: EditorState, id: String) =
    ingredientNameErrorKey(id) in state.rowErrors || ingredientQuantityErrorKey(id) in state.rowErrors
