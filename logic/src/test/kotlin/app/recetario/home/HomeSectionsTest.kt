package app.recetario.home

import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.RecipeSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class HomeSectionsTest {
    private fun r(id: String, title: String = id, updatedAt: Long = 0, favorite: Boolean = false) = RecipeSummary(
        id = id, title = title, categoryId = "cat-comidas", coverPhoto = null, totalMinutes = null,
        flavor = FlavorProfile(), serveTemp = null, moments = emptySet(), alcoholic = null,
        difficulty = Difficulty.EASY, favorite = favorite, tags = emptyList(), ingredientNames = emptyList(),
        updatedAt = Instant.ofEpochSecond(updatedAt),
    )

    @Test
    fun greeting_followsTheClock() {
        assertThat(greetingFor(LocalTime.of(5, 59))).isEqualTo("Buenas noches")
        assertThat(greetingFor(LocalTime.of(6, 0))).isEqualTo("Buenos días")
        assertThat(greetingFor(LocalTime.of(11, 59))).isEqualTo("Buenos días")
        assertThat(greetingFor(LocalTime.of(12, 0))).isEqualTo("Buenas tardes")
        assertThat(greetingFor(LocalTime.of(19, 59))).isEqualTo("Buenas tardes")
        assertThat(greetingFor(LocalTime.of(20, 0))).isEqualTo("Buenas noches")
        assertThat(greetingFor(LocalTime.MIDNIGHT)).isEqualTo("Buenas noches")
    }

    @Test
    fun recent_areTheSixLatestByUpdatedAt() {
        val all = (1..8).map { r("r$it", updatedAt = it.toLong()) }.shuffled()
        assertThat(recentRecipes(all).map { it.id }).containsExactly("r8", "r7", "r6", "r5", "r4", "r3").inOrder()
    }

    @Test
    fun recent_tiesAreAlphabetical() {
        val all = listOf(r("b", "Ñoquis", 5), r("a", "Arroz", 5), r("c", "Ensalada", 5))
        assertThat(recentRecipes(all).map { it.title }).containsExactly("Arroz", "Ensalada", "Ñoquis").inOrder()
    }

    @Test
    fun favorites_onlyFavorites_alphabetical_ignoringAccents() {
        val all = listOf(r("1", "Tarta", favorite = true), r("2", "Ámbar", favorite = true), r("3", "Bizcocho"))
        assertThat(favoriteRecipes(all).map { it.title }).containsExactly("Ámbar", "Tarta").inOrder()
    }
}
