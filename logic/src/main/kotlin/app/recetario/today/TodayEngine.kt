package app.recetario.today

import app.recetario.core.text.normalize
import app.recetario.pairing.LIKED_BONUS
import app.recetario.pairing.PairRole
import app.recetario.pairing.PairingItem
import app.recetario.pairing.pairKey
import app.recetario.pairing.scorePair
import app.recetario.pantry.pantryCovers
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.weather.WeatherKind
import java.time.temporal.ChronoUnit
import kotlin.random.Random

// Puntos de la receta que manda (diseño general §8.6). El azar (hasta NOISE) nunca supera una preferencia clara.
private const val WEATHER_POINTS = 20.0
private const val RICH_WARMTH = 0.4
private const val QUICK_POINTS = 10.0
internal const val QUICK_MAX_MINUTES = 40
private const val PANTRY_POINTS = 20.0
/** Hecha hoy, ayer o anteayer: penaliza. */
private const val RECENT_DAYS = 2L
private const val RECENT_PENALTY = 30.0
private const val LONG_AGO_DAYS = 30L
private const val LONG_AGO_POINTS = 10.0
private const val FAVORITE_POINTS = 10.0
private const val RATING_POINTS_PER_STAR = 4.0
private const val NOISE = 6.0

// Al elegir los acompañantes cuenta sobre todo que combinen.
private const val COMPANION_WEATHER_SHARE = 0.5
private const val COMPANION_FAVORITE_POINTS = 5.0

private enum class Slot { MAIN, DRINK, DESSERT, SNACK }

/** Los momentos de una receta sin momentos apuntados, según su tipo. */
private fun deducedMoments(role: PairRole): Set<Moment> = when (role) {
    PairRole.FOOD -> setOf(Moment.LUNCH, Moment.DINNER)
    PairRole.DRINK -> Moment.entries.toSet()
    PairRole.DESSERT -> setOf(Moment.LUNCH, Moment.DINNER, Moment.SNACK)
    PairRole.SIDE -> setOf(Moment.LUNCH, Moment.DINNER, Moment.NIGHT)
}

private fun TodayRecipe.fits(moment: Moment) = moment in moments.ifEmpty { deducedMoments(role) }

private fun TodayRecipe.isMainDish() = role == PairRole.FOOD && course != Course.STARTER && course != Course.SIDE

private fun TodayRecipe.isNibble() = role == PairRole.SIDE || course == Course.STARTER || course == Course.SIDE

private fun slotsFor(moment: Moment): List<Slot> = when (moment) {
    Moment.LUNCH, Moment.DINNER -> listOf(Slot.MAIN, Slot.DRINK, Slot.DESSERT)
    Moment.BREAKFAST, Moment.SNACK -> listOf(Slot.MAIN, Slot.DRINK)
    Moment.NIGHT -> listOf(Slot.DRINK, Slot.SNACK)
}

/**
 * Recetas que pueden ir en un hueco: las que valen para el momento y, si ninguna vale, lo que haya de ese tipo
 * (con pocas recetas se muestra lo que hay). Algo para picar de noche, en cambio, nunca es un plato fuerte.
 */
private fun pool(slot: Slot, moment: Moment, recipes: List<TodayRecipe>): List<TodayRecipe> {
    fun fitting(kind: (TodayRecipe) -> Boolean) = recipes.filter { kind(it) && it.fits(moment) }
    return when (slot) {
        Slot.MAIN -> if (moment == Moment.LUNCH || moment == Moment.DINNER) {
            fitting { it.isMainDish() }
                .ifEmpty { recipes.filter { it.isMainDish() } }
                .ifEmpty { recipes.filter { it.role == PairRole.FOOD } }
        } else {
            val plates = recipes.filter { it.role != PairRole.DRINK }
            fitting { it.role != PairRole.DRINK }
                .ifEmpty { plates.filter { it.role == PairRole.FOOD } }
                .ifEmpty { plates }
        }
        Slot.DRINK -> fitting { it.role == PairRole.DRINK }.ifEmpty { recipes.filter { it.role == PairRole.DRINK } }
            .let { drinks ->
                // En el desayuno y la merienda, nada con alcohol salvo que no haya otra bebida.
                if (moment == Moment.BREAKFAST || moment == Moment.SNACK) drinks.filter { it.alcoholic != true }.ifEmpty { drinks }
                else drinks
            }
        Slot.DESSERT -> fitting { it.role == PairRole.DESSERT }.ifEmpty { recipes.filter { it.role == PairRole.DESSERT } }
        Slot.SNACK -> fitting { it.role != PairRole.DRINK }
            .ifEmpty { recipes.filter { it.role != PairRole.DRINK && it.isNibble() } }
    }
}

/** Llueve ahora o el tiempo del momento es de lluvia o tormenta (lo mismo cuenta para elegir y para el porqué). */
internal val TodayContext.raining: Boolean
    get() = rainingNow || weatherKind == WeatherKind.RAIN || weatherKind == WeatherKind.STORM

