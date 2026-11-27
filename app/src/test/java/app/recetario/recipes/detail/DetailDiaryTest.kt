package app.recetario.recipes.detail

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.components.StarFillsKey
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.diary.CookLogEntry
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** El diario en el detalle: la línea de estadísticas, el historial y «Lo hice» (diseño §7.1). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DetailDiaryTest {
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
        runBlocking { container.recipeRepository.save(sampleRecipe("r1", "Crema de calabaza")) }
    }

    private fun launch() = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) {
            DetailRoute(container, "r1", onBack = {}, onEdit = {}, onCooking = {}, onShare = {})
        }
    }

    private fun log(id: String, daysAgo: Long, rating: Int?, note: String = "") = runBlocking {
        container.diaryRepository.save(
            CookLogEntry(
                id, "r1", "Crema de calabaza", today.minusDays(daysAgo), rating, note, emptyList(),
                Instant.EPOCH, Instant.EPOCH,
            ),
        )
    }

    private fun entries() = runBlocking { container.diaryRepository.observeEntries().first() }

    @Test
    fun detail_showsStatsLine() {
        launch()
        rule.waitFor(hasText("Aún no la has hecho"))

        log("a", daysAgo = 20, rating = 5, note = "Para la cena de los viernes")
        log("b", daysAgo = 14, rating = 4)

        val line = "La hiciste 2 veces · última hace 2 semanas"
        rule.waitFor(hasText(line))
        // La media (4,5) va en estrellas dibujadas, sin glifos que puedan faltar en la fuente («⯪»).
        rule.onNodeWithTag("detail-stars", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(StarFillsKey, listOf(1f, 1f, 1f, 1f, 0.5f)))
        rule.onAllNodes(hasText("★", substring = true) or hasText("⯪", substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
        // TalkBack no lee «estrella negra»: lee la media en palabras.
        rule.onNode(hasText(line), useUnmergedTree = true)
            .assert(hasContentDescription("La hiciste 2 veces · última hace 2 semanas · valoración media: 4,5 de 5 estrellas"))

        // Al tocarla, el historial con cada vez.
        rule.onNodeWithTag("detail-stats").performClick()
        rule.waitFor(hasTestTag("diary-history"))
        rule.onNodeWithText("Para la cena de los viernes", useUnmergedTree = true).assertExists()
        rule.onNode(hasContentDescription("5 de 5 estrellas"), useUnmergedTree = true).assertExists()
        rule.onNode(hasContentDescription("4 de 5 estrellas"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun loHice_createsEntryWithChosenDate() {
        launch()
        rule.waitFor(hasTestTag("detail-log"))
        rule.onNodeWithTag("detail-log").performClick()

        rule.waitFor(hasTestTag("log-panel"))
        rule.onNodeWithTag("log-date").assertTextEquals("Hoy")
        // No se permiten fechas futuras.
        rule.onNodeWithTag("log-date-next").assertIsNotEnabled()
        rule.onNodeWithTag("log-date-previous").performClick()
        rule.waitFor(hasText("Ayer"))
        rule.onNodeWithTag("log-date-previous").performClick()
        val chosen = today.minusDays(2)
        rule.waitFor(hasText(app.recetario.diary.logDateLabel(chosen, today)))
        rule.onNodeWithTag("diary-save").performClick()

        rule.waitForGone(hasTestTag("log-panel"))
        rule.waitUntil(5_000) { entries().isNotEmpty() }
        val entry = entries().single()
        assertThat(entry.date).isEqualTo(chosen)
        assertThat(entry.recipeId).isEqualTo("r1")
        assertThat(entry.recipeTitle).isEqualTo("Crema de calabaza")
        assertThat(entry.rating).isNull()
        rule.waitFor(hasText("La hiciste 1 vez · hace 2 días"))
    }

    @Test
    fun loHice_cancel_savesNothing() {
        launch()
        rule.waitFor(hasTestTag("detail-log"))
        rule.onNodeWithTag("detail-log").performClick()
        rule.waitFor(hasTestTag("log-panel"))
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForGone(hasTestTag("log-panel"))
        assertThat(entries()).isEmpty()
    }
}
