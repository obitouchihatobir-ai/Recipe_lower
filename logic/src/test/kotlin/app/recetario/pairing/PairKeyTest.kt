package app.recetario.pairing

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PairKeyTest {
    @Test
    fun pairKey_orderIndependent() {
        val a = "RECIPE" to "r1"
        val b = "CATALOG" to "cat-jamaica"
        assertThat(pairKey(a, b)).isEqualTo("CATALOG:cat-jamaica|RECIPE:r1")
        assertThat(pairKey(b, a)).isEqualTo(pairKey(a, b))
        assertThat(pairKey("RECIPE" to "b", "RECIPE" to "a")).isEqualTo("RECIPE:a|RECIPE:b")
        assertThat(pairKey(item("r1"), item("cat-jamaica", kind = "CATALOG"))).isEqualTo(pairKey(a, b))
    }

    @Test
    fun pairKey_catalogCatalog_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            pairKey("CATALOG" to "cat-a", "CATALOG" to "cat-b")
        }
        assertThrows(IllegalArgumentException::class.java) { pairKey("OTRO" to "x", "RECIPE" to "y") }
    }
}
