package app.recetario.core.text

import java.text.Normalizer

private val diacritics = Regex("\\p{M}+")
private val spaces = Regex("\\s+")

/** Minúsculas, sin tildes y sin espacios sobrantes; sirve para búsquedas y para unificar etiquetas. */
fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(diacritics, "")
        .lowercase()
        .trim()
        .replace(spaces, " ")
