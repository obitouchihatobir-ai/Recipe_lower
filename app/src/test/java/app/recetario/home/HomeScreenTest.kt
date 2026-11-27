package app.recetario.home

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.diary.CookLogEntry
import app.recetario.navigation.AppNavHost
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.installTestContainer
import app.recetario.weather.Place
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import app.recetario.testing.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
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
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private val today: LocalDate = LocalDate.now(testClock)

    @Before
    fun setUp() {
        container = installTestContainer()
    }

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
        // «Favoritas» puede quedar debajo de la tarjeta Hoy: se desplaza hasta el final de la cuadrícula.
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("home-grid").performScrollToNode(hasText("Favoritas")) }.isSuccess
        }
        rule.onNodeWithTag("home-grid").performScrollToNode(hasText("Marca una receta como favorita y la tendrás siempre a mano aquí."))
        rule.onNodeWithText("Hace tiempo que no la haces").assertDoesNotExist()
        // Tampoco más abajo: desplazarse hasta la sección falla.
        assertThrows(AssertionError::class.java) {
            rule.onNodeWithTag("home-grid").performScrollToNode(hasText("Hace tiempo que no la haces"))
        }
    }

    @Test
    fun todayCard_aboveChips_andWeatherAskedOnOpen() {
        // Una capital conocida y Open-Meteo de mentira (nunca hay red).
        val fake = FakeOpenMeteo()
        container.database.close()
        container = installTestContainer(openMeteo = fake)
        runBlocking { container.settingsRepository.setCity(Place("Madrid", "Comunidad de Madrid", "España", 40.4168, -3.7038)) }
        recipes("sopa" to "Sopa de tortilla", "tacos" to "Tacos al pastor")
        launch()

        rule.waitFor(hasTestTag("today-card"))
        rule.waitFor(hasTestTag("today-weather"))
        // La tarjeta va arriba, bajo el saludo, antes de los chips y de «Recientes».
        val card = rule.onNodeWithTag("today-card").getBoundsInRoot()
        val header = rule.onNodeWithTag("home-diary").getBoundsInRoot()
        assertThat(card.top).isGreaterThan(header.bottom - 1.dp)
        rule.waitFor(hasText("Recientes"))
        val recent = rule.onNodeWithText("Recientes").getBoundsInRoot()
        assertThat(recent.top).isGreaterThan(card.bottom - 1.dp)
        // Al entrar se pidió el tiempo una vez (en segundo plano).
        assertThat(fake.forecastRequests).hasSize(1)
    }
    @Test
    fun homeTeaser_opensSeason() {
        recipes("sopa" to "Sopa de tortilla")
        launch()

        // La tarjeta pequeña de temporada (el reloj de prueba es el 13 de noviembre).
        rule.waitFor(hasTestTag("home-season"))
        rule.onNodeWithTag("home-grid").performScrollToNode(hasTestTag("home-season"))
        rule.waitFor(hasText("De temporada en noviembre: guayaba, mandarina, calabaza…"))
        // Toda la tarjeta es un botón de 48 dp o más.
        assertThat(rule.onNodeWithTag("home-season").getBoundsInRoot().let { it.bottom - it.top }).isAtLeast(48.dp)
        rule.onNodeWithTag("home-season").performClick()

        rule.waitFor(hasTestTag("season-screen"))
        rule.waitFor(hasTestTag("season-month-11") and isSelected())
    }
}
