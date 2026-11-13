package app.recetario.recipes.quantity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityParserTest {
    private fun p(s: String) = parseQuantity(s)

    @Test fun `coma decimal`() = assertEquals(1.5, p("1,5")!!, 1e-9)
    @Test fun `punto decimal`() = assertEquals(1.5, p("1.5")!!, 1e-9)
    @Test fun `fraccion`() = assertEquals(0.5, p("1/2")!!, 1e-9)
    @Test fun `mixto`() = assertEquals(1.5, p("1 1/2")!!, 1e-9)
    @Test fun `medio unicode`() = assertEquals(0.5, p("½")!!, 1e-9)
    @Test fun `uno y medio unicode`() = assertEquals(1.5, p("1½")!!, 1e-9)
    @Test fun `uno y medio unicode con espacio`() = assertEquals(1.5, p("1 ½")!!, 1e-9)
    @Test fun `tercios`() {
        assertEquals(1.0 / 3, p("⅓")!!, 1e-9)
        assertEquals(2.0 / 3, p("⅔")!!, 1e-9)
        assertEquals(0.25, p("¼")!!, 1e-9)
        assertEquals(0.75, p("¾")!!, 1e-9)
    }
    @Test fun `entero`() = assertEquals(200.0, p("200")!!, 1e-9)
    @Test fun `espacios sobrantes`() = assertEquals(1.5, p("  1,5  ")!!, 1e-9)
    @Test fun `coma sin parte entera`() = assertEquals(0.5, p(",5")!!, 1e-9)
    @Test fun `vacio`() = assertNull(p(""))
    @Test fun `solo espacios`() = assertNull(p("   "))
    @Test fun `texto`() = assertNull(p("abc"))
    @Test fun `negativo`() {
        assertNull(p("-1"))
        assertNull(p("-1,5"))
    }
    @Test fun `denominador cero`() = assertNull(p("1/0"))
    @Test fun `basura al final`() = assertNull(p("1,5 g"))
}
