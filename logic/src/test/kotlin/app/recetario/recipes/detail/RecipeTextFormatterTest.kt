package app.recetario.recipes.detail

import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class RecipeTextFormatterTest {
    private fun ing(id: String, name: String, q: Double?, unit: MeasureUnit) =
        Ingredient(id, null, name, q, unit, null, "")

    private fun recipe(minutes: Int? = 35) = Recipe(
        id = "r", title = "Crema de calabaza", categoryId = "cat-comidas", coverPhoto = null, description = "",
        totalMinutes = minutes, servings = 4, difficulty = Difficulty.EASY, flavor = FlavorProfile(),
        serveTemp = null, moments = emptySet(), alcoholic = null, course = null,
        ingredients = listOf(
            ing("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
            ing("i2", "Sal y pimienta", null, MeasureUnit.TO_TASTE),
        ),
        steps = listOf(
            Step("s1", "Pela la calabaza.", emptyList(), null, emptySet()),
            Step("s2", "Cuécela 20 minutos.", emptyList(), null, emptySet()),
        ),
        tags = emptyList(), notes = "", favorite = false, sourceUrl = null, aiGenerated = false,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test fun exactFormat() {
        assertThat(formatRecipeAsText(recipe(), 4, "🍲")).isEqualTo(
            """
            🍲 Crema de calabaza
            ⏱ 35 min · 4 porciones · Fácil

            Ingredientes
            • Calabaza — 800 g
            • Sal y pimienta — al gusto

            Pasos
            1. Pela la calabaza.
            2. Cuécela 20 minutos.

            Compartido desde mi Recetario
            """.trimIndent(),
        )
    }

    @Test fun scalesQuantitiesToTheChosenServings() {
        val text = formatRecipeAsText(recipe(), 6, "🍲")
        assertThat(text).contains("⏱ 35 min · 6 porciones · Fácil")
        assertThat(text).contains("• Calabaza — 1,2 kg")
    }

    @Test fun missingTimeIsOmittedWithoutSeparator() {
        val text = formatRecipeAsText(recipe(minutes = null), 1, "🍲")
        assertThat(text.lines()[1]).isEqualTo("1 porción · Fácil")
    }

    @Test fun ingredientWithoutQuantityShowsOnlyTheName() {
        val r = recipe().copy(ingredients = listOf(ing("i", "Perejil", null, MeasureUnit.PIECE)))
        assertThat(formatRecipeAsText(r, 4, "🍲")).contains("• Perejil\n")
    }
}
