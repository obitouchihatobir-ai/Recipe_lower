package app.recetario.recipes.search

import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.model.ServeTemp
import java.time.Instant
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeFilterTest {
    private fun r(
        id: String,
        title: String,
        categoryId: String = "cat-comidas",
        totalMinutes: Int? = 30,
        flavor: FlavorProfile = FlavorProfile(),
        serveTemp: ServeTemp? = null,
        moments: Set<Moment> = emptySet(),
        alcoholic: Boolean? = null,
        difficulty: Difficulty = Difficulty.EASY,
        favorite: Boolean = false,
        tags: List<String> = emptyList(),
        ingredients: List<String> = emptyList(),
        updatedAt: Long = 0,
    ) = RecipeSummary(
        id, title, categoryId, null, totalMinutes, flavor, serveTemp, moments, alcoholic,
        difficulty, favorite, tags, ingredients, Instant.ofEpochSecond(updatedAt),
    )

    private fun ids(list: List<RecipeSummary>) = list.map { it.id }

    @Test
    fun query_isAccentInsensitive() {
        val list = listOf(r("1", "Limonada con hierbabuena"), r("2", "Tarta"))
        assertThat(ids(list.applyFilter(RecipeFilter(query = "limon")))).isEqualTo(listOf("1"))
        assertThat(ids(list.applyFilter(RecipeFilter(query = "LIMÓN")))).isEqualTo(listOf("1"))
    }

    @Test
    fun query_matchesIngredient() {
        val list = listOf(r("1", "Crema de calabaza", ingredients = listOf("Calabaza", "Nata")), r("2", "Tarta"))
        assertThat(ids(list.applyFilter(RecipeFilter(query = "calabaza")))).isEqualTo(listOf("1"))
    }

    @Test
    fun query_wordsMayMatchDifferentFields() {
        val list = listOf(
            r("1", "Crema", ingredients = listOf("Calabaza"), tags = listOf("Invierno")),
            r("2", "Crema", ingredients = listOf("Puerro")),
        )
        assertThat(ids(list.applyFilter(RecipeFilter(query = "crema calabaza invierno")))).isEqualTo(listOf("1"))
        assertThat(list.applyFilter(RecipeFilter(query = "  ")).size).isEqualTo(2)
    }

    @Test
    fun classifyTaste() {
        assertThat(classifyTaste(2, 2)).isEqualTo(Taste.SWEET)
        assertThat(classifyTaste(2, 3)).isEqualTo(Taste.SALTY)
        assertThat(classifyTaste(1, 1)).isNull()
        assertThat(classifyTaste(3, 0)).isEqualTo(Taste.SWEET)
        assertThat(classifyTaste(0, 2)).isEqualTo(Taste.SALTY)
    }

    @Test
    fun filters_combineWithAnd() {
        val salty = FlavorProfile(salty = 3)
        val target = r("1", "Gazpacho", categoryId = "cat-bebidas", flavor = salty, alcoholic = false, serveTemp = ServeTemp.COLD)
        val list = listOf(
            target,
            r("2", "Vino", categoryId = "cat-bebidas", flavor = salty, alcoholic = true, serveTemp = ServeTemp.COLD),
            r("3", "Sopa", categoryId = "cat-comidas", flavor = salty, alcoholic = false, serveTemp = ServeTemp.COLD),
            r("4", "Batido", categoryId = "cat-bebidas", flavor = FlavorProfile(sweet = 3), alcoholic = false, serveTemp = ServeTemp.COLD),
            r("5", "Caldo", categoryId = "cat-bebidas", flavor = salty, alcoholic = false, serveTemp = ServeTemp.HOT),
            r("6", "Agua", categoryId = "cat-bebidas", flavor = salty, alcoholic = null, serveTemp = ServeTemp.COLD),
        )
        val f = RecipeFilter(
            categoryIds = setOf("cat-bebidas"), taste = Taste.SALTY, alcoholic = false, serveTemp = ServeTemp.COLD,
        )
        assertThat(ids(list.applyFilter(f))).isEqualTo(listOf("1"))
    }

    @Test
    fun maxMinutes_excludesUnknownTimes() {
        val list = listOf(r("1", "A", totalMinutes = 10), r("2", "B", totalMinutes = null), r("3", "C", totalMinutes = 90))
        assertThat(ids(list.applyFilter(RecipeFilter(maxMinutes = 30)))).isEqualTo(listOf("1"))
        assertThat(list.applyFilter(RecipeFilter()).size).isEqualTo(3)
    }

    @Test
    fun momentDifficultyTagsFavorites() {
        val list = listOf(
            r("1", "A", moments = setOf(Moment.DINNER), difficulty = Difficulty.HARD, tags = listOf("Rápida"), favorite = true),
            r("2", "B", moments = setOf(Moment.LUNCH)),
        )
        assertThat(ids(list.applyFilter(RecipeFilter(moment = Moment.DINNER)))).isEqualTo(listOf("1"))
        assertThat(ids(list.applyFilter(RecipeFilter(difficulty = Difficulty.HARD)))).isEqualTo(listOf("1"))
        assertThat(ids(list.applyFilter(RecipeFilter(tags = setOf("rapida", "otra"))))).isEqualTo(listOf("1"))
        assertThat(ids(list.applyFilter(RecipeFilter(favoritesOnly = true)))).isEqualTo(listOf("1"))
    }

    @Test
    fun sort_alphabetic_ignoresAccents() {
        val list = listOf(r("1", "Bizcocho"), r("2", "Ánade"))
        assertThat(ids(list.applyFilter(RecipeFilter(sort = SortOrder.ALPHABETIC)))).isEqualTo(listOf("2", "1"))
    }

    @Test
    fun sort_recent_newestFirst() {
        val list = listOf(r("1", "B", updatedAt = 5), r("2", "A", updatedAt = 9), r("3", "A2", updatedAt = 5))
        assertThat(ids(list.applyFilter(RecipeFilter()))).isEqualTo(listOf("2", "3", "1"))
    }
}
