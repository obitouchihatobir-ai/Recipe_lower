package app.recetario.home

import app.recetario.AppContainer
import app.recetario.diary.CookStats
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.RecipeSummary
import app.recetario.today.MealTimes
import app.recetario.today.ThermalThresholds
import app.recetario.today.TodayContext
import app.recetario.today.TodayInputs
import app.recetario.today.TodayMenu
import app.recetario.today.TodayRecipe
import app.recetario.today.planToday
import app.recetario.today.recipeIn
import app.recetario.today.todayContext
import app.recetario.today.todayInputs
import app.recetario.today.todaySeed
import app.recetario.today.todaySlots
import app.recetario.weather.Place
import app.recetario.weather.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.random.Random

/**
 * Todo lo que el motor de Hoy necesita, ya leído. Lo comparten la tarjeta de Inicio ([TodayViewModel]) y el widget
 * «Hoy»: con los mismos datos, el mismo día, el mismo momento y la misma vuelta de «Otra idea» proponen lo mismo.
 */
data class TodaySources(
    val summaries: List<RecipeSummary>,
    val categories: List<Category>,
    val stats: Map<String, CookStats>,
    /** Los nombres de lo que hay en la despensa. */
    val pantry: List<String>,
    val basics: List<String>,
    val mealTimes: MealTimes,
    val thresholds: ThermalThresholds,
    /** El tiempo guardado (null sin ubicación o si tiene más de 6 h). */
    val weather: WeatherSnapshot?,
    val place: Place?,
    /** Valoraciones de maridaje: `pairKey` → 👍 (true) o 👎 (false). */
    val ratings: Map<String, Boolean>,
)

/** El menú de un día y momento, su contexto y la receta que manda (la primera de sus huecos, como la tarjeta). */
data class TodayPlan(val context: TodayContext, val menu: TodayMenu, val lead: TodayRecipe?)

/**
 * El menú de Hoy para [day] y [moment] sin repetir [exclude], con la semilla de [round] ([todaySeed]): la tarjeta y el
 * widget llaman a esto mismo. [plan] es el motor (las pruebas de la tarjeta cuentan sus llamadas).
 */
fun planTodayFor(
    src: TodaySources,
    day: LocalDate,
    moment: Moment,
    exclude: List<String>,
    round: Int,
    plan: (TodayContext, TodayInputs, List<String>, Random) -> TodayMenu = ::planToday,
): TodayPlan {
    val ctx = todayContext(day, moment, src.place?.latitude, src.thresholds, src.weather?.current)
    val inputs = todayInputs(src.summaries, src.categories, src.stats, src.pantry, src.basics, src.ratings)
    val menu = plan(ctx, inputs, exclude, Random(todaySeed(day, moment, round)))
    return TodayPlan(ctx, menu, todaySlots(moment).firstNotNullOfOrNull { menu.recipeIn(it) })
}

/** Las valoraciones de maridaje como las lee el motor de Hoy (`pairKey` → 👍). */
fun AppContainer.pairingLikes(): Flow<Map<String, Boolean>> =
    database.pairingRatingDao().observeAll().map { rows -> rows.associate { it.pairKey to it.liked } }

/** Los datos de Hoy de la app, juntos (el widget lee el primer valor; la tarjeta escucha cada parte por separado). */
fun AppContainer.todaySources(): Flow<TodaySources> = todaySources(
    summaries = recipeRepository.observeSummaries(),
    categories = recipeRepository.observeCategories(),
    stats = diaryRepository.observeStats(),
    pantry = pantryRepository.observe().map { items -> items.map { it.name } },
    basics = settingsRepository.pantryBasics,
    mealTimes = settingsRepository.mealTimes,
    thresholds = settingsRepository.thermalThresholds,
    weather = weatherRepository.snapshot,
    place = weatherRepository.location(),
    ratings = pairingLikes(),
)

private data class TodayPrefs(
    val pantry: List<String>,
    val basics: List<String>,
    val mealTimes: MealTimes,
    val thresholds: ThermalThresholds,
)

/** Junta las partes en un solo flujo. */
fun todaySources(
    summaries: Flow<List<RecipeSummary>>,
    categories: Flow<List<Category>>,
    stats: Flow<Map<String, CookStats>>,
    pantry: Flow<List<String>>,
    basics: Flow<List<String>>,
    mealTimes: Flow<MealTimes>,
    thresholds: Flow<ThermalThresholds>,
    weather: Flow<WeatherSnapshot?>,
    place: Flow<Place?>,
    ratings: Flow<Map<String, Boolean>>,
): Flow<TodaySources> = combine(
    combine(summaries, categories, stats) { s, c, st -> Triple(s, c, st) },
    combine(pantry, basics, mealTimes, thresholds) { p, b, m, t -> TodayPrefs(p, b, m, t) },
    combine(weather, place, ratings) { w, pl, r -> Triple(w, pl, r) },
) { (s, c, st), prefs, (w, pl, r) ->
    TodaySources(s, c, st, prefs.pantry, prefs.basics, prefs.mealTimes, prefs.thresholds, w, pl, r)
}
