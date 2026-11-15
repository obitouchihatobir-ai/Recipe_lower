package app.recetario.converter

import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.MeasureUnit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalencesTest {
    private fun units(q: Double, u: MeasureUnit, name: String, cup: Int = 240) =
        equivalencesFor(q, u, name, cup).map { it.unit }

    @Test fun `masa sin densidad da g kg oz lb sin la propia`() {
        assertEquals(listOf(KILOGRAM, OUNCE, POUND), units(800.0, GRAM, "Calabaza"))
        assertEquals(listOf(GRAM, OUNCE, POUND), units(1.0, KILOGRAM, "Calabaza"))
    }

    @Test fun `masa con densidad anade ml taza y cucharada`() {
        assertEquals(listOf(KILOGRAM, OUNCE, POUND, MILLILITER, CUP, TABLESPOON), units(500.0, GRAM, "Harina"))
    }

    @Test fun `volumen sin densidad`() {
        assertEquals(
            listOf(MILLILITER, LITER, TABLESPOON, TEASPOON, FLUID_OUNCE),
            units(1.5, CUP, "Caldo de verduras"),
        )
    }

    @Test fun `volumen con densidad anade gramos`() {
        val eq = equivalencesFor(1.5, CUP, "Harina", 240)
        assertEquals(listOf(MILLILITER, LITER, TABLESPOON, TEASPOON, FLUID_OUNCE, GRAM), eq.map { it.unit })
        assertEquals(360.0, eq.first { it.unit == MILLILITER }.value, 1e-9)
        assertEquals(24.0, eq.first { it.unit == TABLESPOON }.value, 1e-9)
        assertEquals(187.2, eq.first { it.unit == GRAM }.value, 1e-6)
        assertTrue(eq.first { it.unit == GRAM }.approximate)
        assertFalse(eq.first { it.unit == MILLILITER }.approximate)
    }

    @Test fun `la taza configurada se respeta`() {
        assertEquals(375.0, equivalencesFor(1.5, CUP, "x", 250).first { it.unit == MILLILITER }.value, 1e-9)
    }

    @Test fun `unidades de conteo no tienen equivalencias`() {
        assertTrue(equivalencesFor(2.0, CLOVE, "Ajo", 240).isEmpty())
        assertTrue(equivalencesFor(1.0, TO_TASTE, "Sal", 240).isEmpty())
        assertTrue(equivalencesFor(1.0, PIECE, "Cebolla", 240).isEmpty())
    }

    @Test fun `palabras clave de densidad`() {
        assertTrue("harina" in Densities.keywords)
        assertTrue(Densities.keywords.all { Densities.gramsPerMl(it) != null })
    }
}
