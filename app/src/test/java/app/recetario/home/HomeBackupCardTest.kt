package app.recetario.home

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.backup.BackupMessages
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.settings.BackupStatus
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
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
import java.time.Duration

/** Tarjeta de Inicio sobre las copias: el error de la copia automática o el recordatorio de los 30 días. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeBackupCardTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    /** Una receta apuntada hace mucho (la de prueba se creó en 1970). */
    private fun saveOldRecipe() = runBlocking { container.recipeRepository.save(sampleRecipe("tortilla", "Tortilla de patatas")) }

    private fun setStatus(status: BackupStatus) = runBlocking { container.settingsRepository.updateBackupStatus { status } }

    private fun status() = runBlocking { container.settingsRepository.backupStatus.first() }

    private fun launch() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }
        rule.waitFor(hasText("Tortilla de patatas"))
    }

    @Test
    fun reminder_afterMoreThan30DaysWithoutACopyOutside_opensBackups() {
        saveOldRecipe()
        setStatus(BackupStatus(lastExternal = testClock.instant().minus(Duration.ofDays(31))))
        launch()

        rule.waitFor(hasText("Hace más de 30 días que no guardas una copia fuera del teléfono."))
        rule.onNodeWithText("Hacer copia").performClick()

        rule.waitFor(hasTestTag("backup-screen"))
    }

    @Test
    fun reminder_whenThereWasNeverACopy_andTheFirstRecipeIsOld() {
        saveOldRecipe()
        launch()

        rule.waitFor(hasTestTag("home-backup-card"))
        rule.onNodeWithText(BackupMessages.REMINDER).assertExists()
    }

    @Test
    fun error_isShownEvenWithARecentCopy_andOpensBackups() {
        saveOldRecipe()
        setStatus(BackupStatus(lastExternal = testClock.instant(), lastError = BackupMessages.DRIVE_FAILED))
        launch()

        rule.waitFor(hasText("No se pudo guardar en Google Drive. Vuelve a elegir el archivo."))
        rule.onNodeWithText(BackupMessages.REMINDER).assertDoesNotExist()
        rule.onNodeWithText("Abrir copias").performClick()

        rule.waitFor(hasTestTag("backup-screen"))
    }

    @Test
    fun noCard_withARecentCopyOutside() {
        saveOldRecipe()
        setStatus(BackupStatus(lastExternal = testClock.instant().minus(Duration.ofDays(29))))
        launch()

        rule.waitForIdle()
        rule.onNodeWithTag("home-backup-card").assertDoesNotExist()
    }

    @Test
    fun noReminder_withoutRecipes() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }
        rule.waitFor(hasText("Aún no tienes recetas"))

        rule.onNodeWithTag("home-backup-card").assertDoesNotExist()
    }

    @Test
    fun localError_isShown_andOpensBackups() {
        saveOldRecipe()
        setStatus(BackupStatus(lastExternal = testClock.instant(), localError = BackupMessages.LOCAL_FAILED))
        launch()

        rule.waitFor(hasText("No se pudo hacer la copia semanal en el teléfono."))
        rule.onNodeWithText("Abrir copias").performClick()

        rule.waitFor(hasTestTag("backup-screen"))
    }

    @Test
    fun bothErrors_areShown_theLocalOneFirst() {
        saveOldRecipe()
        setStatus(BackupStatus(lastExternal = testClock.instant(), localError = BackupMessages.LOCAL_FAILED, lastError = BackupMessages.DRIVE_FAILED))
        launch()

        rule.waitFor(hasText(BackupMessages.LOCAL_FAILED))
        rule.waitFor(hasText(BackupMessages.DRIVE_FAILED))
        val local = rule.onNodeWithText(BackupMessages.LOCAL_FAILED).fetchSemanticsNode().boundsInRoot
        val drive = rule.onNodeWithText(BackupMessages.DRIVE_FAILED).fetchSemanticsNode().boundsInRoot
        assertThat(local.top).isLessThan(drive.top)
    }

    @Test
    fun dismiss_isSavedForTodayAndThatNotice_andAnotherNoticeShowsAgain() {
        saveOldRecipe()
        launch()
        rule.waitFor(hasTestTag("home-backup-card"))

        rule.onNodeWithTag("home-backup-dismiss").performClick()

        rule.waitForGone(hasTestTag("home-backup-card"))
        rule.waitUntil(5_000) { status().noticeDismissed != null }
        assertThat(status().noticeDismissed).isEqualTo("2026-11-13|recordatorio")

        // Falla Drive: es otro aviso y sale aunque hoy se ocultara el recordatorio.
        runBlocking { container.settingsRepository.updateBackupStatus { it.copy(lastError = BackupMessages.DRIVE_FAILED) } }
        rule.waitFor(hasText(BackupMessages.DRIVE_FAILED))
    }
}
