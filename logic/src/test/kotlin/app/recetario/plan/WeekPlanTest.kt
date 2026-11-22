package app.recetario.plan

import app.recetario.backup.MEAL_SLOTS
import app.recetario.recipes.model.Moment
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals

class WeekPlanTest {
    @Test
    fun weekOf_mondayToSunday() {
        val week = weekOf(LocalDate.of(2026, 11, 18))
        assertEquals(LocalDate.of(2026, 11, 16), week.first())
        assertEquals(LocalDate.of(2026, 11, 22), week.last())
        assertEquals(7, week.size)
        // Domingo: sigue siendo la semana que acaba ese día.
        val sunday = weekOf(LocalDate.of(2026, 11, 22))
        assertEquals(LocalDate.of(2026, 11, 16), sunday.first())
        assertEquals(LocalDate.of(2026, 11, 22), sunday.last())
        assertEquals(LocalDate.of(2026, 11, 16), weekOf(LocalDate.of(2026, 11, 16)).first())
    }

    @Test
    fun weekTitle_cases() {
        val today = LocalDate.of(2026, 11, 18)
        assertEquals("Esta semana", weekTitle(LocalDate.of(2026, 11, 16), today))
        assertEquals("Semana que viene", weekTitle(LocalDate.of(2026, 11, 23), today))
        assertEquals("Semana pasada", weekTitle(LocalDate.of(2026, 11, 9), today))
        assertEquals("Semana del 30 de noviembre", weekTitle(LocalDate.of(2026, 11, 30), today))
        assertEquals("Semana del 2 de noviembre", weekTitle(LocalDate.of(2026, 11, 2), today))
    }

    @Test
    fun slotKeys_areMealSlots() {
        assertEquals(MEAL_SLOTS, MealSlot.entries.map { it.key })
        assertEquals(Moment.BREAKFAST, MealSlot.BREAKFAST.moment)
        assertEquals(Moment.LUNCH, MealSlot.LUNCH.moment)
        assertEquals(Moment.DINNER, MealSlot.DINNER.moment)
    }
}
