package app.recetario.converter

import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.formatQuantity
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class UnitConverterTest {
    private val c = UnitConverter()

    private fun ok(r: Conversion): Conversion.Ok { assertTrue("esperaba Ok, fue $r", r is Conversion.Ok); return r as Conversion.Ok }

    @Test fun `taza de harina a gramos`() {
        val r = ok(c.convert(1.0, MeasureUnit.CUP, MeasureUnit.GRAM, "harina"))
        assertEquals(124.8, r.value, 1e-6)
        assertEquals(MeasureUnit.GRAM, r.unit)
        assertEquals("125 g", formatQuantity(r.value, r.unit, null))
    }
    @Test fun `cucharada son 15 ml`() = assertEquals(15.0, ok(c.convert(1.0, MeasureUnit.TABLESPOON, MeasureUnit.MILLILITER)).value, 1e-9)
    @Test fun `8 oz son 226,8 g`() = assertEquals(226.8, ok(c.convert(8.0, MeasureUnit.OUNCE, MeasureUnit.GRAM)).value, 0.05)
    @Test fun `1 lb son 453,6 g`() = assertEquals(453.6, ok(c.convert(1.0, MeasureUnit.POUND, MeasureUnit.GRAM)).value, 0.05)
    @Test fun `1 fl oz son 29,57 ml`() = assertEquals(29.57, ok(c.convert(1.0, MeasureUnit.FLUID_OUNCE, MeasureUnit.MILLILITER)).value, 0.01)
    @Test fun `taza de 250`() = assertEquals(250.0, ok(UnitConverter(250).convert(1.0, MeasureUnit.CUP, MeasureUnit.MILLILITER)).value, 1e-9)
    @Test fun `sin densidad`() {
        assertEquals(Conversion.NeedsDensity, c.convert(1.0, MeasureUnit.CUP, MeasureUnit.GRAM, "xyz"))
        assertEquals(Conversion.NeedsDensity, c.convert(1.0, MeasureUnit.CUP, MeasureUnit.GRAM))
    }
    @Test fun `diente a ml es incompatible`() {
        assertEquals(Conversion.Incompatible, c.convert(1.0, MeasureUnit.CLOVE, MeasureUnit.MILLILITER))
        assertEquals(Conversion.Incompatible, c.convert(1.0, MeasureUnit.PINCH, MeasureUnit.GRAM))
        assertEquals(Conversion.Incompatible, c.convert(1.0, MeasureUnit.PIECE, MeasureUnit.CLOVE))
    }
    @Test fun `misma unidad devuelve lo mismo`() {
        assertEquals(3.0, ok(c.convert(3.0, MeasureUnit.CLOVE, MeasureUnit.CLOVE)).value, 0.0)
        assertEquals(Conversion.Ok(1.0, MeasureUnit.CUSTOM), c.convert(1.0, MeasureUnit.CUSTOM, MeasureUnit.CUSTOM))
    }
    @Test fun `masa a volumen y viceversa`() {
        assertEquals(120.0, ok(c.convert(120.0, MeasureUnit.GRAM, MeasureUnit.MILLILITER, "agua")).value, 0.5)
        assertEquals(1000.0, ok(c.convert(1.0, MeasureUnit.KILOGRAM, MeasureUnit.GRAM)).value, 1e-9)
    }
    @Test fun `azucar glas gana a azucar`() {
        assertEquals(0.5, Densities.gramsPerMl("Azúcar glas"))
        assertEquals(0.83, Densities.gramsPerMl("azúcar moreno"))
        assertEquals(0.52, Densities.gramsPerMl("Harina de trigo"))
        assertTrue(Densities.gramsPerMl("xyz") == null)
    }
}
