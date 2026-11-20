package app.recetario.pantry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PantryTextsTest {
    @Test
    fun key_normalizesAccentsAndPunctuation() {
        assertThat(pantryKey("  Limón, verde ")).isEqualTo("limon verde")
    }

    @Test
    fun sameName_pluralsAndAccents() {
        assertThat(samePantryName("Tomates", "tomate")).isTrue()
        assertThat(samePantryName("limón", "Limones")).isTrue()
        assertThat(samePantryName("tomate", "tomate verde")).isFalse()
        assertThat(samePantryName("sal", "salsa")).isFalse()
    }

    @Test
    fun suggestions_max6_noRepeats_notInPantry() {
        val ingredients = listOf(
            "Tomate", "tomates", "Tomate verde", "Tomillo", "Atún", "Tortilla", "Tocino", "Tofu",
            "Toronja", "Tostadas", "Cebolla",
        )
        val out = pantrySuggestions("to", ingredients, pantry = listOf("tofu"))
        assertThat(out).hasSize(6)
        assertThat(out).containsNoDuplicates()
        assertThat(out).doesNotContain("tomates")
        assertThat(out).doesNotContain("Tofu")
        assertThat(out).doesNotContain("Cebolla")
        // Primero los que empiezan por lo escrito, en orden alfabético.
        assertThat(out.first()).isEqualTo("Tocino")
    }

    @Test
    fun suggestions_emptyQuery_none() {
        assertThat(pantrySuggestions(" ", listOf("Tomate"), emptyList())).isEmpty()
    }

    @Test
    fun suggestions_wordInside_afterPrefixes() {
        val out = pantrySuggestions("verd", listOf("Tomate verde", "Verdolagas"), emptyList())
        assertThat(out).containsExactly("Verdolagas", "Tomate verde").inOrder()
    }

    @Test
    fun coverageLine_missingUpTo3() {
        assertThat(coverageLine(PantryCoverage("1", 5, 6, listOf("cilantro")))).isEqualTo("Tienes 5 de 6 · falta: cilantro")
        assertThat(coverageLine(PantryCoverage("1", 0, 4, listOf("a", "b", "c", "d"))))
            .isEqualTo("Tienes 0 de 4 · falta: a, b, c…")
        assertThat(coverageLine(PantryCoverage("1", 0, 3, listOf("a", "b", "c"))))
            .isEqualTo("Tienes 0 de 3 · falta: a, b, c")
    }

    @Test
    fun coverageLine_all() {
        assertThat(coverageLine(PantryCoverage("1", 3, 3, emptyList()))).isEqualTo("¡Tienes todo!")
    }
}
