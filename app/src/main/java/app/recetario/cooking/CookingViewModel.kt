package app.recetario.cooking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.AppContainer
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.list.DEFAULT_EMOJI
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.scaleQuantity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CookingUiState(
    /** True hasta que llega la receta. */
    val loading: Boolean = true,
    /** Null mientras carga o si la receta ya no existe. */
    val recipe: Recipe? = null,
    val servings: Int = 1,
    val categoryEmoji: String = DEFAULT_EMOJI,
    /** Paso expandido; igual al número de pasos cuando la receta está lista. */
    val currentIndex: Int = 0,
    /** Índices de los pasos marcados como hechos. */
    val doneSteps: Set<Int> = emptySet(),
    val checkedIngredients: Set<String> = emptySet(),
    /** Cantidad ya escalada a [servings] y formateada de cada ingrediente, por id. */
    val quantities: Map<String, String> = emptyMap(),
    /** Temporizadores de los pasos de esta receta, por id de paso. */
    val timers: Map<String, CookingTimer> = emptyMap(),
) {
    val stepCount: Int get() = recipe?.steps?.size ?: 0
    val finished: Boolean get() = stepCount > 0 && currentIndex >= stepCount
}

/**
 * Modo cocina. Lee `recipeId` y `servings` de la ruta [app.recetario.navigation.Cooking]. El paso actual, los
 * pasos hechos y los ingredientes tachados viven en [savedStateHandle] (sobreviven al giro y a que Android
 * cierre la app); los temporizadores, en el [TimerManager] de la app, con el id del paso.
 */
class CookingViewModel(
    private val savedStateHandle: SavedStateHandle,
    repo: RecipeRepository,
    private val timerManager: TimerManager,
) : ViewModel() {
    private val recipeId: String = savedStateHandle[RECIPE_ID_ARG] ?: ""
    private val servingsArg: Int = savedStateHandle[SERVINGS_ARG] ?: 0

    /** Null mientras carga; después, la receta (o null dentro si no existe). */
    private val loaded: StateFlow<Loaded?> = repo.observeRecipe(recipeId)
        .map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val progress = combine(
        savedStateHandle.getStateFlow(CURRENT_KEY, 0),
        savedStateHandle.getStateFlow(DONE_KEY, arrayListOf<Int>()),
        savedStateHandle.getStateFlow(CHECKED_KEY, arrayListOf<String>()),
    ) { current, done, checked -> Progress(current, done.toSet(), checked.toSet()) }

    val state: StateFlow<CookingUiState> = combine(
        loaded, repo.observeCategories(), progress, timerManager.timers,
    ) { load, categories, p, timers ->
        val recipe = load?.recipe
        when {
            load == null -> CookingUiState()
            recipe == null -> CookingUiState(loading = false)
            else -> {
                val servings = servingsFor(recipe)
                val stepIds = recipe.steps.map { it.id }.toSet()
                CookingUiState(
                    loading = false,
                    recipe = recipe,
                    servings = servings,
                    categoryEmoji = categories.firstOrNull { it.id == recipe.categoryId }?.emoji ?: DEFAULT_EMOJI,
                    currentIndex = p.current.coerceIn(0, recipe.steps.size),
                    doneSteps = p.done.filter { it in recipe.steps.indices }.toSet(),
                    checkedIngredients = p.checked.filter { c -> recipe.ingredients.any { it.id == c } }.toSet(),
                    quantities = recipe.ingredients.associate {
                        it.id to formatQuantity(scaleQuantity(it.quantity, recipe.servings, servings), it.unit, it.customUnit)
                    },
                    timers = timers.filter { it.id in stepIds }.associateBy { it.id },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookingUiState())

    private val recipe: Recipe? get() = loaded.value?.recipe

    private val currentIndex: Int get() = savedStateHandle[CURRENT_KEY] ?: 0

    private val doneSteps: Set<Int> get() = savedStateHandle.get<ArrayList<Int>>(DONE_KEY).orEmpty().toSet()

    /** True cuando todos los pasos están hechos y no se ha vuelto a ninguno. */
    val finished: Boolean
        get() {
            val count = recipe?.steps?.size ?: return false
            return count > 0 && currentIndex >= count
        }

    /** Marca el paso actual como hecho y abre el siguiente sin hacer (o la tarjeta «¡Listo!»). */
    fun markDone() {
        val count = recipe?.steps?.size ?: return
        val current = currentIndex
        if (current !in 0 until count) return
        val done = doneSteps + current
        savedStateHandle[DONE_KEY] = ArrayList(done.sorted())
        savedStateHandle[CURRENT_KEY] = nextPendingStep(current, done, count)
    }

    /** Vuelve a [index] (o salta a él) sin deshacer lo hecho. */
    fun goTo(index: Int) {
        val count = recipe?.steps?.size ?: return
        if (index in 0 until count) savedStateHandle[CURRENT_KEY] = index
    }

    fun toggleIngredient(id: String) {
        val current = savedStateHandle.get<ArrayList<String>>(CHECKED_KEY) ?: arrayListOf()
        savedStateHandle[CHECKED_KEY] = if (id in current) ArrayList(current - id) else ArrayList(current + id)
    }

    /** Arranca el temporizador del paso [stepIndex] («Paso 2: Sofríe la cebolla…»), con el id del paso. */
    fun startTimer(stepIndex: Int) {
        val step = recipe?.steps?.getOrNull(stepIndex) ?: return
        val seconds = step.timerSeconds?.takeIf { it > 0 } ?: return
        timerManager.start(timerLabel(stepIndex + 1, step.text), seconds, id = step.id)
    }

    /** Pausa el temporizador del paso [stepId] si corre, o lo reanuda si está en pausa. */
    fun toggleTimer(stepId: String) {
        val timer = timerManager.timers.value.firstOrNull { it.id == stepId } ?: return
        when {
            timer.isRunning -> timerManager.pause(stepId)
            timer.isPaused -> timerManager.resume(stepId)
        }
    }

    fun addMinute(stepId: String) = timerManager.addMinute(stepId)

    /** Quita el temporizador del paso (también apaga el «¡Tiempo!»). */
    fun dismissTimer(stepId: String) = timerManager.cancel(stepId)

    /** Hora del reloj de los temporizadores, para la cuenta atrás de la pantalla. */
    fun now(): Long = timerManager.now()

    private fun servingsFor(recipe: Recipe): Int = if (servingsArg > 0) servingsArg else recipe.servings

    private data class Loaded(val recipe: Recipe?)

    private data class Progress(val current: Int, val done: Set<Int>, val checked: Set<String>)

    companion object {
        /** Argumentos de la ruta `Cooking(recipeId, servings)`. */
        const val RECIPE_ID_ARG = "recipeId"
        const val SERVINGS_ARG = "servings"
        private const val CURRENT_KEY = "currentIndex"
        private const val DONE_KEY = "doneSteps"
        private const val CHECKED_KEY = "checkedIngredients"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CookingViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repo = container.recipeRepository,
                    timerManager = container.timerManager,
                )
            }
        }
    }
}
