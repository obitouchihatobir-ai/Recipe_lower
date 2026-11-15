package app.recetario.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Un reloj que se puede mover a mano. */
private class MovableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

private class TestOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
}

@RunWith(AndroidJUnit4::class)
class ThemeStateTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rememberNow_readsTheClockAgainWhenTheAppComesBack() {
        val clock = MovableClock(Instant.parse("2026-11-13T19:59:30Z"))
        val owner = TestOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var now: LocalTime? = null
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { now = rememberNow(clock) }
        }
        rule.runOnIdle { assertThat(now).isEqualTo(LocalTime.of(19, 59, 30)) }

        // La pantalla se apaga toda la noche: con el móvil dormido, la espera de un minuto no avanza.
        rule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            clock.now = Instant.parse("2026-11-14T07:15:00Z")
            owner.registry.currentState = Lifecycle.State.RESUMED
        }

        rule.runOnIdle { assertThat(now).isEqualTo(LocalTime.of(7, 15)) }
    }
}
