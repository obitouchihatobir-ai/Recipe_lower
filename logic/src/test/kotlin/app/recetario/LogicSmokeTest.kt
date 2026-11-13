package app.recetario

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Comprueba que el build `logic/` compila y ejecuta pruebas con JUnit 4 y Truth. */
class LogicSmokeTest {
    @Test
    fun toolchainRunsTests() {
        assertThat(listOf("Recetario")).containsExactly("Recetario")
    }
}
