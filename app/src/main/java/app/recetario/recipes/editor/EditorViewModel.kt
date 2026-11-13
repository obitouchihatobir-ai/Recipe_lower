package app.recetario.recipes.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.RecetarioApp
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.Step
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.parseQuantity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor

/** Dónde va una foto recién importada. */
sealed interface PhotoTarget {
    data object Cover : PhotoTarget
    data class StepPhoto(val stepId: String) : PhotoTarget
}

sealed interface EditorEvent {
    data class SetTitle(val value: String) : EditorEvent
    /** [baseType] es el tipo de la categoría elegida; si momentos y temperatura están vacíos se sugieren los de ese tipo. */
    data class SetCategory(val categoryId: String, val baseType: BaseType) : EditorEvent
    data class SetDescription(val value: String) : EditorEvent
    data class SetCookingMinutes(val text: String) : EditorEvent
    data class SetServings(val value: Int) : EditorEvent
    data class SetDifficulty(val value: Difficulty) : EditorEvent
    data class SetFlavor(val value: FlavorProfile) : EditorEvent
    data object MarkSweet : EditorEvent
    data object MarkSalty : EditorEvent
    data class SetServeTemp(val value: ServeTemp?) : EditorEvent
    data class ToggleMoment(val moment: Moment) : EditorEvent
    data class SetAlcoholic(val value: Boolean?) : EditorEvent
    data class SetCourse(val value: Course?) : EditorEvent
    data class SetTags(val value: List<String>) : EditorEvent
    data class SetNotes(val value: String) : EditorEvent
    data class SetSourceUrl(val value: String) : EditorEvent

    data object AddIngredient : EditorEvent
    data class UpdateIngredient(val value: IngredientDraft) : EditorEvent
    data class RemoveIngredient(val id: String) : EditorEvent
    data class MoveIngredient(val from: Int, val to: Int) : EditorEvent

    data object AddStep : EditorEvent
    data class SetStepText(val id: String, val text: String) : EditorEvent
    data class SetStepTimer(val id: String, val seconds: Int?) : EditorEvent
    data class SetStepIngredients(val id: String, val ingredientIds: Set<String>) : EditorEvent
    data class RemoveStep(val id: String) : EditorEvent
    data class MoveStep(val from: Int, val to: Int) : EditorEvent

    /** Importa la imagen con el PhotoStore y la coloca en [target]. */
    class ImportPhoto(val source: InputStream, val target: PhotoTarget) : EditorEvent
    data object RemoveCover : EditorEvent
    data class RemoveStepPhoto(val stepId: String, val fileName: String) : EditorEvent
}

/**
 * Lógica del editor de recetas. El estado se vuelca a un borrador (con 500 ms de espera) cada vez que difiere
 * de la receta guardada, así se recupera tanto al salir sin guardar como si Android cierra la app.
 */
