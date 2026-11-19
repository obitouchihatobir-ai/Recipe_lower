package app.recetario.cooking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimerTextsTest {
    @Test
    fun timerLabel_shortText_isKeptWhole() {
        assertThat(timerLabel(1, "Pela la calabaza.")).isEqualTo("Paso 1: Pela la calabaza.")
    }

    @Test
    fun timerLabel_longText_isCutAtAWordAroundThirtyCharacters() {
        assertThat(timerLabel(2, "Sofríe la cebolla y el ajo picados hasta que estén transparentes."))
            .isEqualTo("Paso 2: Sofríe la cebolla y el ajo…")
    }

    @Test
    fun timerLabel_collapsesSpacesAndNewLines_andDropsTrailingPunctuationBeforeTheEllipsis() {
        assertThat(timerLabel(3, "  Añade   la calabaza,\ny el caldo. Cuece a fuego medio hasta que esté tierna."))
            .isEqualTo("Paso 3: Añade la calabaza, y el caldo…")
    }

    @Test
    fun timerLabel_oneLongWord_isCutAtThirtyCharacters() {
        assertThat(timerLabel(4, "a".repeat(50))).isEqualTo("Paso 4: " + "a".repeat(30) + "…")
    }

    @Test
    fun timerLabel_blankText_isJustTheStep() {
        assertThat(timerLabel(5, "   ")).isEqualTo("Paso 5")
    }

    @Test
    fun formatTimerClock_roundsUpToTheSecond() {
        assertThat(formatTimerClock(300_000)).isEqualTo("5:00")
        assertThat(formatTimerClock(299_001)).isEqualTo("5:00")
        assertThat(formatTimerClock(298_000)).isEqualTo("4:58")
        assertThat(formatTimerClock(9_000)).isEqualTo("0:09")
        assertThat(formatTimerClock(1)).isEqualTo("0:01")
        assertThat(formatTimerClock(0)).isEqualTo("0:00")
        assertThat(formatTimerClock(-5)).isEqualTo("0:00")
        assertThat(formatTimerClock(1_200_000)).isEqualTo("20:00")
        assertThat(formatTimerClock(3_600_000)).isEqualTo("1:00:00")
        assertThat(formatTimerClock(3_905_000)).isEqualTo("1:05:05")
    }

    @Test
    fun shortTimerLabel_isTheStepPart() {
        assertThat(shortTimerLabel("Paso 2: Sofríe la cebolla…")).isEqualTo("Paso 2")
        assertThat(shortTimerLabel("Paso 5")).isEqualTo("Paso 5")
    }

    @Test
    fun ongoingTimerLines_listActiveTimersSoonestFirst_withPausedMarked() {
        val now = 10_000L
        val timers = listOf(
            CookingTimer("a", "Paso 3: Cuece", 1200, endsAtElapsedMs = now + 1_170_000, remainingMsWhenPaused = null, finished = false),
            CookingTimer("b", "Paso 2: Sofríe", 300, endsAtElapsedMs = now + 298_000, remainingMsWhenPaused = null, finished = false),
            CookingTimer("c", "Paso 4: Reposa", 600, endsAtElapsedMs = null, remainingMsWhenPaused = 400_000, finished = false),
            CookingTimer("d", "Paso 1: Pela", 60, endsAtElapsedMs = null, remainingMsWhenPaused = null, finished = true),
        )
        assertThat(ongoingTimerLines(timers, now))
            .containsExactly("Paso 2 · 4:58", "Paso 3 · 19:30", "Paso 4 · ⏸ 6:40").inOrder()
    }

    @Test
    fun finishedTimerTitle() {
        assertThat(finishedTimerTitle("Paso 2: Sofríe la cebolla…")).isEqualTo("⏱ ¡Tiempo! — Paso 2: Sofríe la cebolla…")
    }

    @Test
    fun remainingFraction_followsTheClock_andCountsAddedMinutes() {
        val running = CookingTimer("p1", "Paso 1", 300, endsAtElapsedMs = 1_000_000, remainingMsWhenPaused = null, finished = false)
        assertThat(remainingFraction(running, now = 700_000)).isEqualTo(1f)
        assertThat(remainingFraction(running, now = 850_000)).isEqualTo(0.5f)
        assertThat(remainingFraction(running, now = 2_000_000)).isEqualTo(0f)
        val paused = running.copy(endsAtElapsedMs = null, remainingMsWhenPaused = 75_000)
        assertThat(remainingFraction(paused, now = 0)).isEqualTo(0.25f)
        // Con «+1 min» el total crece: 6 minutos en total y quedan 3.
        val longer = running.copy(totalSeconds = 360, endsAtElapsedMs = 1_180_000)
        assertThat(remainingFraction(longer, now = 1_000_000)).isEqualTo(0.5f)
    }

    @Test
    fun remainingFraction_finishedOrBroken_isEmptyAndNeverOutOfRange() {
        val finished = CookingTimer("p1", "Paso 1", 300, endsAtElapsedMs = null, remainingMsWhenPaused = null, finished = true)
        assertThat(remainingFraction(finished, now = 0)).isEqualTo(0f)
        val zero = finished.copy(totalSeconds = 0, finished = false, remainingMsWhenPaused = 5_000)
        assertThat(remainingFraction(zero, now = 0)).isEqualTo(0f)
        val tooMuch = finished.copy(finished = false, remainingMsWhenPaused = 999_000)
        assertThat(remainingFraction(tooMuch, now = 0)).isEqualTo(1f)
    }
}
