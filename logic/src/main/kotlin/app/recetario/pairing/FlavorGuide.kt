package app.recetario.pairing

import app.recetario.pantry.pantryCovers
import app.recetario.pantry.samePantryName
import app.recetario.pantry.wordsWithin
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Un ingrediente de la guía y los sabores que le van bien. */
@Serializable
data class FlavorEntry(val ingredient: String, val goesWith: List<String>)

/** Guía de sabores incluida (`pairing/flavors.json`): «Le iría bien…» en el detalle. */
object FlavorGuide {
    val entries: List<FlavorEntry> by lazy {
        readResource("pairing/flavors.json") { json.decodeFromString(ListSerializer(FlavorEntry.serializer()), it) }
    }

    /**
     * Hasta [limit] sabores que van bien con los ingredientes de la receta y que todavía no lleva. Un ingrediente de
     * la guía cuenta si todas sus palabras están, completas, en un ingrediente de la receta («chile poblano» en
     * «chiles poblanos asados»; «maíz» no está en «maizena»). Se toman por turnos, uno de cada ingrediente.
     */
    fun goesWellWith(ingredientNames: List<String>, limit: Int = 6): List<String> {
        val names = ingredientNames.filter { it.isNotBlank() }
        val matched = names.flatMap { name -> entries.filter { wordsWithin(it.ingredient, name) } }.distinct()
        val result = mutableListOf<String>()
        val longest = matched.maxOfOrNull { it.goesWith.size } ?: 0
        for (i in 0 until longest) {
            for (entry in matched) {
                if (result.size >= limit) return result
                val flavor = entry.goesWith.getOrNull(i) ?: continue
                if (names.any { pantryCovers(flavor, it) }) continue
                if (result.any { samePantryName(it, flavor) }) continue
                result += flavor
            }
        }
        return result
    }
}
