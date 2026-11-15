package app.recetario.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class ContrastTest {
    private val minimum = 4.5

    @Test
    fun blackOnWhite_is21() {
        assertThat(contrastRatio(Color.Black, Color.White)).isWithin(0.01).of(21.0)
    }

    @Test
    fun textColorsReachMinimumOnBackgroundAndSurface() {
        for (choice in ThemeChoice.entries) {
            val p = paletteFor(choice)
            for ((name, fg) in listOf("ink" to p.ink, "muted" to p.muted, "accent" to p.accent)) {
                for ((bgName, bg) in listOf("bg" to p.bg, "surface" to p.surface, "soft" to p.soft)) {
                    assertWithMessage("$choice: $name sobre $bgName")
                        .that(contrastRatio(fg, bg)).isAtLeast(minimum)
                }
            }
        }
    }

    @Test
    fun onAccentReachesMinimumOnAccent() {
        for (choice in ThemeChoice.entries) {
            val p = paletteFor(choice)
            assertThat(contrastRatio(p.onAccent, p.accent)).isAtLeast(minimum)
        }
    }

    @Test
    fun todayInkReachesMinimumOnEveryTodayStop() {
        for (choice in ThemeChoice.entries) {
            val p = paletteFor(choice)
            for ((i, stop) in p.todayStops.withIndex()) {
                assertWithMessage("$choice: todayInk sobre el color $i de «Hoy»")
                    .that(contrastRatio(p.todayInk, stop)).isAtLeast(minimum)
            }
        }
    }
}
