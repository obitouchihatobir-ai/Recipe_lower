package app.recetario.home

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
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.RecipeSummary
import app.recetario.today.MealTimes
import app.recetario.today.ThermalThresholds
import app.recetario.today.TodayContext
import app.recetario.today.TodayInputs
import app.recetario.today.TodayMenu
import app.recetario.today.TodaySlot
import app.recetario.today.momentAt
import app.recetario.today.nextExclude
import app.recetario.today.planToday
import app.recetario.today.recipeIn
import app.recetario.today.seasonFor
import app.recetario.today.thermalClass
import app.recetario.today.toTodayRecipe
import app.recetario.today.todayEyebrow
import app.recetario.today.todaySeed
import app.recetario.today.todaySlots
import app.recetario.today.weatherChipDescription
import app.recetario.today.weatherChipText
import app.recetario.today.weatherEmoji
import app.recetario.weather.APPROXIMATE_PLACE_NAME
import app.recetario.weather.Place
import app.recetario.weather.WeatherSnapshot
import app.recetario.weather.weatherKind
import app.recetario.weather.weatherLabel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import kotlin.random.Random

/** Una receta del menú de hoy tal como la pinta la tarjeta. */
data class TodayCardRecipe(
    val id: String,
    val title: String,
    /** El emoji de su categoría y el nombre de la categoría (lo que lee TalkBack en lugar del emoji). */
    val emoji: String,
    val categoryName: String,
    /** «Principal · 35 min». */
    val subtitle: String,
    val servings: Int,
)

/** Un hueco de la tarjeta: su receta o, si no hay ninguna que lo llene, null («Aún no tienes bebidas»). */
data class TodayCardItem(val slot: TodaySlot, val recipe: TodayCardRecipe?)

/** El tiempo de ahora, pequeño, arriba de la tarjeta. */
data class TodayWeatherChip(val emoji: String, val text: String, val description: String)

data class TodayState(
    /** False hasta tener el primer menú (la tarjeta no sale hasta entonces; Inicio no espera por ella). */
    val loaded: Boolean = false,
    val eyebrow: String = "",
    val reason: String = "",
    val moment: Moment = Moment.DINNER,
    val items: List<TodayCardItem> = emptyList(),
    val weather: TodayWeatherChip? = null,
    /** El menú de ahora (para «Otra idea») y el día y momento al que pertenece. */
    val menu: TodayMenu? = null,
    val slotKey: String = "",
) {
    /** La receta que manda (el plato principal o, de noche, la bebida): la que abre «Empezar». */
    val lead: TodayCardRecipe? get() = items.firstNotNullOfOrNull { it.recipe }
}

/**
 * Lo ya mostrado por «Otra idea» en un día y momento ([key], «2026-11-13|DINNER»): los ids en orden ([exclude]) y
 * cuántas veces se pidió ([round], parte de la semilla). Va en el `SavedStateHandle`: sobrevive al giro.
 */
private data class TodayPick(val key: String, val exclude: List<String>, val round: Int)

private data class TodayPrefs(
    val pantry: List<String>,
    val basics: List<String>,
    val mealTimes: MealTimes,
    val thresholds: ThermalThresholds,
)

/** Todo lo que el motor de Hoy necesita, ya leído. */
private data class TodayData(
    val summaries: List<RecipeSummary>,
    val categories: List<Category>,
    val stats: Map<String, CookStats>,
    val pantry: List<String>,
    val basics: List<String>,
    val mealTimes: MealTimes,
    val thresholds: ThermalThresholds,
    val weather: WeatherSnapshot?,
    val place: Place?,
    val ratings: Map<String, Boolean>,
)

/**
 * La tarjeta Hoy de Inicio (diseño 2, §7.3). El menú se calcula en [dispatcher] (fuera del hilo principal) cada vez que
 * cambian los datos o el minuto; con la misma semilla por día y momento ([todaySeed]) la propuesta no salta al girar
 * ni al volver a Inicio. [refreshWeather] pide el tiempo en segundo plano: Inicio nunca lo espera.
 */
