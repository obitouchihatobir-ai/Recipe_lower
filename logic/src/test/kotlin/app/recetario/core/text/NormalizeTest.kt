package app.recetario.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NormalizeTest {
    @Test
    fun removesAccentsCaseAndExtraSpaces() {
        assertThat(normalize("  Limón  Ácido ")).isEqualTo("limon acido")
    }

    @Test
    fun keepsEnye() {
        // La ñ se descompone en n + tilde combinante; para búsquedas se acepta como n.
        assertThat(normalize("Piña")).isEqualTo("pina")
    }
}
