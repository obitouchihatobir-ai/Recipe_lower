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
import app.recetario.shopping.ShopSection

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

    @Test
    fun weekIngredients_keepSourceRecipe_andSection() {
        val items = weekIngredients(listOf(meal("1", today, "a", 2)), today, recipes, emptyList(), emptyList())
        val rice = items.single { it.name == "Arroz" }
        assertEquals("a", rice.sourceRecipeId)
        assertEquals(ShopSection.PANTRY, rice.section)
        assertEquals(ShopSection.FRUITS_VEG, items.single { it.name == "Tomate" }.section)
    }

    @Test
    fun weekIngredients_mixedMassUnits_areSummedInGrams() {
        val withKilo = recipes + ("k" to recipe("k", 2, ing("Arroz", 1.0, MeasureUnit.KILOGRAM)))
        val items = weekIngredients(
            listOf(meal("1", today, "a", 2), meal("2", today.plusDays(1), "k", 2)),
            today, withKilo, emptyList(), emptyList(),
        )
        val rice = items.single { it.name == "Arroz" }
        assertEquals(MeasureUnit.GRAM, rice.unit)
        assertEquals(1200.0, rice.quantity!!, 0.001)
    }

    @Test
    fun weekIngredients_sameRecipeTwice_withDifferentServings() {
        val items = weekIngredients(
            listOf(meal("1", today, "a", 2), meal("2", today.plusDays(2), "a", 6)),
            today, recipes, emptyList(), emptyList(),
        )
        // 200 g para 2 + 600 g para 6; 2 + 6 tomates.
        assertEquals(800.0, items.single { it.name == "Arroz" }.quantity!!, 0.001)
        assertEquals(8.0, items.single { it.name == "Tomate" }.quantity!!, 0.001)
        assertEquals(1, items.count { it.name == "Sal" })
    }

    @Test
    fun weekIngredients_cupSize_comesFromTheSetting() {
        val milk = mapOf(
            "c" to recipe("c", 1, ing("Leche", 1.0, MeasureUnit.CUP)),
            "m" to recipe("m", 1, ing("Leche", 100.0, MeasureUnit.MILLILITER)),
        )
        val items = weekIngredients(
            listOf(meal("1", today, "c", 1), meal("2", today, "m", 1)),
            today, milk, emptyList(), emptyList(), cupMl = 250,
        )
        val line = items.single()
        assertEquals(MeasureUnit.MILLILITER, line.unit)
        assertEquals(350.0, line.quantity!!, 0.001)
    }
}
