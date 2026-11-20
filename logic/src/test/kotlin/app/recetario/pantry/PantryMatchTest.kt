package app.recetario.pantry

import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.RecipeSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class PantryMatchTest {
    private fun r(id: String, title: String, vararg ingredients: String) = RecipeSummary(
        id, title, "cat", null, null, FlavorProfile(), null, emptySet(), null,
        Difficulty.EASY, false, emptyList(), ingredients.toList(), Instant.EPOCH,
    )

    private val basics = listOf("sal", "agua")

    @Test
    fun pantryMatch_wholeWordsOnly() {
        assertThat(pantryCovers("sal", "salsa de tomate")).isFalse()
        assertThat(pantryCovers("ajo", "ajonjolí")).isFalse()
        assertThat(pantryCovers("sal", "sal fina")).isTrue()
    }

    @Test
    fun pantryMatch_plurals() {
        assertThat(pantryCovers("tomates", "tomate saladet")).isTrue()
        assertThat(pantryCovers("tomate", "tomates")).isTrue()
        assertThat(pantryCovers("limones", "limón")).isTrue()
    }

    @Test
    fun pantryMatch_ignoresPunctuation() {
        assertThat(pantryCovers("cebolla", "cebolla, picada")).isTrue()
        assertThat(pantryCovers("tomate", "tomate (maduro)")).isTrue()
        assertThat(pantryCovers("chile", "chile-guajillo")).isTrue()
        assertThat(pantryCovers("sal", "salsa, de tomate")).isFalse()
    }

    @Test
    fun pantryItem_holdsFields() {
        val item = PantryItem("1", "Limón", "limon", Instant.EPOCH)
        assertThat(item.normalized).isEqualTo("limon")
    }

    @Test
    fun pantryMatch_accents() {
        assertThat(pantryCovers("limón", "limon")).isTrue()
    }

    @Test
    fun coverage_excludesBasics() {
        val c = pantryCoverage(listOf(r("1", "A", "Sal", "Agua", "Tomate")), listOf("tomate"), basics).single()
        assertThat(c.total).isEqualTo(1)
        assertThat(c.have).isEqualTo(1)
        assertThat(c.missing).isEmpty()
    }

    @Test
    fun coverage_order() {
        val list = listOf(
            r("half", "Aaa", "Tomate", "Cebolla"),
            r("full-b", "Bbb", "Tomate"),
            r("full-a", "Ábaco", "Tomate"),
            r("none", "Ccc", "Cebolla"),
        )
        val ids = pantryCoverage(list, listOf("tomate"), basics).map { it.recipeId }
        assertThat(ids).containsExactly("full-a", "full-b", "half", "none").inOrder()
    }

    @Test
    fun coverage_missingNames() {
        val c = pantryCoverage(listOf(r("1", "A", "Tomate", "Cebolla morada", "Sal")), listOf("tomates"), basics).single()
        assertThat(c.have).isEqualTo(1)
        assertThat(c.total).isEqualTo(2)
        assertThat(c.missing).containsExactly("Cebolla morada")
    }

    @Test
    fun coverage_skipsEmptyRecipes() {
        val list = listOf(r("1", "Sin nada"), r("2", "Solo básicos", "Sal", "Agua"), r("3", "Con", "Ajo"))
        assertThat(pantryCoverage(list, emptyList(), basics).map { it.recipeId }).containsExactly("3")
    }

    @Test
    fun defaultBasics() {
        assertThat(DEFAULT_PANTRY_BASICS).containsExactly("sal", "agua", "aceite", "pimienta", "azúcar").inOrder()
    }
}
