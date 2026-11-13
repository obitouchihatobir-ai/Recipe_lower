package app.recetario.converter

import app.recetario.core.text.normalize

/** Densidades aproximadas (g/ml) de ingredientes habituales. */
object Densities {
    private val table: Map<String, Double> = mapOf(
        "harina" to 0.52,
        "azucar glas" to 0.5,
        "azucar" to 0.83,
        "arroz" to 0.79,
        "mantequilla" to 0.95,
        "leche" to 1.03,
        "agua" to 1.0,
        "aceite" to 0.92,
        "miel" to 1.42,
        "cacao" to 0.42,
        "avena" to 0.38,
        "sal" to 1.2,
    )

    /** La palabra clave más larga contenida (como palabra completa) en el nombre gana. */
    fun gramsPerMl(ingredient: String): Double? {
        val name = " ${normalize(ingredient)} "
        return table.entries
            .filter { name.contains(" ${it.key} ") }
            .maxByOrNull { it.key.length }
            ?.value
    }
}