class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: RecipeRepository,
    private val photoStore: PhotoStore,
    private val draftStore: DraftStore,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val draftDelayMillis: Long = 500,
) : ViewModel() {

    private val editingId: String? = savedStateHandle[RECIPE_ID_ARG]
    private val presetCategoryId: String? = savedStateHandle[CATEGORY_ID_ARG]

    /** `new` mientras la receta es nueva; tras el primer guardado pasa a ser el ID de la receta. */
    private var draftKey = editingId ?: DraftStore.NEW_KEY

    /** Estado de partida (receta guardada o formulario en blanco); el borrador solo existe si difiere de él. */
    private var baseline = EditorState(recipeId = editingId ?: newId())

    private val _state = MutableStateFlow(baseline)
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True cuando la receta que se edita ya se cargó (y se miró si había borrador). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _pendingDraft = MutableStateFlow<EditorState?>(null)

    /** Borrador encontrado al abrir: [recoverDraft] lo adopta y [discardPendingDraft] lo borra. */
    val pendingDraft: StateFlow<EditorState?> = _pendingDraft.asStateFlow()

    private val importedPhotos = LinkedHashSet<String>()
    private var persistJob: Job? = null
    private var dirty = false

    init {
        viewModelScope.launch {
            if (editingId != null) {
                repo.observeRecipe(editingId).first()?.let {
                    baseline = it.toEditorState()
                    _state.value = baseline
                }
            }
            if (editingId == null && presetCategoryId != null) {
                // Solo se preselecciona una categoría que existe (si no, guardar chocaría con la clave foránea).
                val category = repo.observeCategories().first().firstOrNull { it.id == presetCategoryId }
                if (category != null) {
                    val (moments, temp) = defaultsFor(category.baseType)
                    baseline = baseline.copy(
                        categoryId = category.id, moments = moments, serveTemp = temp, defaultsSuggested = true,
                    )
                    _state.value = baseline
                }
            }
            val draft = withContext(ioDispatcher) { draftStore.load(draftKey) }
            if (draft != null) {
                if (draft == baseline) withContext(ioDispatcher) { draftStore.clear(draftKey) }
                else _pendingDraft.value = draft
            }
            _ready.value = true
        }
    }

    fun onEvent(e: EditorEvent) {
        when (e) {
            is EditorEvent.SetTitle -> update { it.copy(title = e.value, errors = it.errors - Field.TITLE) }
            is EditorEvent.SetCategory -> update { s ->
                val next = s.copy(categoryId = e.categoryId, errors = s.errors - Field.CATEGORY)
                if ((s.moments.isEmpty() && s.serveTemp == null) || s.defaultsSuggested) {
                    val (moments, temp) = defaultsFor(e.baseType)
                    next.copy(moments = moments, serveTemp = temp, defaultsSuggested = true)
                } else next
            }
            is EditorEvent.SetDescription -> update { it.copy(description = e.value) }
            is EditorEvent.SetCookingMinutes -> update { it.copy(cookingMinutesText = e.text, errors = it.errors - Field.MINUTES) }
            is EditorEvent.SetServings -> update { it.copy(servings = e.value.coerceIn(1, 99)) }
            is EditorEvent.SetDifficulty -> update { it.copy(difficulty = e.value) }
            is EditorEvent.SetFlavor -> update { it.withFlavor(e.value) }
            EditorEvent.MarkSweet -> update { it.withFlavor(it.flavor.markSweet()) }
            EditorEvent.MarkSalty -> update { it.withFlavor(it.flavor.markSalty()) }
            is EditorEvent.SetServeTemp -> update { it.copy(serveTemp = e.value, defaultsSuggested = false) }
            is EditorEvent.ToggleMoment -> update {
                it.copy(
                    moments = if (e.moment in it.moments) it.moments - e.moment else it.moments + e.moment,
                    defaultsSuggested = false,
                )
            }
            is EditorEvent.SetAlcoholic -> update { it.copy(alcoholic = e.value) }
            is EditorEvent.SetCourse -> update { it.copy(course = e.value) }
            is EditorEvent.SetTags -> update { it.copy(tags = e.value) }
            is EditorEvent.SetNotes -> update { it.copy(notes = e.value) }
            is EditorEvent.SetSourceUrl -> update { it.copy(sourceUrl = e.value) }

            EditorEvent.AddIngredient -> update { it.copy(ingredients = it.ingredients + IngredientDraft(id = newId())) }
            is EditorEvent.UpdateIngredient -> update { s ->
                s.withoutRowErrorsOf(e.value.id)
                    .copy(ingredients = s.ingredients.map { if (it.id == e.value.id) e.value else it })
            }
            is EditorEvent.RemoveIngredient -> update { s ->
                s.copy(
                    ingredients = s.ingredients.filterNot { it.id == e.id },
                    steps = s.steps.map { it.copy(ingredientIds = it.ingredientIds - e.id) },
                )
            }
            is EditorEvent.MoveIngredient -> update { it.copy(ingredients = it.ingredients.moved(e.from, e.to)) }

            EditorEvent.AddStep -> update { it.copy(steps = it.steps + StepDraft(id = newId())) }
            is EditorEvent.SetStepText -> updateStep(e.id) { it.copy(text = e.text) }
            is EditorEvent.SetStepTimer -> updateStep(e.id) { it.copy(timerSeconds = e.seconds) }
            is EditorEvent.SetStepIngredients -> updateStep(e.id) { it.copy(ingredientIds = e.ingredientIds) }
            is EditorEvent.RemoveStep -> update { s -> s.copy(steps = s.steps.filterNot { it.id == e.id }) }
            is EditorEvent.MoveStep -> update { it.copy(steps = it.steps.moved(e.from, e.to)) }

            is EditorEvent.ImportPhoto -> viewModelScope.launch {
                val name = try {
                    photoStore.import(e.source)
                } catch (_: IOException) {
                    return@launch
                }
                attachImportedPhoto(name, e.target)
            }
            EditorEvent.RemoveCover -> update { it.copy(coverPhoto = null) }
            is EditorEvent.RemoveStepPhoto -> updateStep(e.stepId) { it.copy(photos = it.photos - e.fileName) }
        }
    }

    /** Coloca una foto ya guardada en el PhotoStore y la anota como de esta sesión (se borra si se descarta). */
    fun attachImportedPhoto(fileName: String, target: PhotoTarget) {
        if (_pendingDraft.value != null) {
            // Con un borrador sin resolver no se edita: la foto recién importada no la usará nadie.
            photoStore.delete(fileName)
            return
        }
        importedPhotos += fileName
        when (target) {
            PhotoTarget.Cover -> update { it.copy(coverPhoto = fileName) }
            is PhotoTarget.StepPhoto -> updateStep(target.stepId) { it.copy(photos = it.photos + fileName) }
        }
    }

    /** Valida y guarda. Devuelve el ID de la receta, o null si hay errores (quedan en `state.errors`). */
    suspend fun save(): String? {
        if (!_ready.value) return null
        if (_pendingDraft.value != null) return null
        val s = _state.value
        val (errors, rowErrors) = s.validate()
        if (errors.isNotEmpty() || rowErrors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, rowErrors = rowErrors) }
            return null
        }
        val recipe = s.toRecipe(clock)
        repo.save(recipe)
        persistJob?.cancelAndJoin()
        dirty = false
        val createdMillis = recipe.createdAt.toEpochMilli()
        baseline = s.withoutErrors().copy(createdAtMillis = createdMillis)
        _state.update { it.withoutErrors().copy(createdAtMillis = createdMillis) }
        val kept = (listOfNotNull(recipe.coverPhoto) + recipe.steps.flatMap { it.photos }).toSet()
        withContext(ioDispatcher) {
            draftStore.clear(draftKey)
            (importedPhotos - kept).forEach(photoStore::delete)
        }
        importedPhotos.clear()
        draftKey = recipe.id // las siguientes ediciones ya son de una receta existente
        return recipe.id
    }

    /** «Descartar»: vuelve al estado de partida, borra el borrador y las fotos importadas que no son de la receta guardada. */
    suspend fun discard() {
        if (_pendingDraft.value != null) return
        persistJob?.cancelAndJoin()
        dirty = false
        val orphans = importedPhotos + (_state.value.photoFiles - baseline.photoFiles)
        _state.value = baseline
        importedPhotos.clear()
        withContext(ioDispatcher) {
            draftStore.clear(draftKey)
            orphans.forEach { deleteIfUnreferenced(it) }
        }
    }

    /** Adopta el borrador encontrado al abrir. */
    fun recoverDraft() {
        val draft = _pendingDraft.value ?: return
        _state.value = draft
        _pendingDraft.value = null
    }

    /** Descarta el borrador encontrado al abrir (y sus fotos nuevas). */
    suspend fun discardPendingDraft() {
        val draft = _pendingDraft.value ?: return
        _pendingDraft.value = null
        withContext(ioDispatcher) {
            draftStore.clear(draftKey)
            (draft.photoFiles - baseline.photoFiles).forEach { deleteIfUnreferenced(it) }
        }
    }

    override fun onCleared() {
        // Salir de la pantalla con cambios sin volcar: se escribe ya, sin esperar a la pausa.
        if (dirty) writeDraft()
    }

    private suspend fun deleteIfUnreferenced(name: String) {
        if (!repo.isPhotoReferenced(name)) photoStore.delete(name)
    }

    private fun update(transform: (EditorState) -> EditorState) {
        // Mientras haya un borrador sin resolver (recuperar o descartar) no se edita nada.
        if (!_ready.value || _pendingDraft.value != null) return
        _state.update(transform)
        dirty = true
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(draftDelayMillis)
            withContext(ioDispatcher) { writeDraft() }
        }
    }

    private fun updateStep(id: String, transform: (StepDraft) -> StepDraft) =
        update { s -> s.withoutRowErrorsOf(id).copy(steps = s.steps.map { if (it.id == id) transform(it) else it }) }

    private fun writeDraft() {
        if (_pendingDraft.value != null) return // todavía no se ha decidido qué hacer con el borrador anterior
        val current = _state.value.withoutErrors()
        try {
            if (current == baseline) draftStore.clear(draftKey) else draftStore.save(draftKey, current)
        } catch (_: IOException) {
            // Un borrador que no se puede escribir no debe tumbar el editor.
        }
    }

    companion object {
        /** Argumentos de la ruta `RecipeEditor(id, categoryId)`. */
        const val RECIPE_ID_ARG = "id"
        const val CATEGORY_ID_ARG = "categoryId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RecetarioApp).container
                EditorViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repo = container.recipeRepository,
                    photoStore = container.photoStore,
                    draftStore = DraftStore(container.draftsDir),
                    clock = container.clock,
                )
            }
        }
    }
}

