package app.recetario.settings

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.backup.SwitchableDataStore
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.installTestContainer
import app.recetario.testing.testSettingsDataStore
import app.recetario.testing.waitFor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Con el disco lleno (o los ajustes sin poder guardarse) la app no se cierra: lo dice en un aviso. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsWriteFailuresTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val settings = SwitchableDataStore(testSettingsDataStore()).apply { failWrites = true }
        container = installTestContainer(settingsDataStore = settings)
    }

    private fun launchAppearance() = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) { AppearanceRoute(container, onBack = {}) }
    }

    @Test
    fun cupChip_whenItCannotBeSaved_saysSo() {
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                SettingsRoute(container, onBack = {}, onAppearance = {}, onCategories = {}, onTags = {}, onBackup = {}, onAbout = {})
            }
        }
        rule.waitFor(hasTestTag("cup-250"))

        rule.onNodeWithTag("cup-250").performClick()

        rule.waitFor(hasText("No se pudo guardar la medida de la taza."))
    }

    @Test
    fun themeSwatch_whenItCannotBeSaved_saysSo() {
        launchAppearance()
        rule.waitFor(hasTestTag("theme-FRESCO"))

        rule.onNodeWithTag("theme-FRESCO").performClick()

        rule.waitFor(hasText("No se pudo guardar el tema."))
    }

    @Test
    fun autoSwitch_whenItCannotBeSaved_saysSo() {
        launchAppearance()
        rule.waitFor(hasTestTag("auto-switch"))

        rule.onNodeWithTag("auto-switch").performClick()

        rule.waitFor(hasText("No se pudo guardar el tema."))
    }

    @Test
    fun nightHours_whenTheyCannotBeSaved_saySo() {
        launchAppearance()
        rule.waitFor(hasTestTag("night-start-plus"))

        rule.onNodeWithTag("night-start-plus").performClick()

        rule.waitFor(hasText("No se pudo guardar el horario de noche."))
    }
}
