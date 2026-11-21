package app.recetario.plan

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", SPANISH)

/** «Desayuno», «Comida» o «Cena». */
val MealSlot.label: String
    get() = when (this) {
        MealSlot.BREAKFAST -> "Desayuno"
        MealSlot.LUNCH -> "Comida"
        MealSlot.DINNER -> "Cena"
    }

/** «Lunes 16». */
fun weekDayLabel(date: LocalDate): String =
    WEEKDAY.format(date).replaceFirstChar { it.uppercase(SPANISH) } + " " + date.dayOfMonth

fun servingsLabel(servings: Int): String = if (servings == 1) "1 porción" else "$servings porciones"

/** Lo que lee TalkBack de un hueco: «Lunes 16, comida: Arroz con tomate, 4 porciones» o «Lunes 16, cena: vacío». */
fun slotDescription(date: LocalDate, slot: MealSlot, title: String?, servings: Int): String {
    val head = "${weekDayLabel(date)}, ${slot.label.lowercase(SPANISH)}"
    return if (title == null) "$head: vacío" else "$head: $title, ${servingsLabel(servings)}"
}

/** El aviso de «Rellenar con sugerencias». */
fun filledMessage(count: Int): String = when (count) {
    0 -> "No se añadió ninguna comida"
    1 -> "Se añadió 1 comida"
    else -> "Se añadieron $count comidas"
}

/** El aviso de «Generar lista de compra». */
fun weekListMessage(count: Int): String = when (count) {
    0 -> "No falta nada para esta semana"
    1 -> "✓ 1 ingrediente añadido"
    else -> "✓ $count ingredientes añadidos"
}
