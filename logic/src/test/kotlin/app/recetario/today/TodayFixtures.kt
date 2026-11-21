package app.recetario.today

import app.recetario.pairing.PairRole
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.weather.WeatherKind
import java.time.LocalDateTime

/** Miércoles 18 de noviembre de 2026. */
internal val WEDNESDAY: LocalDateTime = LocalDateTime.of(2026, 11, 18, 14, 0)

/** Sábado 21 de noviembre de 2026. */
internal val SATURDAY: LocalDateTime = LocalDateTime.of(2026, 11, 21, 14, 0)

internal fun recipe(
    id: String,
    role: PairRole = PairRole.FOOD,
    course: Course? = null,
    sweet: Int = 0,
    salty: Int = 0,
    sour: Int = 0,
    bitter: Int = 0,
    spicy: Int = 0,
    intensity: Intensity = Intensity.MEDIUM,
    rich: Boolean = false,
    serveTemp: ServeTemp? = null,
    moments: Set<Moment> = emptySet(),
    minutes: Int? = null,
    favorite: Boolean = false,
    alcoholic: Boolean? = null,
    ingredients: List<String> = emptyList(),
    title: String = id,
) = TodayRecipe(
    id = id,
    title = title,
    role = role,
    course = course,
    flavor = FlavorProfile(sweet, salty, sour, bitter, spicy, intensity, rich),
    serveTemp = serveTemp,
    moments = moments,
    totalMinutes = minutes,
    favorite = favorite,
    alcoholic = alcoholic,
    ingredientNames = ingredients,
    coverPhoto = null,
)

internal fun ctx(
    now: LocalDateTime = WEDNESDAY,
    moment: Moment = Moment.LUNCH,
    season: Season = Season.DRY,
    thermal: Thermal? = null,
    weatherKind: WeatherKind? = null,
    apparentC: Double? = null,
    rainingNow: Boolean = false,
    weekend: Boolean = now.dayOfWeek.value >= 6,
    isDay: Boolean? = null,
) = TodayContext(now, moment, season, thermal, weatherKind, apparentC, rainingNow, weekend, isDay)

internal fun inputs(vararg recipes: TodayRecipe) = TodayInputs(
    recipes = recipes.toList(),
    stats = emptyMap(),
    pantry = emptyList(),
    basics = emptyList(),
)