private fun newId() = UUID.randomUUID().toString()

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Texto editable de una cantidad: entero, fracción sencilla («1 1/3») o decimal con coma; lo entiende parseQuantity. */
internal fun quantityToText(v: Double): String {
    if (abs(v - Math.rint(v)) < 1e-9) return Math.rint(v).toLong().toString()
    val whole = floor(v).toLong()
    val frac = v - whole
    for (den in intArrayOf(2, 3, 4, 6, 8)) {
        val num = Math.rint(frac * den)
        if (num in 1.0..(den - 1.0) && abs(frac * den - num) < 1e-6) {
            val f = "${num.toLong()}/$den"
            return if (whole > 0) "$whole $f" else f
        }
    }
    return java.math.BigDecimal(v).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString().replace('.', ',')
}

internal fun Recipe.toEditorState() = EditorState(
    recipeId = id, createdAtMillis = createdAt.toEpochMilli(), title = title, categoryId = categoryId,
    coverPhoto = coverPhoto, description = description, cookingMinutesText = totalMinutes?.toString().orEmpty(),
    servings = servings, difficulty = difficulty,
    sweet = flavor.sweet, salty = flavor.salty, sour = flavor.sour, bitter = flavor.bitter, spicy = flavor.spicy,
    intensity = flavor.intensity, rich = flavor.rich, serveTemp = serveTemp, moments = moments,
    alcoholic = alcoholic, course = course,
    ingredients = ingredients.map {
        IngredientDraft(
            id = it.id, section = it.section.orEmpty(), name = it.name,
            quantityText = it.quantity?.let(::quantityToText).orEmpty(), unit = it.unit,
            customUnit = it.customUnit.orEmpty(), note = it.note,
        )
    },
    steps = steps.map { StepDraft(it.id, it.text, it.photos, it.timerSeconds, it.ingredientIds) },
    tags = tags, notes = notes, favorite = favorite, sourceUrl = sourceUrl.orEmpty(), aiGenerated = aiGenerated,
)

