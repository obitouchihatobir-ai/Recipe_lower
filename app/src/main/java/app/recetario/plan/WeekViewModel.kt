package app.recetario.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.AppContainer
import app.recetario.diary.CookStats
import app.recetario.pantry.PantryItem
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.RecipeSummary
import app.recetario.shopping.ShoppingRepository
import app.recetario.today.ThermalThresholds
import app.recetario.today.dayContext
import app.recetario.today.planToday
import app.recetario.today.todayInputs
import app.recetario.today.todaySeed
import app.recetario.weather.Place
import app.recetario.weather.WeatherSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/** Una receta que se puede poner en un hueco (el buscador del panel). */
data class WeekRecipeChoice(val id: String, val title: String, val emoji: String, val servings: Int)

/** Un hueco de un día: su plato (null si está vacío) con el título y el emoji de la receta. */
data class WeekSlotView(val date: LocalDate, val slot: MealSlot, val meal: PlannedMeal?, val title: String?, val emoji: String?)

data class WeekDayView(val date: LocalDate, val label: String, val isToday: Boolean, val slots: List<WeekSlotView>)

data class WeekState(
    /** False hasta que llegan los datos (así no parpadea «Añade recetas…»). */
    val loaded: Boolean = false,
    /** Semanas desde la actual (0: esta semana). */
    val offset: Int = 0,
    val title: String = "",
    val days: List<WeekDayView> = emptyList(),
    /** Las recetas para el buscador, por título. */
    val recipes: List<WeekRecipeChoice> = emptyList(),
) {
    val hasRecipes: Boolean get() = recipes.isNotEmpty()

    fun slot(date: LocalDate, slot: MealSlot): WeekSlotView? =
        days.firstOrNull { it.date == date }?.slots?.firstOrNull { it.slot == slot }
}

/** Lo que «Rellenar con sugerencias» le pasa al motor de Hoy, ya leído (como la tarjeta Hoy). */
private data class FillSources(
    val summaries: List<RecipeSummary>,
    val categories: List<Category>,
    val stats: Map<String, CookStats>,
    val pantry: List<String>,
    val basics: List<String>,
    val ratings: Map<String, Boolean>,
    val thresholds: ThermalThresholds,
    val forecast: WeatherSnapshot?,
    val place: Place?,
)

