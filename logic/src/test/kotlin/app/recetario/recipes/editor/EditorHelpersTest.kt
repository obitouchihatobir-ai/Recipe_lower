package app.recetario.recipes.editor

import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.search.Taste
import app.recetario.recipes.search.classifyTaste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorHelpersTest {
    @Test fun `minutos`() = assertEquals(1200, detectDurationSeconds("Cuece 20 minutos"))
    @Test fun `hora y minutos`() = assertEquals(5400, detectDurationSeconds("1 h 30 min"))
    @Test fun `hora y minutos con y`() = assertEquals(5400, detectDurationSeconds("Hornea 1 hora y 30 minutos"))
    @Test fun `una hora`() = assertEquals(3600, detectDurationSeconds("Hornea 1 hora"))
    @Test fun `dos horas`() = assertEquals(7200, detectDurationSeconds("Deja 2 horas"))
    @Test fun `segundos`() = assertEquals(45, detectDurationSeconds("Reposa 45 segundos"))
    @Test fun `sin tiempo`() = assertNull(detectDurationSeconds("Sirve"))
    @Test fun `solo la primera expresion`() = assertEquals(1200, detectDurationSeconds("Cuece 20 minutos y reposa 5 min"))
    @Test fun `mayusculas`() = assertEquals(600, detectDurationSeconds("COCINA 10 MIN"))
    @Test fun `no confunde palabras que empiezan por s`() = assertNull(detectDurationSeconds("Usa 2 sartenes"))
    @Test fun `hora y media decimal`() = assertEquals(5400, detectDurationSeconds("Hornea 1,5 horas"))

    private fun ing(id: String, name: String) =
        Ingredient(id, null, name, null, MeasureUnit.PIECE, null, "")

    @Test fun `sugiere ingredientes por nombre o primera palabra`() {
        val list = listOf(ing("a", "Calabaza"), ing("b", "Caldo de verduras"), ing("c", "Crema"))
        assertEquals(setOf("a", "b"), suggestStepIngredients("Añade la calabaza y el caldo", list))
    }

    @Test fun `nombre completo y plural`() {
        val list = listOf(ing("a", "Sal"), ing("b", "Huevo"))
        assertEquals(setOf("b"), suggestStepIngredients("Bate los huevos", list))
    }

    @Test fun `primera palabra corta no cuenta`() {
        val list = listOf(ing("a", "Sal gorda"))
        assertEquals(emptySet<String>(), suggestStepIngredients("Pon sal", list))
    }

    @Test fun `defaults por tipo`() {
        assertEquals(setOf(Moment.LUNCH, Moment.DINNER) to ServeTemp.HOT, defaultsFor(BaseType.FOOD))
        assertEquals(setOf(Moment.LUNCH, Moment.SNACK, Moment.DINNER) to ServeTemp.COLD, defaultsFor(BaseType.DRINK))
        assertEquals(setOf(Moment.LUNCH, Moment.SNACK, Moment.DINNER) to ServeTemp.ROOM, defaultsFor(BaseType.DESSERT))
        assertEquals(setOf(Moment.LUNCH, Moment.DINNER) to ServeTemp.ROOM, defaultsFor(BaseType.OTHER))
    }

    @Test fun `marcar dulce`() {
        val f = FlavorProfile(sweet = 0, salty = 3).markSweet()
        assertEquals(2, f.sweet)
        assertEquals(1, f.salty)
        assertEquals(Taste.SWEET, classifyTaste(f.sweet, f.salty))
    }

    @Test fun `marcar salado`() {
        val f = FlavorProfile(sweet = 3, salty = 0).markSalty()
        assertEquals(1, f.sweet)
        assertEquals(2, f.salty)
        assertEquals(Taste.SALTY, classifyTaste(f.sweet, f.salty))
    }

    @Test fun `marcar dulce no baja un dulce mayor`() = assertEquals(3, FlavorProfile(sweet = 3).markSweet().sweet)

    @Test fun `duracion estricta acepta entero y terminos`() {
        assertEquals(35, parseDurationMinutes("35"))
        assertEquals(60, parseDurationMinutes("1 h"))
        assertEquals(60, parseDurationMinutes("1 hora"))
        assertEquals(120, parseDurationMinutes("2 horas"))
        assertEquals(45, parseDurationMinutes(" 45 min "))
        assertEquals(90, parseDurationMinutes("1 h 30 min"))
        assertEquals(90, parseDurationMinutes("90 minutos"))
        assertEquals(90, parseDurationMinutes("1 hora y 30 minutos"))
    }

    @Test fun `duracion estricta rechaza lo entendido a medias`() {
        for (t in listOf("1 hora y media", "1 h 30", "20-30 min", "40 min + 2 h de reposo", "-5", "", "unos 40", "2 sartenes")) {
            assertNull(t, parseDurationMinutes(t))
        }
    }
}
