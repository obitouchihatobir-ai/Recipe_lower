package app.recetario.season

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")

/** «noviembre». */
fun seasonMonthName(month: Int): String {
    require(month in 1..12) { "Mes fuera de rango: $month" }
    return Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, SPANISH).lowercase(SPANISH)
}

const val SEASON_TEASER_COUNT = 3

/**
 * Tarjeta de Inicio: «De temporada en noviembre: guayaba, mandarina, calabaza…». Primero lo que está en su mejor
 * momento y, si no llegan a tres, lo que también hay; «…» solo si quedan más.
 */
fun seasonTeaser(month: Int, calendar: List<Produce>): String {
    val (best, available) = SeasonCalendar.forMonth(month, calendar)
    val all = best + available
    val head = "De temporada en ${seasonMonthName(month)}"
    if (all.isEmpty()) return head
    val names = all.take(SEASON_TEASER_COUNT).joinToString(", ") { it.name.replaceFirstChar { c -> c.lowercase(SPANISH) } }
    return "$head: $names" + if (all.size > SEASON_TEASER_COUNT) "…" else ""
}

/** «Noviembre»: el chip del mes en «De temporada». */
fun seasonMonthLabel(month: Int): String = seasonMonthName(month).replaceFirstChar { it.titlecase(SPANISH) }

/** «Sin recetas», «1 receta», «3 recetas»: cuántas recetas propias usan un producto. */
fun seasonRecipeCount(count: Int): String = when (count) {
    0 -> "Sin recetas"
    1 -> "1 receta"
    else -> "$count recetas"
}

/** El grupo de un producto en «De temporada». */
fun produceKindTitle(kind: ProduceKind): String = when (kind) {
    ProduceKind.FRUIT -> "Frutas"
    ProduceKind.VEGETABLE -> "Verduras"
    ProduceKind.CHILE -> "Chiles"
    ProduceKind.HERB -> "Hierbas"
}

/**
 * Lo que lee TalkBack de un producto: su nombre y sus recetas («Guayaba, 2 recetas»). Nunca el nombre del emoji, que a
 * menudo es otro (la guayaba usa 🍈, que se leería «melón»).
 */
fun produceDescription(produce: Produce, count: Int): String =
    "${produce.name}, ${seasonRecipeCount(count).replaceFirstChar { it.lowercase(SPANISH) }}"
