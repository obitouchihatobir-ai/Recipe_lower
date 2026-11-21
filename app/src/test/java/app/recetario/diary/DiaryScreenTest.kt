package app.recetario.diary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.RecipeDetail
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Pantalla Diario: cronología por meses, borrar con «Deshacer» y entradas sin receta (diseño §7.1). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DiaryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        container = installTestContainer()
        runBlocking {
            container.recipeRepository.save(sampleRecipe("r1", "Crema de calabaza"))
            container.recipeRepository.save(sampleRecipe("r2", "Tarta de queso"))
        }
    }

    @After
    fun tearDown() = container.database.close()

    private fun log(
        id: String,
        recipeId: String?,
        title: String,
        date: LocalDate,
        photos: List<String> = emptyList(),
        note: String = "",
    ) = runBlocking {
        container.diaryRepository.save(CookLogEntry(id, recipeId, title, date, 4, note, photos, Instant.EPOCH, Instant.EPOCH))
    }

    private fun open() {
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav) }
        }
        rule.waitFor(hasTestTag("home-diary"))
        rule.onNodeWithTag("home-diary").performClick()
        rule.waitFor(hasTestTag("diary-screen"))
    }

    private fun top(tag: String): Float = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top.value

    private fun entries() = runBlocking { container.diaryRepository.observeEntries().first() }

    @Test
    fun groupsByMonth_newestFirst() {
        log("oct", "r1", "Crema de calabaza", LocalDate.of(2026, 10, 20))
        log("nov-3", "r2", "Tarta de queso", LocalDate.of(2026, 11, 3))
        log("nov-10", "r1", "Crema de calabaza", LocalDate.of(2026, 11, 10))
        open()

        rule.waitFor(hasText("noviembre de 2026"))
        rule.onNodeWithText("octubre de 2026").assertIsDisplayed()
        val november = top("month-2026-11")
        val october = top("month-2026-10")
        assertThat(november).isLessThan(top("diary-entry-nov-10"))
        assertThat(top("diary-entry-nov-10")).isLessThan(top("diary-entry-nov-3"))
        assertThat(top("diary-entry-nov-3")).isLessThan(october)
        assertThat(october).isLessThan(top("diary-entry-oct"))
    }

    /** TalkBack lee la valoración con la fila (no es una parada aparte dentro de la entrada). */
    @Test
    fun rating_isReadTogetherWithTheRow() {
        log("e1", "r1", "Crema de calabaza", LocalDate.of(2026, 11, 10))
        open()
        rule.waitFor(hasTestTag("diary-entry-e1"))
        rule.onNodeWithTag("diary-entry-e1").assert(hasContentDescription("4 de 5 estrellas"))
    }

    @Test
    fun delete_undo_restores() {
        val photo = container.photoStore.file("log.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        log("e1", "r1", "Crema de calabaza", LocalDate.of(2026, 11, 10), photos = listOf("log.jpg"), note = "Muy rica")
        open()
        rule.waitFor(hasTestTag("diary-entry-e1"))

        rule.onNodeWithTag("diary-entry-e1").performClick()
        rule.waitFor(hasTestTag("log-panel"))
        rule.onNodeWithTag("log-delete").performClick()

        rule.waitForGone(hasTestTag("diary-entry-e1"))
        rule.waitFor(hasText("Deshacer"))
        // Mientras se puede deshacer, la foto sigue en disco.
        assertThat(photo.exists()).isTrue()
        rule.onNodeWithText("Deshacer").performClick()

        rule.waitFor(hasTestTag("diary-entry-e1"))
        rule.waitUntil(5_000) { entries().isNotEmpty() }
        val restored = entries().single()
        assertThat(restored.photos).containsExactly("log.jpg")
        assertThat(restored.note).isEqualTo("Muy rica")
        assertThat(photo.exists()).isTrue()
    }

    @Test
    fun delete_withoutUndo_deletesItsPhotoWhenTheUndoExpires() {
        val photo = container.photoStore.file("log.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        log("e1", "r1", "Crema de calabaza", LocalDate.of(2026, 11, 10), photos = listOf("log.jpg"))
        open()
        rule.waitFor(hasTestTag("diary-entry-e1"))
        rule.onNodeWithTag("diary-entry-e1").performClick()
        rule.waitFor(hasTestTag("log-panel"))
        rule.onNodeWithTag("log-delete").performClick()
        rule.waitFor(hasText("Deshacer"))

        rule.waitForGone(hasText("Deshacer"), timeoutMillis = 20_000)
        rule.waitUntil(5_000) { !photo.exists() }
        assertThat(entries()).isEmpty()
    }

    @Test
    fun orphanEntry_notClickable() {
        log("orphan", "r2", "Tarta de queso", LocalDate.of(2026, 11, 10))
        log("normal", "r1", "Crema de calabaza", LocalDate.of(2026, 11, 3))
        runBlocking { container.recipeRepository.delete("r2") }
        open()
        rule.waitFor(hasTestTag("diary-entry-orphan"))

        // Sin receta: se lee con el título guardado y no lleva a ningún detalle.
        rule.onNodeWithTag("diary-title-orphan", useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Tarta de queso"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("diary-open-orphan").assertDoesNotExist()

        // La que tiene receta sí lleva a su detalle.
        rule.onNodeWithTag("diary-open-normal").performClick()
        rule.waitForGone(hasTestTag("diary-screen"))
        rule.runOnIdle { assertThat(nav.currentDestination?.hasRoute(RecipeDetail::class)).isTrue() }
    }
}
