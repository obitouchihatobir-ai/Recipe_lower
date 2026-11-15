package app.recetario.recipes.quantity

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class QuantityFormatterTest {
    private fun scaled(q: Double, unit: MeasureUnit, n: Int) =
        formatQuantity(scaleQuantity(q, 4, n), unit, null)

    @Test fun `800 g por 6 entre 4 es 1,2 kg`() = assertEquals("1,2 kg", scaled(800.0, MeasureUnit.GRAM, 6))
    @Test fun `750 ml por 6 entre 4 es 1,13 l`() = assertEquals("1,13 l", scaled(750.0, MeasureUnit.MILLILITER, 6))
    @Test fun `2 dientes entre 4 es medio diente`() = assertEquals("½ diente", scaled(2.0, MeasureUnit.CLOVE, 1))
    @Test fun `1 unidad por 6 entre 4 es 1½`() = assertEquals("1½", scaled(1.0, MeasureUnit.PIECE, 6))
    @Test fun `100 ml entre 4`() = assertEquals("25 ml", scaled(100.0, MeasureUnit.MILLILITER, 1))
    @Test fun `20 g entre 4`() = assertEquals("5 g", scaled(20.0, MeasureUnit.GRAM, 1))
    @Test fun `2,5 g`() = assertEquals("2,5 g", formatQuantity(2.5, MeasureUnit.GRAM, null))
    @Test fun `una taza`() = assertEquals("1 taza", formatQuantity(1.0, MeasureUnit.CUP, null))
    @Test fun `dos tazas`() = assertEquals("2 tazas", formatQuantity(2.0, MeasureUnit.CUP, null))
    @Test fun `un tercio de taza`() = assertEquals("⅓ taza", formatQuantity(0.33, MeasureUnit.CUP, null))
    @Test fun `pizca y al gusto`() {
        assertEquals("una pizca", formatQuantity(null, MeasureUnit.PINCH, null))
        assertEquals("una pizca", formatQuantity(3.0, MeasureUnit.PINCH, null))
        assertEquals("al gusto", formatQuantity(null, MeasureUnit.TO_TASTE, null))
    }
    @Test fun `0,05 unidades es un cuarto`() = assertEquals("¼", formatQuantity(0.05, MeasureUnit.PIECE, null))
    @Test fun `null es vacio`() = assertEquals("", formatQuantity(null, MeasureUnit.GRAM, null))
    @Test fun `escalar null es null`() = assertNull(scaleQuantity(null, 4, 6))
    @Test fun `1000 g es 1 kg`() = assertEquals("1 kg", formatQuantity(1000.0, MeasureUnit.GRAM, null))
    @Test fun `bajo 10 con un decimal`() = assertEquals("9,5 g", formatQuantity(9.46, MeasureUnit.GRAM, null))
    @Test fun `onzas y libras con un decimal`() {
        assertEquals("8 oz", formatQuantity(8.0, MeasureUnit.OUNCE, null))
        assertEquals("1,5 lb", formatQuantity(1.5, MeasureUnit.POUND, null))
        assertEquals("2,3 fl oz", formatQuantity(2.25, MeasureUnit.FLUID_OUNCE, null))
    }
    @Test fun `personalizada con y sin etiqueta`() {
        assertEquals("2 latas", formatQuantity(2.0, MeasureUnit.CUSTOM, "latas"))
        assertEquals("2", formatQuantity(2.0, MeasureUnit.CUSTOM, null))
        assertEquals("2", formatQuantity(2.0, MeasureUnit.CUSTOM, " "))
    }
    @Test fun `fracciones`() {
        assertEquals("¼", formatFraction(0.01))
        assertEquals("2", formatFraction(1.95))
        assertEquals("2⅔", formatFraction(2.66))
        assertEquals("¾", formatFraction(0.8))
        assertEquals("1½", formatFraction(1.5))
    }
}
