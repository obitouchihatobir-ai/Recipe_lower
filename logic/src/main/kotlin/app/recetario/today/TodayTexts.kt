package app.recetario.today

import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.weather.WeatherKind
import kotlin.math.roundToLong

/** «Hace 9 °C y llueve» (de noche y en la cena no se habla del sol). */
private fun weatherPart(ctx: TodayContext, apparentC: Double): String {
    val daytime = ctx.moment == Moment.BREAKFAST || ctx.moment == Moment.LUNCH || ctx.moment == Moment.SNACK
    val what = when {
        ctx.weatherKind == WeatherKind.STORM -> " y hay tormenta"
        ctx.weatherKind == WeatherKind.SNOW -> " y nieva"
        ctx.rainingNow || ctx.weatherKind == WeatherKind.RAIN -> " y llueve"
        ctx.weatherKind == WeatherKind.FOG -> " y hay niebla"
        ctx.weatherKind == WeatherKind.CLOUDY -> " y está nublado"
        ctx.weatherKind == WeatherKind.CLEAR && daytime -> " y hay sol"
        else -> ""
    }
    return "Hace ${apparentC.roundToLong()} °C$what"
}

private fun mealName(moment: Moment) = if (moment == Moment.DINNER) "cena" else "comida"

private fun momentIdea(moment: Moment): String = when (moment) {
    Moment.BREAKFAST -> "algo para empezar el día"
    Moment.LUNCH -> "una idea para la comida"
    Moment.SNACK -> "algo para la merienda"
    Moment.DINNER -> "una idea para la cena"
    Moment.NIGHT -> "algo para la noche"
}

private fun String.capitalized() = replaceFirstChar { it.uppercase() }

/**
 * La línea que explica el menú de hoy, a partir del contexto y de la receta que manda ([lead]). Nunca contradice al
 * plato: solo dice «caliente» si se sirve caliente y «fresco» si se sirve frío.
 * - Con clima: «Hace 9 °C y llueve: algo caliente y reconfortante», «Hace 31 °C y hay sol: algo fresco»,
 *   «Hace 20 °C: algo rápido para la cena» o «Hace 20 °C: una idea para la comida».
 * - Sin clima: «Es temporada de lluvias: algo calientito», «Cena de entre semana: algo rápido»,
 *   «Es fin de semana: hay tiempo para cocinar» o «Una idea para la comida».
 */
fun todayReason(ctx: TodayContext, lead: TodayRecipe?): String {
    val hot = lead?.serveTemp == ServeTemp.HOT
    val cold = lead?.serveTemp == ServeTemp.COLD
    val meal = ctx.moment == Moment.LUNCH || ctx.moment == Moment.DINNER
    val minutes = lead?.totalMinutes
    val quick = meal && !ctx.weekend && minutes != null && minutes <= QUICK_MAX_MINUTES
    val apparentC = ctx.apparentC

    if (apparentC != null) {
        val raining = ctx.rainingNow || ctx.weatherKind == WeatherKind.RAIN || ctx.weatherKind == WeatherKind.STORM
        val wish = when {
            ctx.thermal == Thermal.COLD && hot -> "algo caliente y reconfortante"
            ctx.thermal == Thermal.HOT && cold -> "algo fresco"
            ctx.thermal == Thermal.MILD && raining && hot -> "algo calientito"
            quick -> "algo rápido para la ${mealName(ctx.moment)}"
            else -> momentIdea(ctx.moment)
        }
        return "${weatherPart(ctx, apparentC)}: $wish"
    }

    return when {
        ctx.season == Season.RAINY && hot -> "Es temporada de lluvias: algo calientito"
        ctx.season == Season.WINTER && hot -> "Es invierno: algo calientito"
        ctx.season == Season.SUMMER && cold -> "Es verano: algo fresco"
        quick -> "${mealName(ctx.moment).capitalized()} de entre semana: algo rápido"
        meal && ctx.weekend && minutes != null && minutes > QUICK_MAX_MINUTES -> "Es fin de semana: hay tiempo para cocinar"
        else -> momentIdea(ctx.moment).capitalized()
    }
}
