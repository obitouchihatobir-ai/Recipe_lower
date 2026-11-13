package app.recetario.recipes.editor

import app.recetario.core.text.normalize
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import kotlin.math.roundToInt

private const val TERM = """(\d+(?:[.,]\d+)?)\s*(horas?|hrs?|h|minutos?|mins?|segundos?|segs?|s)(?![a-z0-9])"""
private val firstTerm = Regex("""(?<![a-z0-9])$TERM""")
private val nextTerm = Regex("""\s*(?:y|e|,)?\s*$TERM""")

private fun termSeconds(match: MatchResult): Double {
    val amount = match.groupValues[1].replace(',', '.').toDouble()
    val unit = match.groupValues[2]
    val factor = when {
        unit.startsWith("h") -> 3600
        unit.startsWith("m") -> 60
        else -> 1
    }
    return amount * factor
}

/**
 * Duración de la PRIMERA expresión de tiempo del texto, en segundos. Suma horas, minutos y segundos
 * solo si van seguidos («1 h 30 min» → 5400). Null si no hay ninguna.
 */
fun detectDurationSeconds(text: String): Int? {
    val t = normalize(text)
    var match = firstTerm.find(t) ?: return null
    var total = termSeconds(match)
    while (true) {
        match = nextTerm.matchAt(t, match.range.last + 1) ?: break
        total += termSeconds(match)
    }
    return total.roundToInt().takeIf { it > 0 }
}

private fun mentions(text: String, phrase: String): Boolean =
    Regex("""(?<![a-z0-9])${Regex.escape(phrase)}(?:s|es)?(?![a-z0-9])""").containsMatchIn(text)

/** IDs de los ingredientes cuyo nombre completo, o su primera palabra de 4 letras o más, aparece en el paso. */
fun suggestStepIngredients(stepText: String, ingredients: List<Ingredient>): Set<String> {
    val text = normalize(stepText)
    return ingredients.filter { ing ->
        val name = normalize(ing.name)
        name.isNotEmpty() && (
            mentions(text, name) ||
                name.substringBefore(' ').let { it.length >= 4 && mentions(text, it) }
            )
    }.mapTo(LinkedHashSet()) { it.id }
}

/** Momentos y temperatura sugeridos según el tipo base de la categoría. */
fun defaultsFor(base: BaseType): Pair<Set<Moment>, ServeTemp> = when (base) {
    BaseType.FOOD -> setOf(Moment.LUNCH, Moment.DINNER) to ServeTemp.HOT
    BaseType.DRINK -> setOf(Moment.LUNCH, Moment.SNACK, Moment.DINNER) to ServeTemp.COLD
    BaseType.DESSERT -> setOf(Moment.LUNCH, Moment.SNACK, Moment.DINNER) to ServeTemp.ROOM
    BaseType.OTHER -> setOf(Moment.LUNCH, Moment.DINNER) to ServeTemp.ROOM
}

fun FlavorProfile.markSweet(): FlavorProfile = copy(sweet = maxOf(sweet, 2), salty = minOf(salty, 1))

fun FlavorProfile.markSalty(): FlavorProfile = copy(salty = maxOf(salty, 2), sweet = minOf(sweet, 1))
