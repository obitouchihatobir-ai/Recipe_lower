package app.recetario.cooking

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerManagerTest {
    /** Reloj falso en milisegundos (como `SystemClock.elapsedRealtime()`). */
    private var clock = 1_000L
    private val manager = TimerManager { clock }

    private fun timer(id: String) = manager.timers.value.single { it.id == id }

    @Test
    fun start_remainingCountsDown() {
        val id = manager.start("Paso 2: Sofríe la cebolla…", 300)
        assertThat(manager.remainingMs(id, clock)).isEqualTo(300_000)
        clock += 10_000
        assertThat(manager.remainingMs(id, clock)).isEqualTo(290_000)
        val t = timer(id)
        assertThat(t.label).isEqualTo("Paso 2: Sofríe la cebolla…")
        assertThat(t.totalSeconds).isEqualTo(300)
        assertThat(t.endsAtElapsedMs).isEqualTo(301_000)
        assertThat(t.remainingMsWhenPaused).isNull()
        assertThat(t.finished).isFalse()
        assertThat(t.isRunning).isTrue()
    }

    @Test
    fun pause_freezes_resume_continues() {
        val id = manager.start("Paso 1", 300)
        clock += 10_000
        manager.pause(id)
        assertThat(timer(id).isPaused).isTrue()
        assertThat(timer(id).remainingMsWhenPaused).isEqualTo(290_000)
        assertThat(timer(id).endsAtElapsedMs).isNull()

        clock += 60_000 // en pausa no corre
        assertThat(manager.remainingMs(id, clock)).isEqualTo(290_000)

        manager.resume(id)
        assertThat(timer(id).isRunning).isTrue()
        clock += 5_000
        assertThat(manager.remainingMs(id, clock)).isEqualTo(285_000)
    }

    @Test
    fun addMinute_extendsRunning() {
        val id = manager.start("Paso 1", 300)
        clock += 100_000
        manager.addMinute(id)
        assertThat(manager.remainingMs(id, clock)).isEqualTo(260_000)
        assertThat(timer(id).totalSeconds).isEqualTo(360)
    }

    @Test
    fun addMinute_extendsPaused() {
        val id = manager.start("Paso 1", 300)
        manager.pause(id)
        manager.addMinute(id)
        assertThat(timer(id).isPaused).isTrue()
        assertThat(manager.remainingMs(id, clock)).isEqualTo(360_000)
    }

    @Test
    fun addMinute_onFinished_runsOneMoreMinute() {
        val id = manager.start("Paso 1", 30)
        clock += 30_000
        manager.tick(clock)
        assertThat(timer(id).finished).isTrue()
        manager.addMinute(id)
        assertThat(timer(id).finished).isFalse()
        assertThat(timer(id).isRunning).isTrue()
        assertThat(manager.remainingMs(id, clock)).isEqualTo(60_000)
    }

    @Test
    fun tick_afterEnd_emitsFinishedOnce() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<CookingTimer>()
        backgroundScope.launch { manager.finishedEvents.collect { events += it } }
        val id = manager.start("Paso 2: Sofríe la cebolla…", 300)

        manager.tick(clock + 299_999)
        assertThat(events).isEmpty()
        assertThat(timer(id).finished).isFalse()

        manager.tick(clock + 300_000)
        manager.tick(clock + 301_000)
        manager.tick(clock + 400_000)

        assertThat(events).hasSize(1)
        assertThat(events.single().id).isEqualTo(id)
        assertThat(events.single().label).isEqualTo("Paso 2: Sofríe la cebolla…")
        assertThat(events.single().finished).isTrue()
        assertThat(timer(id).finished).isTrue()
        assertThat(timer(id).isRunning).isFalse()
        assertThat(manager.remainingMs(id, clock + 400_000)).isEqualTo(0)
    }

    @Test
    fun multipleTimers_independent() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<String>()
        backgroundScope.launch { manager.finishedEvents.collect { events += it.id } }
        val a = manager.start("Paso 2", 60)
        val b = manager.start("Paso 3", 120)
        assertThat(a).isNotEqualTo(b)

        clock += 30_000
        manager.pause(a)
        assertThat(manager.remainingMs(a, clock)).isEqualTo(30_000)
        assertThat(manager.remainingMs(b, clock)).isEqualTo(90_000)

        clock += 90_000
        manager.tick(clock)
        assertThat(events).containsExactly(b)
        assertThat(timer(a).isPaused).isTrue()
        assertThat(timer(b).finished).isTrue()

        manager.cancel(b)
        assertThat(manager.timers.value.map { it.id }).containsExactly(a)
        manager.resume(a)
        clock += 30_000
        manager.tick(clock)
        assertThat(events).containsExactly(b, a).inOrder()
    }

    @Test
    fun start_withAnExistingId_restartsThatTimerInPlace() {
        manager.start("Paso 1", 60, id = "s1")
        manager.start("Paso 2", 120, id = "s2")
        clock += 60_000
        manager.tick(clock)
        assertThat(timer("s1").finished).isTrue()

        manager.start("Paso 1", 60, id = "s1")
        assertThat(manager.timers.value.map { it.id }).containsExactly("s1", "s2").inOrder()
        assertThat(timer("s1").isRunning).isTrue()
        assertThat(manager.remainingMs("s1", clock)).isEqualTo(60_000)
    }

    @Test
    fun pause_afterTheEnd_finishesInsteadOfFreezingAtZero() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<String>()
        backgroundScope.launch { manager.finishedEvents.collect { events += it.id } }
        val id = manager.start("Paso 1", 10)
        clock += 10_500
        manager.pause(id)
        assertThat(timer(id).finished).isTrue()
        assertThat(events).containsExactly(id)
    }

    @Test
    fun unknownId_isIgnored_andHasNoTimeLeft() {
        manager.pause("nada")
        manager.resume("nada")
        manager.addMinute("nada")
        manager.cancel("nada")
        assertThat(manager.timers.value).isEmpty()
        assertThat(manager.remainingMs("nada", clock)).isEqualTo(0)
    }

    @Test
    fun now_readsTheInjectedClock() {
        clock = 42_000
        assertThat(manager.now()).isEqualTo(42_000)
    }
}