class TodayViewModel(
    summaries: Flow<List<RecipeSummary>>,
    categories: Flow<List<Category>>,
    stats: Flow<Map<String, CookStats>>,
    pantry: Flow<List<PantryItem>>,
    basics: Flow<List<String>>,
    mealTimes: Flow<MealTimes>,
    thresholds: Flow<ThermalThresholds>,
    weather: Flow<WeatherSnapshot?>,
    place: Flow<Place?>,
    ratings: Flow<Map<String, Boolean>>,
    private val refresh: suspend () -> Unit,
    private val clock: Clock,
    private val handle: SavedStateHandle,
    ticks: Flow<Unit> = minuteTicks(clock),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val pick = MutableStateFlow(
        TodayPick(
            key = handle.get<String>(KEY_SLOT).orEmpty(),
            exclude = handle.get<ArrayList<String>>(KEY_EXCLUDE).orEmpty(),
            round = handle.get<Int>(KEY_ROUND) ?: 0,
        ),
    )

    private val now: Flow<Instant> = ticks.onStart { emit(Unit) }.map { clock.instant() }

    private val data: Flow<TodayData> = combine(
        combine(summaries, categories, stats) { s, c, st -> Triple(s, c, st) },
        combine(pantry, basics, mealTimes, thresholds) { p, b, m, t -> TodayPrefs(p.map { it.name }, b, m, t) },
        combine(weather, place, ratings) { w, pl, r -> Triple(w, pl, r) },
    ) { (s, c, st), prefs, (w, pl, r) ->
        TodayData(s, c, st, prefs.pantry, prefs.basics, prefs.mealTimes, prefs.thresholds, w, pl, r)
    }

    val state: StateFlow<TodayState> = combine(data, now, pick) { d, at, p -> compute(d, at, p) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** Al entrar en Inicio: pide el tiempo (solo si la caché tiene más de 1 h) sin bloquear nada. */
    fun refreshWeather() {
        viewModelScope.launch { runCatching { refresh() } }
    }

    /** «Otra idea»: otro menú sin repetir lo ya mostrado en este día y momento. */
    fun anotherIdea() {
        val s = state.value
        val menu = s.menu ?: return
        val current = pick.value.takeIf { it.key == s.slotKey } ?: TodayPick(s.slotKey, emptyList(), 0)
        val next = TodayPick(s.slotKey, menu.nextExclude(current.exclude), current.round + 1)
        handle[KEY_SLOT] = next.key
        handle[KEY_EXCLUDE] = ArrayList(next.exclude)
        handle[KEY_ROUND] = next.round
        pick.value = next
    }

    private fun compute(d: TodayData, at: Instant, stored: TodayPick): TodayState {
        val local = LocalDateTime.ofInstant(at, clock.zone)
        val moment = d.mealTimes.momentAt(local.toLocalTime())
        val key = "${local.toLocalDate()}|$moment"
        // Otro día u otro momento: empieza de cero.
        val p = stored.takeIf { it.key == key } ?: TodayPick(key, emptyList(), 0)
        val current = d.weather?.current
        val season = seasonFor(local.toLocalDate(), d.place?.latitude)
        val ctx = TodayContext(
            now = local,
            moment = moment,
            season = season,
            thermal = current?.let { thermalClass(it.apparentC, d.thresholds) },
            weatherKind = current?.let { weatherKind(it.weatherCode) },
            apparentC = current?.apparentC,
            rainingNow = current != null && current.precipitationMm > 0,
            weekend = local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY,
            isDay = current?.isDay,
        )
        val byCategory = d.categories.associateBy { it.id }
        val inputs = TodayInputs(
            recipes = d.summaries.map { it.toTodayRecipe(byCategory[it.categoryId]?.baseType) },
            stats = d.stats,
            pantry = d.pantry,
            basics = d.basics,
            ratings = d.ratings,
        )
        val menu = planToday(ctx, inputs, p.exclude, Random(todaySeed(local.toLocalDate(), moment, p.round)))
        val byId = d.summaries.associateBy { it.id }
        val items = todaySlots(moment).map { slot ->
            TodayCardItem(
                slot,
                menu.recipeIn(slot)?.let { r ->
                    val summary = byId.getValue(r.id)
                    val category = byCategory[summary.categoryId]
                    TodayCardRecipe(
                        id = r.id,
                        title = r.title,
                        emoji = category?.emoji ?: "🍽",
                        categoryName = category?.name ?: "Receta",
                        subtitle = slot.label(moment) + (r.totalMinutes?.let { " · $it min" } ?: ""),
                        servings = summary.servings,
                    )
                },
            )
        }
        val chip = current?.let { c ->
            val city = d.place?.name?.takeIf { it != APPROXIMATE_PLACE_NAME }
            TodayWeatherChip(
                emoji = weatherEmoji(weatherKind(c.weatherCode), c.isDay),
                text = weatherChipText(c.apparentC, city),
                description = weatherChipDescription(weatherLabel(c.weatherCode), c.apparentC, city),
            )
        }
        return TodayState(
            loaded = true,
            eyebrow = todayEyebrow(moment, season),
            reason = menu.reason,
            moment = moment,
            items = items,
            weather = chip,
            menu = menu,
            slotKey = key,
        )
    }

    companion object {
        private const val KEY_SLOT = "today-slot"
        private const val KEY_EXCLUDE = "today-exclude"
        private const val KEY_ROUND = "today-round"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TodayViewModel(
                    summaries = container.recipeRepository.observeSummaries(),
                    categories = container.recipeRepository.observeCategories(),
                    stats = container.diaryRepository.observeStats(),
                    pantry = container.pantryRepository.observe(),
                    basics = container.settingsRepository.pantryBasics,
                    mealTimes = container.settingsRepository.mealTimes,
                    thresholds = container.settingsRepository.thermalThresholds,
                    weather = container.weatherRepository.snapshot,
                    place = container.weatherRepository.location(),
                    ratings = container.database.pairingRatingDao().observeAll()
                        .map { rows -> rows.associate { it.pairKey to it.liked } },
                    refresh = { container.weatherRepository.refresh() },
                    clock = container.clock,
                    handle = createSavedStateHandle(),
                )
            }
        }
    }
}
