package app.recetario.pairing

import app.recetario.core.text.normalize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlavorGuideTest {
    @Test
    fun flavors_atLeast80_entries() {
        val entries = FlavorGuide.entries
        assertThat(entries.size).isAtLeast(80)
        val names = entries.map { normalize(it.ingredient) }
        assertThat(names.toSet()).hasSize(entries.size)
        for (wanted in listOf(
            "chile poblano", "chipotle", "epazote", "cilantro", "nopal", "maiz", "frijol", "aguacate",
            "tomate verde", "piloncillo", "canela", "vainilla", "calabaza",
        )) assertThat(names).contains(wanted)
        for (e in entries) {
            assertThat(e.goesWith.size).isAtLeast(3)
            assertThat(e.goesWith.map { normalize(it) }).doesNotContain(normalize(e.ingredient))
        }
    }

    @Test
    fun goesWellWith_excludesWhatRecipeHas() {
        val result = FlavorGuide.goesWellWith(listOf("Calabaza de Castilla", "canela en raja", "2 tazas de leche"))
        assertThat(result).isNotEmpty()
        assertThat(result.size).isAtMost(6)
        assertThat(result.map { normalize(it) }).containsNoneOf("canela", "calabaza", "leche")
        assertThat(result.map { normalize(it) }.toSet()).hasSize(result.size)
        assertThat(FlavorGuide.goesWellWith(listOf("calabaza"), limit = 2)).hasSize(2)
    }

    @Test
    fun goesWellWith_wholeWordsOnly() {
        // «sal» no es «salsa» ni «maíz» es «maizena».
        assertThat(FlavorGuide.goesWellWith(listOf("maizena"))).isEmpty()
        assertThat(FlavorGuide.goesWellWith(emptyList())).isEmpty()
        assertThat(FlavorGuide.goesWellWith(listOf("chiles poblanos asados"))).isNotEmpty()
    }

    @Test
    fun goesWellWith_mixesIngredients() {
        // Cada ingrediente aporta antes de repetir el primero.
        val result = FlavorGuide.goesWellWith(listOf("aguacate", "chocolate"), limit = 2)
        val aguacate = FlavorGuide.entries.first { normalize(it.ingredient) == "aguacate" }.goesWith
        val chocolate = FlavorGuide.entries.first { normalize(it.ingredient) == "chocolate" }.goesWith
        assertThat(result[0]).isIn(aguacate)
        assertThat(result[1]).isIn(chocolate)
    }
}
