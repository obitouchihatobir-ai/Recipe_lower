package app.recetario.today

import app.recetario.diary.CookStats
import app.recetario.pairing.PairRole
import app.recetario.pairing.PairingItem
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.weather.WeatherKind
import java.time.LocalDateTime

/** Una receta propia con lo que el motor de Hoy necesita. */
data class TodayRecipe(
    val id: String,
    val title: String,
    val role: PairRole,
    val course: Course?,
    val flavor: FlavorProfile,
    val serveTemp: ServeTemp?,
    val moments: Set<Moment>,
    val totalMinutes: Int?,
    val favorite: Boolean,
    val alcoholic: Boolean?,
    val ingredientNames: List<String>,
    val coverPhoto: String?,
)

fun TodayRecipe.asPairingItem(): PairingItem = PairingItem(
    kind = "RECIPE",
    id = id,
    name = title,
    role = role,
    course = course,
    alcoholic = alcoholic,
    flavor = flavor,
    serveTemp = serveTemp,
)

/**
 * El momento, la estación y, si hay datos del tiempo, la clase térmica, el tipo de tiempo y la temperatura aparente.
 * Sin clima, [thermal], [weatherKind] y [apparentC] son null y [rainingNow] es false. [isDay] dice si el sol está
 * arriba (null si no se sabe): con false no se habla del sol.
 */
data class TodayContext(
    val now: LocalDateTime,
    val moment: Moment,
    val season: Season,
    val thermal: Thermal?,
    val weatherKind: WeatherKind?,
    val apparentC: Double?,
    val rainingNow: Boolean,
    val weekend: Boolean,
    val isDay: Boolean? = null,
)

/**
 * Recetas, estadísticas del diario por id de receta, la despensa, los básicos (no cuentan en la cobertura) y el empujón
 * de temporada (vacío hasta la 2D). [ratings] son las valoraciones de maridaje (👍 true, 👎 false) por `pairKey`.
 */
data class TodayInputs(
    val recipes: List<TodayRecipe>,
    val stats: Map<String, CookStats>,
    val pantry: List<String>,
    val basics: List<String>,
    val inSeasonBonus: (TodayRecipe) -> Double = { 0.0 },
    val ratings: Map<String, Boolean> = emptyMap(),
)

/**
 * El menú de hoy. Comida y cena: [main], [drink] y [dessert]. Desayuno y merienda: [main] (el plato) y [drink].
 * Noche: [drink] y [snack]. Un hueco sin recetas que lo llenen queda en null. [reason] explica la elección.
 */
data class TodayMenu(
    val moment: Moment,
    val main: TodayRecipe?,
    val drink: TodayRecipe?,
    val dessert: TodayRecipe?,
    val snack: TodayRecipe?,
    val reason: String,
)

/**
 * La receta que manda en el menú, la que se sortea y la que «Otra idea» no repite: el plato principal (de noche, la
 * bebida); si falta, la primera receta del menú.
 */
val TodayMenu.lead: TodayRecipe?
    get() = if (moment == Moment.NIGHT) drink ?: snack else main ?: drink ?: dessert

/**
 * Los ids ya mostrados después de este menú, en el orden en que salieron (el último es el que se ve ahora), para la
 * siguiente «Otra idea»: se añade [lead]; si ya estaba (se habían acabado las opciones y empezó otra vuelta), la vuelta
 * nueva empieza solo con él. Al empezar otra vuelta, `planToday` no repite el último.
 */
fun TodayMenu.nextExclude(previous: List<String>): List<String> {
    val id = lead?.id ?: return previous
    return if (id in previous) listOf(id) else previous + id
}
