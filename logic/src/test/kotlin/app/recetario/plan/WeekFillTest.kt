package app.recetario.plan

import app.recetario.today.recipe
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class WeekFillTest {
    private val week = weekOf(LocalDate.of(2026, 11, 18))
    private val today = LocalDate.of(2026, 11, 18)

    private fun meal(id: String, date: LocalDate, slot: MealSlot, recipe: String) =
        PlannedMeal(id, date, slot, recipe, 2)

    private var n = 0
    private fun fill(
        existing: List<PlannedMeal> = emptyList(),
        ids: List<String> = listOf("a", "b", "c"),
        planFor: (LocalDate, MealSlot, Set<String>) -> app.recetario.today.TodayRecipe? = { _, _, used ->
            ids.firstOrNull { it !in used }?.let { recipe(it) } ?: ids.first().let { recipe(it) }
        },
    ): List<PlannedMeal> {
        n = 0
        return fillWeek(week, today, existing, planFor, { 4 }, { "new-${n++}" })
    }

    @Test
    fun fill_onlyEmptyFromToday() {
        val taken = meal("m1", today, MealSlot.LUNCH, "x")
        val past = meal("m0", LocalDate.of(2026, 11, 17), MealSlot.DINNER, "y")
        val added = fill(listOf(taken, past))
        assertTrue(added.none { it.date < today })
        assertTrue(added.none { it.date == today && it.slot == MealSlot.LUNCH })
        // Hoy a quedan 5 días... días 18..22 = 5 días x 3 huecos - 1 ocupado.
        assertEquals(5 * 3 - 1, added.size)
        assertTrue(added.all { it.servings == 4 })
        assertEquals(added.size, added.map { it.date to it.slot }.toSet().size)
    }

    @Test
    fun fill_noRepeatsWhileOptions() {
        val seen = mutableListOf<Set<String>>()
        val many = (1..20).map { "r$it" }
        val added = fill(ids = many, planFor = { _, _, used ->
            seen += used
            many.first { it !in used }.let { recipe(it) }
        })
        assertEquals(added.size, added.map { it.recipeId }.toSet().size)
        assertEquals(emptySet<String>(), seen.first())
        assertEquals(added.size - 1, seen.last().size)
    }

    @Test
    fun fill_usedIncludesExistingMeals() {
        var first: Set<String>? = null
        fill(listOf(meal("m1", today, MealSlot.LUNCH, "x"))) { _, _, used ->
            if (first == null) first = used
            recipe("a")
        }
        assertEquals(setOf("x"), first)
    }

    @Test
    fun fill_returnsOnlyNew() {
        val existing = listOf(meal("m1", today, MealSlot.LUNCH, "x"))
        val added = fill(existing)
        assertTrue(added.none { it.id == "m1" })
        assertTrue(added.all { it.id.startsWith("new-") })
        // Sin propuesta (planFor devuelve null) el hueco se queda vacío y no gasta id.
        val none = fill(planFor = { _, _, _ -> null })
        assertTrue(none.isEmpty())
    }
}