/**
 * La pestaña Semana de Compra (diseño de la etapa 2, §7.6). La semana que se ve ([WeekState.offset]) va en el
 * `SavedStateHandle`: sobrevive al giro. «Rellenar con sugerencias» usa el motor de Hoy ([planToday]) con la previsión
 * de cada día ([dayContext]) y las mismas entradas que la tarjeta Hoy ([todayInputs]); «Generar lista de compra»
 * funde [weekIngredients] con la lista como «Lo que necesito».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(
    private val plan: MealPlanRepository,
    private val summaries: Flow<List<RecipeSummary>>,
    private val categories: Flow<List<Category>>,
    private val stats: Flow<Map<String, CookStats>>,
    private val pantry: Flow<List<PantryItem>>,
    private val basics: Flow<List<String>>,
    private val ratings: Flow<Map<String, Boolean>>,
    private val thresholds: Flow<ThermalThresholds>,
    private val forecast: Flow<WeatherSnapshot?>,
    private val place: Flow<Place?>,
    private val recipe: suspend (String) -> Recipe?,
    private val shopping: ShoppingRepository,
    private val cupMl: Flow<Int>,
    private val clock: Clock,
    private val handle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val offset = MutableStateFlow(handle.get<Int>(KEY_OFFSET) ?: 0)

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun mondayOf(weeks: Int): LocalDate = weekOf(today()).first().plusWeeks(weeks.toLong())

    val state: StateFlow<WeekState> = offset
        .flatMapLatest { weeks ->
            val monday = mondayOf(weeks)
            combine(plan.observeWeek(monday), summaries, categories) { meals, recipes, cats ->
                build(weeks, monday, meals, recipes, cats)
            }
        }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekState())

    private fun build(weeks: Int, monday: LocalDate, meals: List<PlannedMeal>, recipes: List<RecipeSummary>, cats: List<Category>): WeekState {
        val today = today()
        val emojis = cats.associate { it.id to it.emoji }
        val byId = recipes.associateBy { it.id }
        val bySlot = meals.associateBy { it.date to it.slot }
        val days = weekOf(monday).map { date ->
            WeekDayView(
                date = date,
                label = weekDayLabel(date),
                isToday = date == today,
                slots = MealSlot.entries.map { slot ->
                    val meal = bySlot[date to slot]
                    val r = meal?.let { byId[it.recipeId] }
                    WeekSlotView(date, slot, meal?.takeIf { r != null }, r?.title, r?.let { emojis[it.categoryId] ?: "🍽" })
                },
            )
        }
        return WeekState(
            loaded = true,
            offset = weeks,
            title = weekTitle(monday, today),
            days = days,
            recipes = recipes
                .sortedBy { it.title.lowercase() }
                .map { WeekRecipeChoice(it.id, it.title, emojis[it.categoryId] ?: "🍽", it.servings) },
        )
    }

    fun previousWeek() = moveTo(offset.value - 1)

    fun nextWeek() = moveTo(offset.value + 1)

    private fun moveTo(weeks: Int) {
        handle[KEY_OFFSET] = weeks
        offset.value = weeks
    }

    suspend fun set(date: LocalDate, slot: MealSlot, recipeId: String, servings: Int) = plan.set(date, slot, recipeId, servings)

    /** Vacía el hueco y devuelve lo que había, para «Deshacer». */
    suspend fun clear(date: LocalDate, slot: MealSlot): PlannedMeal? = plan.clear(date, slot)

    /** «Deshacer» de «Quitar»: vuelve a poner el plato si su hueco sigue libre. */
    suspend fun restore(meal: PlannedMeal) {
        plan.addAll(listOf(meal))
    }

    /**
     * «Rellenar con sugerencias»: un plato para cada hueco vacío de la semana que se ve, desde hoy hasta el domingo,
     * sin repetir receta mientras haya otras. Devuelve los platos añadidos (los que quita «Deshacer»).
     */
    suspend fun fill(): List<PlannedMeal> = withContext(dispatcher) {
        val monday = mondayOf(offset.value)
        val today = today()
        val s = sources()
        val inputs = todayInputs(s.summaries, s.categories, s.stats, s.pantry, s.basics, s.ratings)
        val servings = s.summaries.associate { it.id to it.servings }
        val proposed = fillWeek(
            week = weekOf(monday),
            today = today,
            existing = plan.observeWeek(monday).first(),
            planFor = { date, slot, used ->
                val daily = s.forecast?.daily?.firstOrNull { it.date == date }
                val ctx = dayContext(date, slot.moment, s.place?.latitude, s.thresholds, daily)
                // Solo el plato: un hueco del menú no se llena con una bebida o un postre sueltos.
                planToday(ctx, inputs, used.toList(), Random(todaySeed(date, slot.moment, 0))).main
            },
            defaultServings = { servings[it] ?: 1 },
            newId = { UUID.randomUUID().toString() },
        )
        plan.addAll(proposed)
    }

    /** «Deshacer» de «Rellenar con sugerencias»: quita exactamente lo que se añadió. */
    suspend fun undoFill(added: List<PlannedMeal>) = plan.removeAll(added.map { it.id })

    /**
     * «Generar lista de compra»: los ingredientes de la semana que se ve, desde hoy, sin la despensa ni los básicos,
     * fundidos con la lista. Devuelve cuántas líneas se añadieron (0: no falta nada).
     */
    suspend fun generateList(): Int = withContext(dispatcher) {
        val meals = plan.observeWeek(mondayOf(offset.value)).first()
        val recipes = meals.map { it.recipeId }.distinct().mapNotNull { recipe(it) }.associateBy { it.id }
        val lines = weekIngredients(
            meals = meals,
            today = today(),
            recipes = recipes,
            pantry = pantry.first().map { it.name },
            basics = basics.first(),
            cupMl = cupMl.first(),
        )
        shopping.addLines(lines)
    }

    private suspend fun sources(): FillSources = FillSources(
        summaries = summaries.first(),
        categories = categories.first(),
        stats = stats.first(),
        pantry = pantry.first().map { it.name },
        basics = basics.first(),
        ratings = ratings.first(),
        thresholds = thresholds.first(),
        forecast = forecast.first(),
        place = place.first(),
    )

    companion object {
        private const val KEY_OFFSET = "week-offset"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WeekViewModel(
                    plan = container.mealPlanRepository,
                    summaries = container.recipeRepository.observeSummaries(),
                    categories = container.recipeRepository.observeCategories(),
                    stats = container.diaryRepository.observeStats(),
                    pantry = container.pantryRepository.observe(),
                    basics = container.settingsRepository.pantryBasics,
                    ratings = container.pairingRepository.observeRatings(),
                    thresholds = container.settingsRepository.thermalThresholds,
                    forecast = container.weatherRepository.forecast,
                    place = container.weatherRepository.location(),
                    recipe = { id -> container.recipeRepository.observeRecipe(id).first() },
                    shopping = container.shoppingRepository,
                    cupMl = container.settingsRepository.cupMl,
                    clock = container.clock,
                    handle = createSavedStateHandle(),
                )
            }
        }
    }
}