/** Cuánto apetece lo caliente: 1 con frío, −1 con calor; sin clima, la mitad según la estación. */
private fun warmthWanted(ctx: TodayContext): Double = when (ctx.thermal) {
    Thermal.COLD -> 1.0
    Thermal.HOT -> -1.0
    Thermal.MILD -> if (ctx.raining) 0.5 else 0.0
    null -> when (ctx.season) {
        Season.RAINY, Season.WINTER -> 0.5
        Season.SUMMER -> -0.5
        else -> 0.0
    }
}

private fun TodayRecipe.warmth(): Double = when (serveTemp) {
    ServeTemp.HOT -> 1.0
    ServeTemp.COLD -> -1.0
    else -> 0.0
} + if (flavor.rich) RICH_WARMTH else 0.0

private fun weatherFit(r: TodayRecipe, ctx: TodayContext) = WEATHER_POINTS * warmthWanted(ctx) * r.warmth()

/** Parte de los ingredientes (sin contar los básicos) que hay en la despensa, de 0 a 1. */
private fun pantryShare(r: TodayRecipe, pantry: List<String>, basics: List<String>): Double {
    val needed = r.ingredientNames.filter { it.isNotBlank() && basics.none { b -> pantryCovers(b, it) } }
    if (needed.isEmpty()) return 0.0
    return needed.count { n -> pantry.any { pantryCovers(it, n) } }.toDouble() / needed.size
}

private fun leadScore(r: TodayRecipe, ctx: TodayContext, inputs: TodayInputs): Double {
    var points = weatherFit(r, ctx)
    val minutes = r.totalMinutes
    if (!ctx.weekend && (ctx.moment == Moment.LUNCH || ctx.moment == Moment.DINNER) && minutes != null) {
        points += if (minutes <= QUICK_MAX_MINUTES) QUICK_POINTS else -QUICK_POINTS
    }
    points += PANTRY_POINTS * pantryShare(r, inputs.pantry, inputs.basics)
    inputs.stats[r.id]?.let { s ->
        s.lastDate?.let { last ->
            val days = ChronoUnit.DAYS.between(last, ctx.now.toLocalDate())
            // Hecha hoy, ayer o anteayer (0 a 2 días): penaliza; hace 3 días ya no.
            if (days <= RECENT_DAYS) points -= RECENT_PENALTY
            else if (days >= LONG_AGO_DAYS) points += LONG_AGO_POINTS
        }
        s.averageRating?.let { points += (it - 3) * RATING_POINTS_PER_STAR }
    }
    if (r.favorite) points += FAVORITE_POINTS
    return points + inputs.inSeasonBonus(r)
}

private val byTitle = compareBy<TodayRecipe>({ normalize(it.title) }, { it.id })

/**
 * El menú de hoy (diseño general §8.6 y de la etapa 2 §7.3). La receta que manda ([lead]) se sortea entre las que valen
 * para el momento, con la hora, el clima o la estación, el día, la despensa, el diario (penaliza lo hecho hoy, ayer o
 * anteayer y sube lo que lleva 30 días o más sin hacerse), las favoritas y un poco de azar; las demás son las que mejor
 * combinan con lo ya elegido ([scorePair]): nunca forman una pareja con 👎 ([TodayInputs.ratings], por [pairKey]) y las
 * de 👍 suman [LIKED_BONUS]. [exclude] son los ids ya mostrados por «Otra idea», en el orden en que salieron
 * ([nextExclude]): si no queda ninguna opción fuera de ellos, empieza otra vuelta, que nunca abre con el último mostrado
 * (salvo que sea la única opción).
 */
fun planToday(ctx: TodayContext, inputs: TodayInputs, exclude: List<String>, random: Random): TodayMenu {
    val chosen = linkedMapOf<Slot, TodayRecipe>()
    for (slot in slotsFor(ctx.moment)) {
        val options = pool(slot, ctx.moment, inputs.recipes)
        if (options.isEmpty()) continue
        chosen[slot] = if (chosen.isEmpty()) {
            val fresh = options.filter { it.id !in exclude }.ifEmpty {
                val last = exclude.lastOrNull()
                options.filter { it.id != last }.ifEmpty { options }
            }
            // Se sortea en orden fijo: con la misma semilla, el mismo menú.
            fresh.sortedWith(byTitle)
                .map { it to leadScore(it, ctx, inputs) + random.nextDouble() * NOISE }
                .maxBy { it.second }.first
        } else {
            val partners = chosen.values.map { it.asPairingItem() }
            fun rating(a: PairingItem, b: PairingItem) = inputs.ratings[pairKey(a, b)]
            options.sortedWith(byTitle)
                .filter { o -> partners.none { rating(o.asPairingItem(), it) == false } }
                .maxByOrNull { o ->
                    val item = o.asPairingItem()
                    partners.sumOf { p ->
                        scorePair(item, p).score.toDouble() + if (rating(item, p) == true) LIKED_BONUS else 0
                    } + COMPANION_WEATHER_SHARE * weatherFit(o, ctx) +
                        if (o.favorite) COMPANION_FAVORITE_POINTS else 0.0
                } ?: continue
        }
    }
    val menu = TodayMenu(
        moment = ctx.moment,
        main = chosen[Slot.MAIN],
        drink = chosen[Slot.DRINK],
        dessert = chosen[Slot.DESSERT],
        snack = chosen[Slot.SNACK],
        reason = "",
    )
    return menu.copy(reason = todayReason(ctx, menu.lead))
}
