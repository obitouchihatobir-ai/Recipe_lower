package app.recetario.recipes.editor

import app.recetario.core.text.normalize

/**
 * Añade a [tags] lo escrito en el campo de etiquetas (separado por comas), sin vacías ni repetidas
 * (sin distinguir tildes ni mayúsculas). Si no añade nada devuelve la misma lista.
 */
fun addTagsFromInput(tags: List<String>, input: String): List<String> {
    var next = tags
    for (tag in input.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
        if (next.none { normalize(it) == normalize(tag) }) next = next + tag
    }
    return next
}