internal fun EditorState.toRecipe(clock: Clock): Recipe {
    val now = clock.instant()
    val ingredientList = ingredients.filter { it.name.isNotBlank() }.map {
        Ingredient(
            id = it.id,
            section = it.section.trim().ifEmpty { null },
            name = it.name.trim(),
            quantity = parseQuantity(it.quantityText),
            unit = it.unit,
            customUnit = if (it.unit == MeasureUnit.CUSTOM) it.customUnit.trim().ifEmpty { null } else null,
            note = it.note.trim(),
        )
    }
    val ids = ingredientList.mapTo(HashSet()) { it.id }
    return Recipe(
        id = recipeId,
        title = title.trim(),
        categoryId = requireNotNull(categoryId),
        coverPhoto = coverPhoto,
        description = description.trim(),
        totalMinutes = parseMinutes(cookingMinutesText),
        servings = servings.coerceAtLeast(1),
        difficulty = difficulty,
        flavor = flavor,
        serveTemp = serveTemp,
        moments = moments,
        alcoholic = alcoholic,
        course = course,
        ingredients = ingredientList,
        steps = steps.filter { it.text.isNotBlank() }.map {
            Step(it.id, it.text.trim(), it.photos, it.timerSeconds, it.ingredientIds.filterTo(LinkedHashSet()) { id -> id in ids })
        },
        tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        notes = notes.trim(),
        favorite = favorite,
        sourceUrl = sourceUrl.trim().ifEmpty { null },
        aiGenerated = aiGenerated,
        createdAt = createdAtMillis?.let(Instant::ofEpochMilli) ?: now,
        updatedAt = now,
    )
}

/** Minutos de un texto como «35», «45 min» o «1 h 30 min»; null si está vacío o no se entiende. */
internal fun parseMinutes(text: String): Int? = parseDurationMinutes(text)?.takeIf { it > 0 }

/** Errores del formulario: los de campo y los de fila. Las filas totalmente vacías no dan error (se descartan al guardar). */
internal fun EditorState.validate(): Pair<Map<Field, String>, Map<String, String>> {
    val errors = buildMap {
        if (title.isBlank()) put(Field.TITLE, "Ponle un nombre a la receta")
        if (categoryId == null) put(Field.CATEGORY, "Elige una categoría")
        if (cookingMinutesText.isNotBlank() && parseMinutes(cookingMinutesText) == null) {
            put(Field.MINUTES, "Escribe el tiempo en minutos, por ejemplo 35.")
        }
    }
    val rows = buildMap {
        for (i in ingredients) {
            val hasQuantity = i.quantityText.isNotBlank()
            if (i.name.isBlank()) {
                if (hasQuantity || i.note.isNotBlank() || i.section.isNotBlank() || i.customUnit.isNotBlank()) put(ingredientNameErrorKey(i.id), "Ponle nombre a este ingrediente.")
            }
            if (hasQuantity && parseQuantity(i.quantityText) == null) {
                put(
                    ingredientQuantityErrorKey(i.id),
                    "No entiendo esta cantidad. Escribe, por ejemplo, 1,5 o ½, o déjala vacía y explícalo en la nota.",
                )
            }
        }
        for (st in steps) {
            if (st.text.isBlank() && (st.photos.isNotEmpty() || st.timerSeconds != null)) {
                put(stepTextErrorKey(st.id), "Escribe qué hay que hacer en este paso.")
            }
        }
    }
    return errors to rows
}
