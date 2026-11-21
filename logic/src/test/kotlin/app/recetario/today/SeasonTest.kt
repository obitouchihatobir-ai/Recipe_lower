package app.recetario.today

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class SeasonTest {
    @Test
    fun season_tropicNorth_dryAndRainy() {
        assertThat(seasonFor(LocalDate.of(2026, 1, 10), 20.0)).isEqualTo(Season.DRY)
        assertThat(seasonFor(LocalDate.of(2026, 7, 15), 20.0)).isEqualTo(Season.RAINY)
        assertThat(seasonFor(LocalDate.of(2026, 5, 31), 20.0)).isEqualTo(Season.DRY)
        assertThat(seasonFor(LocalDate.of(2026, 6, 1), 20.0)).isEqualTo(Season.RAINY)
        assertThat(seasonFor(LocalDate.of(2026, 10, 31), 20.0)).isEqualTo(Season.RAINY)
        assertThat(seasonFor(LocalDate.of(2026, 11, 1), 20.0)).isEqualTo(Season.DRY)
    }

    @Test
    fun season_tropicSouth_inverted() {
        assertThat(seasonFor(LocalDate.of(2026, 1, 10), -10.0)).isEqualTo(Season.RAINY)
        assertThat(seasonFor(LocalDate.of(2026, 7, 15), -10.0)).isEqualTo(Season.DRY)
    }

    @Test
    fun season_noLocation_isTropicNorth() {
        assertThat(seasonFor(LocalDate.of(2026, 1, 10), null)).isEqualTo(Season.DRY)
        assertThat(seasonFor(LocalDate.of(2026, 7, 15), null)).isEqualTo(Season.RAINY)
    }

    @Test
    fun season_temperateNorth() {
        assertThat(seasonFor(LocalDate.of(2026, 1, 15), 40.0)).isEqualTo(Season.WINTER)
        assertThat(seasonFor(LocalDate.of(2026, 12, 1), 40.0)).isEqualTo(Season.WINTER)
        assertThat(seasonFor(LocalDate.of(2026, 4, 1), 40.0)).isEqualTo(Season.SPRING)
        assertThat(seasonFor(LocalDate.of(2026, 7, 1), 40.0)).isEqualTo(Season.SUMMER)
        assertThat(seasonFor(LocalDate.of(2026, 10, 1), 40.0)).isEqualTo(Season.AUTUMN)
    }

    @Test
    fun season_temperateSouth() {
        assertThat(seasonFor(LocalDate.of(2026, 1, 15), -40.0)).isEqualTo(Season.SUMMER)
        assertThat(seasonFor(LocalDate.of(2026, 7, 15), -40.0)).isEqualTo(Season.WINTER)
        assertThat(seasonFor(LocalDate.of(2026, 4, 1), -40.0)).isEqualTo(Season.AUTUMN)
        assertThat(seasonFor(LocalDate.of(2026, 10, 1), -40.0)).isEqualTo(Season.SPRING)
    }

    @Test
    fun season_tropicBoundary_23_5_isTemperate() {
        assertThat(seasonFor(LocalDate.of(2026, 1, 15), 23.5)).isEqualTo(Season.WINTER)
        assertThat(seasonFor(LocalDate.of(2026, 1, 15), 23.4)).isEqualTo(Season.DRY)
    }

    @Test
    fun season_labels() {
        assertThat(Season.DRY.label).isEqualTo("secas")
        assertThat(Season.RAINY.label).isEqualTo("lluvias")
        assertThat(Season.SPRING.label).isEqualTo("primavera")
        assertThat(Season.SUMMER.label).isEqualTo("verano")
        assertThat(Season.AUTUMN.label).isEqualTo("otoño")
        assertThat(Season.WINTER.label).isEqualTo("invierno")
    }
}
