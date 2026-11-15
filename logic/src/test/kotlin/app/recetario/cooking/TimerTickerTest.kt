package app.recetario.cooking

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerTickerTest {
    @Test
    fun ticker_finishesRunningTimers_andStopsReadingTheClockWhenNoneRun() = runTest {
        var clockReads = 0
        val manager = TimerManager {
            clockReads++
            testScheduler.currentTime
        }
        backgroundScope.launchTimerTicker(manager, periodMs = 250)
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertThat(clockReads).isEqualTo(0) // sin temporizadores no hace nada

        val id = manager.start("Paso 1", 2)
        testScheduler.advanceTimeBy(1_900)
        testScheduler.runCurrent()
        assertThat(manager.timers.value.single().finished).isFalse()
        testScheduler.advanceTimeBy(400)
        testScheduler.runCurrent()
        assertThat(manager.timers.value.single { it.id == id }.finished).isTrue()

        val readsWhenFinished = clockReads
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertThat(clockReads).isEqualTo(readsWhenFinished)
    }

    @Test
    fun ticker_pausedTimers_doNotKeepItTicking() = runTest {
        var clockReads = 0
        val manager = TimerManager {
            clockReads++
            testScheduler.currentTime
        }
        backgroundScope.launchTimerTicker(manager, periodMs = 250)
        val id = manager.start("Paso 1", 60)
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        manager.pause(id)
        testScheduler.runCurrent()
        val reads = clockReads
        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()
        assertThat(clockReads).isEqualTo(reads)

        manager.resume(id)
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertThat(manager.timers.value.single().finished).isTrue()
    }
}
