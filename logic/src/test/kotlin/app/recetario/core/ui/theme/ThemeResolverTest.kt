package app.recetario.core.ui.theme

import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test

class ThemeResolverTest {
    @Test
    fun manual_returnsChoice() {
        val s = ThemeSettings(auto = false, choice = ThemeChoice.FRESCO)
        assertThat(resolveTheme(s, LocalTime.of(23, 0), systemDark = false)).isEqualTo(ThemeChoice.FRESCO)
    }

    @Test
    fun auto_day_returnsDayTheme() {
        val s = ThemeSettings(auto = true, dayTheme = ThemeChoice.FRESCO)
        assertThat(resolveTheme(s, LocalTime.of(13, 0), systemDark = false)).isEqualTo(ThemeChoice.FRESCO)
    }

    @Test
    fun auto_night_returnsNocturno() {
        val s = ThemeSettings(auto = true, dayTheme = ThemeChoice.FRESCO)
        assertThat(resolveTheme(s, LocalTime.of(20, 0), systemDark = false)).isEqualTo(ThemeChoice.NOCTURNO)
        assertThat(resolveTheme(s, LocalTime.of(6, 59), systemDark = false)).isEqualTo(ThemeChoice.NOCTURNO)
    }

    @Test
    fun auto_7am_isDay() {
        val s = ThemeSettings(auto = true, dayTheme = ThemeChoice.FRESCO)
        assertThat(resolveTheme(s, LocalTime.of(7, 0), systemDark = false)).isEqualTo(ThemeChoice.FRESCO)
    }

    @Test
    fun auto_systemDark_returnsNocturno() {
        val s = ThemeSettings(auto = true)
        assertThat(resolveTheme(s, LocalTime.of(13, 0), systemDark = true)).isEqualTo(ThemeChoice.NOCTURNO)
    }

    @Test
    fun auto_nightWindowInsideSameDay_isRespected() {
        val s = ThemeSettings(auto = true, nightStart = LocalTime.of(1, 0), nightEnd = LocalTime.of(5, 0))
        assertThat(resolveTheme(s, LocalTime.of(3, 0), systemDark = false)).isEqualTo(ThemeChoice.NOCTURNO)
        assertThat(resolveTheme(s, LocalTime.of(23, 0), systemDark = false)).isEqualTo(ThemeChoice.CALIDO)
    }
}

class ThemeBySunTest {
    private val sun = LocalTime.of(6, 58) to LocalTime.of(20, 2)

    @Test
    fun resolveTheme_bySun_nightAfterSunset() {
        val s = ThemeSettings(auto = true, dayTheme = ThemeChoice.FRESCO)
        assertThat(resolveTheme(s, LocalTime.of(20, 3), systemDark = false, sun = sun)).isEqualTo(ThemeChoice.NOCTURNO)
        assertThat(resolveTheme(s, LocalTime.of(6, 57), systemDark = false, sun = sun)).isEqualTo(ThemeChoice.NOCTURNO)
        // 20:00 sería noche por horas, pero el sol aún no se ha puesto.
        assertThat(resolveTheme(s, LocalTime.of(20, 0), systemDark = false, sun = sun)).isEqualTo(ThemeChoice.FRESCO)
        assertThat(resolveTheme(s, LocalTime.of(12, 0), systemDark = false, sun = sun)).isEqualTo(ThemeChoice.FRESCO)
    }

    @Test
    fun resolveTheme_bySun_nullFallsBackToHours() {
        val s = ThemeSettings(auto = true, dayTheme = ThemeChoice.FRESCO)
        assertThat(resolveTheme(s, LocalTime.of(20, 0), systemDark = false, sun = null)).isEqualTo(ThemeChoice.NOCTURNO)
        assertThat(resolveTheme(s, LocalTime.of(12, 0), systemDark = false, sun = null)).isEqualTo(ThemeChoice.FRESCO)
    }

    @Test
    fun resolveTheme_bySun_manualAndSystemDarkStillWin() {
        val manual = ThemeSettings(auto = false, choice = ThemeChoice.CALIDO)
        assertThat(resolveTheme(manual, LocalTime.of(23, 0), systemDark = false, sun = sun)).isEqualTo(ThemeChoice.CALIDO)
        val auto = ThemeSettings(auto = true)
        assertThat(resolveTheme(auto, LocalTime.of(12, 0), systemDark = true, sun = sun)).isEqualTo(ThemeChoice.NOCTURNO)
    }

    @Test
    fun isNightBySun_boundaries() {
        val rise = LocalTime.of(7, 0)
        val set = LocalTime.of(20, 0)
        assertThat(isNightBySun(LocalTime.of(7, 0), rise, set)).isFalse()
        assertThat(isNightBySun(LocalTime.of(6, 59), rise, set)).isTrue()
        assertThat(isNightBySun(LocalTime.of(19, 59), rise, set)).isFalse()
        assertThat(isNightBySun(LocalTime.of(20, 0), rise, set)).isTrue()
        assertThat(isNightBySun(LocalTime.MIDNIGHT, rise, set)).isTrue()
    }
}
