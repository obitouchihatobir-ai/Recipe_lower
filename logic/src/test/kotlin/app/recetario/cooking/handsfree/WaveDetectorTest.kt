package app.recetario.cooking.handsfree

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WaveDetectorTest {
    @Test
    fun gesture_wave_counts() {
        val d = WaveDetector()
        assertThat(d.onProximity(near = false, atMs = 0)).isFalse()
        assertThat(d.onProximity(near = true, atMs = 1_000)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 1_400)).isTrue()
        // Lecturas repetidas de «lejos» no cuentan otra vez.
        assertThat(d.onProximity(near = false, atMs = 1_500)).isFalse()
        // Otro pase, ya fuera de la pausa.
        assertThat(d.onProximity(near = true, atMs = 5_000)).isFalse()
        assertThat(d.onProximity(near = true, atMs = 5_100)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 5_300)).isTrue()
    }

    @Test
    fun gesture_firstReadingNear_doesNotCount() {
        // Al encender el sensor ya tapado (boca abajo), destaparlo no es un gesto.
        val d = WaveDetector()
        assertThat(d.onProximity(near = true, atMs = 0)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 300)).isFalse()
        assertThat(d.onProximity(near = true, atMs = 3_000)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 3_300)).isTrue()
    }

    @Test
    fun gesture_heldNear_doesNotCount() {
        val d = WaveDetector()
        d.onProximity(near = false, atMs = 0)
        assertThat(d.onProximity(near = true, atMs = 1_000)).isFalse()
        assertThat(d.onProximity(near = true, atMs = 2_500)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 3_500)).isFalse()
        // Bolsillo: tapado y destapado a ratos largos, nunca cuenta.
        for (i in 0 until 10) {
            val t = 10_000L + i * 5_000
            assertThat(d.onProximity(near = true, atMs = t)).isFalse()
            assertThat(d.onProximity(near = false, atMs = t + 2_500)).isFalse()
        }
        // Al sacarlo del bolsillo, un pase rápido justo después tampoco cuenta (pausa tras soltar).
        assertThat(d.onProximity(near = true, atMs = 57_600)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 57_800)).isFalse()
        // Pasada la pausa, sí.
        assertThat(d.onProximity(near = true, atMs = 60_000)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 60_300)).isTrue()
    }

    @Test
    fun gesture_cooldown() {
        val d = WaveDetector()
        d.onProximity(near = false, atMs = 0)
        d.onProximity(near = true, atMs = 100)
        assertThat(d.onProximity(near = false, atMs = 300)).isTrue()
        // Dentro de los 1,5 s de pausa: no cuenta.
        d.onProximity(near = true, atMs = 500)
        assertThat(d.onProximity(near = false, atMs = 700)).isFalse()
        d.onProximity(near = true, atMs = 1_200)
        assertThat(d.onProximity(near = false, atMs = 1_700)).isFalse()
        // Pasada la pausa desde el último que contó.
        d.onProximity(near = true, atMs = 1_900)
        assertThat(d.onProximity(near = false, atMs = 2_000)).isTrue()
    }

    @Test
    fun gesture_slowWave_doesNotCount() {
        val d = WaveDetector()
        d.onProximity(near = false, atMs = 0)
        d.onProximity(near = true, atMs = 1_000)
        assertThat(d.onProximity(near = false, atMs = 2_600)).isFalse()
        // Justo en el límite, sí.
        d.onProximity(near = true, atMs = 10_000)
        assertThat(d.onProximity(near = false, atMs = 11_500)).isTrue()
    }

    @Test
    fun gesture_customTimes_andClockGoingBack() {
        val d = WaveDetector(cooldownMs = 100, maxNearMs = 200, heldMs = 400)
        d.onProximity(near = false, atMs = 0)
        d.onProximity(near = true, atMs = 10)
        assertThat(d.onProximity(near = false, atMs = 150)).isTrue()
        d.onProximity(near = true, atMs = 300)
        assertThat(d.onProximity(near = false, atMs = 450)).isTrue()
        // Una lectura con una hora anterior se descarta.
        d.onProximity(near = true, atMs = 600)
        assertThat(d.onProximity(near = false, atMs = 500)).isFalse()
        assertThat(d.onProximity(near = false, atMs = 700)).isTrue()
    }
}
