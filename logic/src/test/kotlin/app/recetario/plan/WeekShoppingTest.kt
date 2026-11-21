package app.recetario.plan

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.MeasureUnit
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals

class WeekShoppingTest {
    private val today = LocalDate.of(2026, 11, 18)

    private fun ing(name: String, q: Double?, unit: MeasureUnit = MeasureUnit.GRAM) =
        Ingredient("i-$name", null, name, q, unit, null, "")

    private fun recipe(id: String, servings: Int, vararg ings: Ingredient) = Recipe(
        id, id, "c", null, "", null, servings, Difficulty.EASY, FlavorProfile(0, 0, 0, 0, 0, app.recetario.recipes.model.Intensity.MEDIUM, false),
        null, emptySet(), null, null as Course?, ings.toList(), emptyList(), emptyList(), "", false, null, false,
        Instant.EPOCH, Instant.EPOCH,
    )

    private val recipes = mapOf(
        "a" to recipe("a", 2, ing("Arroz", 200.0), ing("Tomate", 2.0, MeasureUnit.PIECE), ing("Sal", null, MeasureUnit.TO_TASTE)),
        "b" to recipe("b", 4, ing("Arroz", 400.0)),
    )

    private fun meal(id: String, date: LocalDate, recipe: String, servings: Int) =
        PlannedMeal(id, date, MealSlot.LUNCH, recipe, servings)

    @Test
    fun weekIngredients_scaledAndSummed() {
        val items = weekIngredients(
            listOf(meal("1", today, "a", 4), meal("2", today.plusDays(1), "b", 2)),
            today, recipes, emptyList(), emptyList(),
        )
        val rice = items.single { it.name == "Arroz" }
        assertEquals(600.0, rice.quantity!!, 0.001)
        assertEquals(MeasureUnit.GRAM, rice.unit)
        assertEquals(4.0, items.single { it.name == "Tomate" }.quantity!!, 0.001)
    }

    @Test
    fun weekIngredients_excludesPantryAndBasics() {
        val items = weekIngredients(
            listOf(meal("1", today, "a", 2)), today, recipes,
            pantry = listOf("arroz"), basics = listOf("sal"),
        )
        assertEquals(listOf("Tomate"), items.map { it.name })
    }

    @Test
    fun weekIngredients_onlyFromToday() {
        val items = weekIngredients(
            listOf(meal("1", today.minusDays(1), "a", 2), meal("2", today, "b", 4), meal("3", today, "missing", 2)),
            today, recipes, emptyList(), emptyList(),
        )
        assertEquals(listOf("Arroz"), items.map { it.name })
        assertEquals(400.0, items.single().quantity!!, 0.001)
    }
}
