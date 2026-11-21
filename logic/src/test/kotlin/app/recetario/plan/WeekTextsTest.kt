package app.recetario.plan

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTextsTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun dayLabel_weekdayAndNumber() {
        assertEquals("Lunes 16", weekDayLabel(monday))
        assertEquals("Miércoles 18", weekDayLabel(monday.plusDays(2)))
        assertEquals("Domingo 22", weekDayLabel(monday.plusDays(6)))
    }

    @Test
    fun slotLabels() {
        assertEquals(listOf("Desayuno", "Comida", "Cena"), MealSlot.entries.map { it.label })
    }

    @Test
    fun servings() {
        assertEquals("1 porción", servingsLabel(1))
        assertEquals("4 porciones", servingsLabel(4))
    }

    @Test
    fun slotDescription_filledAndEmpty() {
        assertEquals(
            "Lunes 16, comida: Arroz con tomate, 4 porciones",
            slotDescription(monday, MealSlot.LUNCH, "Arroz con tomate", 4),
        )
        assertEquals("Lunes 16, cena: vacío", slotDescription(monday, MealSlot.DINNER, null, 0))
    }

    @Test
    fun messages() {
        assertEquals("Se añadió 1 comida", filledMessage(1))
        assertEquals("Se añadieron 5 comidas", filledMessage(5))
        assertEquals("No se añadió ninguna comida", filledMessage(0))
        assertEquals("✓ 1 ingrediente añadido", weekListMessage(1))
        assertEquals("✓ 7 ingredientes añadidos", weekListMessage(7))
        assertEquals("No falta nada para esta semana", weekListMessage(0))
    }
}
