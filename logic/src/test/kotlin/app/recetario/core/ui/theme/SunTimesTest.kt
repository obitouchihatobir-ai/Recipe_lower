package app.recetario.core.ui.theme

import app.recetario.weather.DailyForecast
import app.recetario.weather.WeatherSnapshot
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class SunTimesTest {
    private val day = LocalDate.of(2026, 11, 13)
    private val fetched = Instant.parse("2026-11-12T10:00:00Z")

    private fun snapshot(sunrise: LocalTime? = LocalTime.of(6, 30), sunset: LocalTime? = LocalTime.of(18, 10)) = WeatherSnapshot(
        current = null,
        daily = (0L..6L).map { DailyForecast(day.minusDays(1).plusDays(it), 1, 25.0, 12.0, null, sunrise, sunset) },
        fetchedAt = fetched,
    )

    @Test
    fun sunTimes_takesTodayFromTheForecast() {
        assertThat(sunTimesFor(snapshot(), day, fetched.plusSeconds(3600)))
            .isEqualTo(LocalTime.of(6, 30) to LocalTime.of(18, 10))
    }

    @Test
    fun sunTimes_olderThan3Days_isNull() {
        val s = snapshot()
        assertThat(sunTimesFor(s, day.plusDays(2), fetched.plusSeconds(3 * 86_400 - 60))).isNotNull()
        assertThat(sunTimesFor(s, day.plusDays(3), fetched.plusSeconds(3 * 86_400 + 60))).isNull()
    }

    @Test
    fun sunTimes_withoutTodayOrWithoutSun_isNull() {
        assertThat(sunTimesFor(null, day, fetched)).isNull()
        assertThat(sunTimesFor(snapshot(), day.plusDays(10), fetched)).isNull()
        assertThat(sunTimesFor(snapshot(sunrise = null), day, fetched)).isNull()
        assertThat(sunTimesFor(snapshot(sunset = null), day, fetched)).isNull()
    }

    @Test
    fun themeBySun_nightBetweenSunsetAndSunrise() {
        // 19:00: con las horas fijas (20:00–07:00) es de día; con el sol (puesta 18:10) ya es de noche.
        val settings = ThemeSettings()
        val sun = sunTimesFor(snapshot(), day, fetched)
        assertThat(resolveTheme(settings, LocalTime.of(19, 0), systemDark = false)).isEqualTo(ThemeChoice.CALIDO)
        assertThat(resolveTheme(settings, LocalTime.of(19, 0), systemDark = false, sun = sun)).isEqualTo(ThemeChoice.NOCTURNO)
        assertThat(resolveTheme(settings, LocalTime.of(6, 45), systemDark = false, sun = sun)).isEqualTo(ThemeChoice.CALIDO)
    }
}
