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
