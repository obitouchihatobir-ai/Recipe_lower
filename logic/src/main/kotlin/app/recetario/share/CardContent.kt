package app.recetario.share

import app.recetario.core.text.normalize
import app.recetario.recipes.detail.shareIngredientLine
import app.recetario.recipes.detail.shareMetaLine
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Recipe

const val CARD_FOOTER = "Compartido desde Sazón"

/** Lo que dice la ficha, ya escalado a las porciones elegidas y filtrado por las opciones. */
data class CardContent(
    val title: String,
    /** «35 min · 4 porciones · Fácil». */
    val servingsLine: String,
    /** «Calabaza — 1,2 kg», sin viñeta (la pone la maquetación). */
    val ingredients: List<String>,
    /** Textos de los pasos, sin número (vacía si la opción «Pasos» está apagada). */
    val steps: List<String>,
    val notes: String?,
    val flavorLine: String?,
    val emoji: String,
    /** La receta tiene foto y la opción «Foto» está encendida. */
    val hasPhoto: Boolean,
    val footer: String = CARD_FOOTER,
)

fun cardContent(recipe: Recipe, servings: Int, categoryEmoji: String, options: CardOptions): CardContent =
    CardContent(
        title = recipe.title,
        servingsLine = shareMetaLine(recipe, servings, clock = false),
        ingredients = recipe.ingredients.map { shareIngredientLine(it, recipe.servings, servings) },
        steps = if (options.steps) recipe.steps.map { it.text } else emptyList(),
        notes = recipe.notes.trim().takeIf { options.notes && it.isNotEmpty() },
        flavorLine = if (options.flavor) flavorLine(recipe.flavor) else null,
        emoji = categoryEmoji,
        hasPhoto = options.photo && recipe.coverPhoto != null,
    )

/** «Sabor: muy dulce y un poco ácido» (1 «un poco», 2 tal cual, 3 «muy»); null sin sabores. */
fun flavorLine(flavor: FlavorProfile): String? {
    if (!flavor.hasTastes()) return null
    val words = listOf(
        "dulce" to flavor.sweet, "salado" to flavor.salty, "ácido" to flavor.sour,
        "amargo" to flavor.bitter, "picante" to flavor.spicy,
    ).filter { it.second > 0 }.map { (word, level) ->
        when (level) {
            1 -> "un poco $word"
            2 -> word
            else -> "muy $word"
        }
    }
    val joined = if (words.size == 1) words[0] else words.dropLast(1).joinToString(", ") + " y " + words.last()
    return "Sabor: $joined"
}

private const val MAX_FILE_NAME = 60
private val notFileChars = Regex("[^a-z0-9]+")

/** `Sazon-crema-de-calabaza.png`: sin tildes, solo letras, números y guiones, como mucho 60 caracteres. */
fun cardFileName(title: String, format: CardFormat): String {
    val ext = "." + format.extension
    val prefix = "Sazon-"
    val slug = normalize(title).replace(notFileChars, "-").trim('-').ifEmpty { "receta" }
    val cut = slug.take(MAX_FILE_NAME - prefix.length - ext.length).trimEnd('-')
    return prefix + cut + ext
}
