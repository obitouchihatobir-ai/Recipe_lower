package app.recetario.settings

import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import java.text.BreakIterator

/** Colores que se pueden elegir para una categoría: (nombre para el lector de pantalla, hexadecimal). */
val categoryColors: List<Pair<String, String>> = listOf(
    "Terracota" to "#C8553D",
    "Ámbar" to "#B5733A",
    "Mostaza" to "#D1A23A",
    "Verde" to "#6B8E4E",
    "Turquesa" to "#2A7F8E",
    "Azul" to "#4A6FA5",
    "Morado" to "#8A5A9E",
    "Frambuesa" to "#A8526E",
)

const val DEFAULT_CATEGORY_EMOJI = "🍽️"

/** Solo el último carácter visible: al escribir un emoji sobre otro, se queda el nuevo (un emoji combinado cuenta como uno). */
fun lastGrapheme(text: String): String {
    if (text.isEmpty()) return ""
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    val end = it.last()
    val start = it.previous()
    return if (start == BreakIterator.DONE) text else text.substring(start, end)
}

/** La categoría de serie que recibe las recetas de una borrada: la del mismo tipo base si sigue ahí. */
fun defaultReassignTarget(deleted: Category, others: List<Category>): Category? {
    val preferred = when (deleted.baseType) {
        BaseType.DRINK -> DefaultCategories.DRINK_ID
        BaseType.DESSERT -> DefaultCategories.DESSERT_ID
        BaseType.FOOD, BaseType.OTHER -> DefaultCategories.FOOD_ID
    }
    return others.firstOrNull { it.id == preferred } ?: others.firstOrNull()
}
