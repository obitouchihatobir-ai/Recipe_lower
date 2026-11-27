package app.recetario.today

import app.recetario.diary.CookStats
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.RecipeSummary
import app.recetario.season.Produce
import app.recetario.season.SeasonCalendar
import app.recetario.season.inSeasonBonus
import app.recetario.weather.CurrentWeather
import app.recetario.weather.DailyForecast
import app.recetario.weather.weatherKind
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Lo que el motor de Hoy necesita de las recetas y la casa, ya leído (lo comparten la tarjeta Hoy y «Rellenar con
 * sugerencias» de la Semana). Una receta sin categoría cuenta como comida. Con [seasonMonth] (1–12, el mes del
 * contexto), las recetas con productos de temporada de ese mes reciben el empujón de [inSeasonBonus] (diseño de la
 * etapa 2, §7.10); sin él, ninguno.
 */
fun todayInputs(
    summaries: List<RecipeSummary>,
    categories: List<Category>,
    stats: Map<String, CookStats>,
    pantry: List<String>,
    basics: List<String>,
    ratings: Map<String, Boolean>,
    seasonMonth: Int? = null,
    calendar: List<Produce> = SeasonCalendar.load(),
): TodayInputs {
    val baseTypes = categories.associate { it.id to it.baseType }
    return TodayInputs(
        recipes = summaries.map { it.toTodayRecipe(baseTypes[it.categoryId]) },
        stats = stats,
        pantry = pantry,
        basics = basics,
        inSeasonBonus = if (seasonMonth == null) {
            { 0.0 }
        } else {
            { recipe -> inSeasonBonus(recipe, seasonMonth, calendar) }
        },
        ratings = ratings,
    )
}

/**
 * El contexto de Hoy para [day] y [moment] con el tiempo de ahora ([current]; null sin datos: solo la estación).
 * `now` es el principio del día: el motor solo usa la fecha.
 */
fun todayContext(
    day: LocalDate,
    moment: Moment,
    latitude: Double?,
    thresholds: ThermalThresholds,
    current: CurrentWeather?,
): TodayContext = TodayContext(
    now = day.atStartOfDay(),
    moment = moment,
    season = seasonFor(day, latitude),
    thermal = current?.let { thermalClass(it.apparentC, thresholds) },
    weatherKind = current?.let { weatherKind(it.weatherCode) },
    apparentC = current?.apparentC,
    rainingNow = current != null && current.precipitationMm > 0,
    weekend = day.isWeekend(),
    isDay = current?.isDay,
)

/**
 * El contexto para un hueco del menú de la semana con la previsión de ese día ([daily]; si falta o es de otro día,
 * solo la estación). La previsión no da la sensación térmica: la comida usa la máxima y el desayuno y la cena, la
 * media del día.
 */
fun dayContext(
    day: LocalDate,
    moment: Moment,
    latitude: Double?,
    thresholds: ThermalThresholds,
    daily: DailyForecast?,
): TodayContext {
    val forecast = daily?.takeIf { it.date == day }
    val temperature = forecast?.let { if (moment == Moment.LUNCH) it.maxC else (it.maxC + it.minC) / 2 }
    return TodayContext(
        now = day.atStartOfDay(),
        moment = moment,
        season = seasonFor(day, latitude),
        thermal = temperature?.let { thermalClass(it, thresholds) },
        weatherKind = forecast?.let { weatherKind(it.weatherCode) },
        apparentC = temperature,
        rainingNow = false,
        weekend = day.isWeekend(),
        isDay = null,
    )
}

private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
