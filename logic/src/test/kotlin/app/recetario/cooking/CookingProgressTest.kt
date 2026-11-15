package app.recetario.cooking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CookingProgressTest {
    @Test
    fun next_isTheFollowingStep() {
        assertThat(nextPendingStep(current = 0, done = setOf(0), count = 4)).isEqualTo(1)
    }

    @Test
    fun next_skipsStepsAlreadyDone() {
        // Volvió al paso 1 (hecho) con 1, 2 y 3 hechos: sigue donde lo dejó, en el 4.
        assertThat(nextPendingStep(current = 0, done = setOf(0, 1, 2), count = 4)).isEqualTo(3)
    }

    @Test
    fun next_wrapsToAnEarlierPendingStep() {
        assertThat(nextPendingStep(current = 3, done = setOf(0, 2, 3), count = 4)).isEqualTo(1)
    }

    @Test
    fun allDone_returnsCount() {
        assertThat(nextPendingStep(current = 3, done = setOf(0, 1, 2, 3), count = 4)).isEqualTo(4)
    }
}
