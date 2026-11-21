package app.recetario.today

import app.recetario.pairing.PairRole
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.RecipeSummary
import app.recetario.weather.WeatherKind
import java.time.LocalDate
import kotlin.math.roundToLong

/** El papel de una receta en Hoy y en el maridaje, según el tipo base de su categoría («Otro» es algo para acompañar). */
fun pairRoleOf(baseType: BaseType): PairRole = when (baseType) {
    BaseType.FOOD -> PairRole.FOOD
    BaseType.DRINK -> PairRole.DRINK
    BaseType.DESSERT -> PairRole.DESSERT
    BaseType.OTHER -> PairRole.SIDE
}

/** La receta para el motor de Hoy; sin categoría ([baseType] null) cuenta como comida. */
fun RecipeSummary.toTodayRecipe(baseType: BaseType?): TodayRecipe = TodayRecipe(
    id = id,
    title = title,
    role = pairRoleOf(baseType ?: BaseType.FOOD),
    course = course,
    flavor = flavor,
    serveTemp = serveTemp,
    moments = moments,
    totalMinutes = totalMinutes,
    favorite = favorite,
    alcoholic = alcoholic,
    ingredientNames = ingredientNames,
    coverPhoto = coverPhoto,
)

private fun Season.title(): String = when (this) {
    Season.DRY, Season.RAINY -> "Temporada de $label"
    else -> label.replaceFirstChar { it.uppercase() }
}

/** La línea de arriba de la tarjeta Hoy: «Cena de hoy · Otoño», «Para esta noche · Temporada de lluvias». */
fun todayEyebrow(moment: Moment, season: Season): String {
    val when_ = when (moment) {
        Moment.BREAKFAST -> "Desayuno de hoy"
        Moment.LUNCH -> "Comida de hoy"
        Moment.SNACK -> "Merienda de hoy"
        Moment.DINNER -> "Cena de hoy"
        Moment.NIGHT -> "Para esta noche"
    }
    return "$when_ · ${season.title()}"
}

/** Los huecos de la tarjeta, con lo que dicen cuando no hay ninguna receta que los llene. */
enum class TodaySlot(val missing: String) {
    MAIN("Aún no tienes platos"),
    DRINK("Aún no tienes bebidas"),
    DESSERT("Aún no tienes postres"),
    SNACK("Aún no tienes nada para picar"),
    ;

    /** «Principal · 35 min» en comida y cena; «Plato» en el desayuno y la merienda. */
    fun label(moment: Moment): String = when (this) {
        MAIN -> if (moment == Moment.LUNCH || moment == Moment.DINNER) "Principal" else "Plato"
        DRINK -> "Bebida"
        DESSERT -> "Postre"
        SNACK -> "Para picar"
    }
}

/** Los huecos de cada momento, en el orden de la tarjeta (los mismos que llena [planToday]). */
fun todaySlots(moment: Moment): List<TodaySlot> = when (moment) {
    Moment.LUNCH, Moment.DINNER -> listOf(TodaySlot.MAIN, TodaySlot.DRINK, TodaySlot.DESSERT)
    Moment.BREAKFAST, Moment.SNACK -> listOf(TodaySlot.MAIN, TodaySlot.DRINK)
    Moment.NIGHT -> listOf(TodaySlot.DRINK, TodaySlot.SNACK)
}

/** La receta del menú en ese hueco (o null). */
fun TodayMenu.recipeIn(slot: TodaySlot): TodayRecipe? = when (slot) {
    TodaySlot.MAIN -> main
    TodaySlot.DRINK -> drink
    TodaySlot.DESSERT -> dessert
    TodaySlot.SNACK -> snack
}

/** Icono del tiempo de ahora (de noche, con cielo despejado, la luna). */
fun weatherEmoji(kind: WeatherKind, isDay: Boolean): String = when (kind) {
    WeatherKind.CLEAR -> if (isDay) "☀️" else "🌙"
    WeatherKind.CLOUDY -> "☁️"
    WeatherKind.FOG -> "🌫️"
    WeatherKind.RAIN -> "🌧️"
    WeatherKind.STORM -> "⛈️"
    WeatherKind.SNOW -> "❄️"
}

/** «9 °C · Madrid» (sin ciudad, solo la temperatura). */
fun weatherChipText(apparentC: Double, city: String?): String =
    "${apparentC.roundToLong()} °C" + (city?.let { " · $it" } ?: "")

/** Lo que lee TalkBack: «El tiempo ahora: lluvia, 9 °C en Madrid». [label] es el de `weatherLabel`. */
fun weatherChipDescription(label: String, apparentC: Double, city: String?): String =
    "El tiempo ahora: ${label.lowercase()}, ${apparentC.roundToLong()} °C" + (city?.let { " en $it" } ?: "")

/**
 * La semilla del azar de Hoy: una por día y momento más el número de «Otra idea» ([round]). Así la propuesta no
 * cambia al girar la pantalla ni al volver a Inicio, pero sí cada día.
 */
fun todaySeed(date: LocalDate, moment: Moment, round: Int): Long =
    (date.toEpochDay() * 8 + moment.ordinal) * 1_000_003L + round
