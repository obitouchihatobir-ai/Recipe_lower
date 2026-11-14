package app.recetario.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.LocalThemeRevealOrigin
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeRevealHost
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** 13:10 en Madrid: de día. */
private val dayClock: Clock = Clock.fixed(Instant.parse("2026-11-13T12:10:00Z"), ZoneId.of("Europe/Madrid"))

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class AppearanceScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private var theme: ThemeChoice? = null
    private var revealOrigin: Offset? = null

    @Before
    fun setUp() {
        container = installTestContainer(dayClock)
    }

    @After
    fun tearDown() = container.database.close()

    /** El mismo montaje que MainActivity: el tema sale de los ajustes y la pantalla se pinta dentro del host. */
    private fun launch() = rule.setContent {
        val resolved = rememberResolvedTheme(container)
        theme = resolved
        var origin by remember { mutableStateOf<Offset?>(null) }
        if (resolved != null) {
            CompositionLocalProvider(LocalThemeRevealOrigin provides { o: Offset? -> origin = o; revealOrigin = o }) {
                ThemeRevealHost(resolved, origin, onOriginConsumed = { origin = null }) { shown ->
                    RecetarioTheme(shown) { AppearanceRoute(container, onBack = {}) }
                }
            }
        }
    }

    private fun settings() = runBlocking { container.settingsRepository.themeSettings.first() }

    @Test
    fun appearance_tapFresco_changesThemeInHost() {
        launch()
        rule.waitFor(hasTestTag("theme-FRESCO"))
        rule.runOnIdle { assertEquals(ThemeChoice.CALIDO, theme) }
        rule.onNodeWithTag("theme-CALIDO").assertIsSelected()

        rule.onNodeWithTag("theme-FRESCO").performClick()
        rule.waitUntil(5_000) { theme == ThemeChoice.FRESCO }
        // La pantalla avisó al host de desde dónde se tocó.
        rule.runOnIdle { assertNotNull(revealOrigin) }
        rule.onNodeWithTag("theme-FRESCO").assertIsSelected()
        // Automático sigue activado: Fresco pasa a ser el tema claro de día.
        assertEquals(true, settings().auto)
        assertEquals(ThemeChoice.FRESCO, settings().dayTheme)
    }

    @Test
    fun appearance_tapNocturno_turnsAutoOffAndKeepsDayTheme() {
        launch()
        rule.waitFor(hasTestTag("theme-NOCTURNO"))
        rule.onNodeWithTag("theme-NOCTURNO").performClick()
        rule.waitUntil(5_000) { theme == ThemeChoice.NOCTURNO }
        rule.onNodeWithTag("auto-switch").assertIsOff()
        assertEquals(false, settings().auto)
        assertEquals(ThemeChoice.NOCTURNO, settings().choice)
        assertEquals(ThemeChoice.CALIDO, settings().dayTheme)
    }

    @Test
    fun appearance_autoSwitchOff_keepsWhatYouSee() {
        launch()
        rule.waitFor(hasTestTag("auto-switch"))
        rule.onNodeWithTag("auto-switch").performClick()
        rule.waitUntil(5_000) { !settings().auto }
        assertEquals(ThemeChoice.CALIDO, settings().choice)
        rule.runOnIdle { assertEquals(ThemeChoice.CALIDO, theme) }
    }

    @Test
    fun appearance_showsAutoNote_andNightHours() {
        launch()
        rule.waitFor(hasText("De día: Cálido · De noche: Nocturno · Ahora: Cálido"))
        rule.onNodeWithTag("night-start").assertIsDisplayed()
        rule.waitFor(hasText("20:00"))
        rule.waitFor(hasText("07:00"))
        rule.onNodeWithTag("night-start-plus").performClick()
        rule.waitUntil(5_000) { settings().nightStart == LocalTime.of(20, 30) }
    }

    @Test
    fun appearance_nightWindow_cannotBecomeEmpty() {
        runBlocking { container.settingsRepository.setNightWindow(LocalTime.of(20, 0), LocalTime.of(20, 30)) }
        launch()
        rule.waitFor(hasText("20:30"))
        // Bajar el final o subir el inicio los igualaría: Automático nunca llegaría a la noche.
        rule.onNodeWithTag("night-end-minus").assertIsNotEnabled()
        rule.onNodeWithTag("night-start-plus").assertIsNotEnabled()
        rule.onNodeWithTag("night-start-minus").performClick()
        rule.waitUntil(5_000) { settings().nightStart == LocalTime.of(19, 30) }
    }
}
