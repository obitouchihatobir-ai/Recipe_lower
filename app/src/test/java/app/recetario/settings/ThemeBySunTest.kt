package app.recetario.settings

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.forecastJson
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import app.recetario.weather.Place
import app.recetario.weather.WeatherCache
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 19:00 en Madrid: de día con las horas fijas (la noche empieza a las 20:00). */
private val sevenPm: Clock = Clock.fixed(Instant.parse("2026-11-13T18:00:00Z"), ZoneId.of("Europe/Madrid"))

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ThemeBySunTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container?.database?.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private var container: AppContainer? = null
    private val madrid = Place("Madrid", "Comunidad de Madrid", "España", 40.4168, -3.7038)

    /** Ciudad elegida y una previsión con la puesta a las 18:10, pedida hace [age]. */
    private fun setUp(bySun: Boolean, age: Duration = Duration.ofHours(2)): AppContainer {
        val c = installTestContainer(sevenPm, openMeteo = FakeOpenMeteo(offline = true)).also { container = it }
        runBlocking {
            c.settingsRepository.setCity(madrid)
            c.settingsRepository.setThemeBySun(bySun)
            c.settingsRepository.setWeatherCache(
                WeatherCache(forecastJson(LocalDate.of(2026, 11, 10)), sevenPm.instant().minus(age), madrid.latitude, madrid.longitude),
            )
        }
        return c
    }

    private fun resolved(c: AppContainer): ThemeChoice? {
        var theme: ThemeChoice? = null
        rule.setContent { theme = rememberResolvedTheme(c) }
        rule.waitUntil(5_000) { theme != null }
        rule.waitForIdle()
        return theme
    }

    @Test
    fun themeBySun_nightBetweenSunsetAndSunrise() {
        assertThat(resolved(setUp(bySun = true))).isEqualTo(ThemeChoice.NOCTURNO)
    }

    @Test
    fun themeBySun_off_usesFixedHours() {
        assertThat(resolved(setUp(bySun = false))).isEqualTo(ThemeChoice.CALIDO)
    }

    @Test
    fun themeBySun_forecastOlderThan3Days_usesFixedHours() {
        assertThat(resolved(setUp(bySun = true, age = Duration.ofDays(4)))).isEqualTo(ThemeChoice.CALIDO)
    }

    @Test
    fun appearance_sunSwitch_onlyWithAutoAndLocation() {
        val c = setUp(bySun = false)
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppearanceRoute(c, onBack = {}) } }
        rule.waitFor(hasTestTag("sun-switch"))
        rule.onNodeWithTag("sun-switch").performClick()
        rule.waitUntil(5_000) { runBlocking { c.settingsRepository.themeBySun.first() } }

        // Sin ubicación no sale.
        runBlocking { c.settingsRepository.clearLocation() }
        rule.waitForGone(hasTestTag("sun-switch"))
        // Ni sin Automático.
        runBlocking {
            c.settingsRepository.setCity(madrid)
            c.settingsRepository.setAuto(false)
        }
        rule.waitForIdle()
        rule.waitForGone(hasTestTag("sun-switch"))
    }
}
