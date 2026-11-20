package app.recetario.home

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.diary.CookLogEntry
import app.recetario.navigation.AppNavHost
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import app.recetario.testing.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Inicio: la sección «Hace tiempo que no la haces» (diseño §7.1). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private val today: LocalDate = LocalDate.now(testClock)

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    private fun recipes(vararg titles: Pair<String, String>) = runBlocking {
        titles.forEachIndexed { i, (id, title) -> container.recipeRepository.save(sampleRecipe(id, title, updatedAt = i.toLong())) }
    }

    private fun cooked(recipeId: String, title: String, daysAgo: Long) = runBlocking {
        container.diaryRepository.save(
            CookLogEntry("log-$recipeId-$daysAgo", recipeId, title, today.minusDays(daysAgo), null, "", emptyList(), Instant.EPOCH, Instant.EPOCH),
        )
    }

    private fun launch() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }
        rule.waitFor(hasTestTag("home-diary"))
    }

    @Test
    fun longTimeNoCook_sectionShowsOldCooked() {
        recipes("old" to "Pozole rojo", "recent" to "Tacos al pastor", "never" to "Flan napolitano")
        cooked("old", "Pozole rojo", daysAgo = 45)
        cooked("recent", "Tacos al pastor", daysAgo = 5)
        launch()

        // La sección llega con los mismos datos que las recientes.
        rule.waitFor(hasText("Flan napolitano"))
        rule.onNodeWithTag("home-grid").performScrollToNode(hasText("Hace tiempo que no la haces"))
        rule.onNodeWithTag("longtime-old").assertExists()
        rule.onNodeWithTag("longtime-recent").assertDoesNotExist()
        rule.onNodeWithTag("longtime-never").assertDoesNotExist()
    }

    @Test
    fun longTimeNoCook_hiddenWhenEmpty() {
        recipes("recent" to "Tacos al pastor", "never" to "Flan napolitano")
        cooked("recent", "Tacos al pastor", daysAgo = 5)
        launch()

        rule.waitFor(hasText("Tacos al pastor"))
        rule.waitFor(hasText("Favoritas"))
        rule.onNodeWithText("Hace tiempo que no la haces").assertDoesNotExist()
    }
}
