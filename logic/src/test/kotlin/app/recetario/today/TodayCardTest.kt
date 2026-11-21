package app.recetario.today

import app.recetario.pairing.PairRole
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.model.ServeTemp
import app.recetario.weather.WeatherKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TodayCardTest {
    private fun summary(course: Course? = null, alcoholic: Boolean? = null) = RecipeSummary(
        id = "r1", title = "Pozole rojo", categoryId = "c", coverPhoto = "f.jpg", totalMinutes = 90,
        flavor = FlavorProfile(salty = 2, spicy = 1), serveTemp = ServeTemp.HOT, moments = setOf(Moment.DINNER),
        alcoholic = alcoholic, difficulty = Difficulty.MEDIUM, favorite = true, tags = emptyList(),
        ingredientNames = listOf("Maíz", "Cerdo"), updatedAt = Instant.EPOCH, course = course, servings = 6,
    )

    @Test
    fun summary_toTodayRecipe_keepsEverythingTheEngineNeeds() {
        val r = summary(course = Course.MAIN, alcoholic = false).toTodayRecipe(BaseType.FOOD)
        assertThat(r).isEqualTo(
            TodayRecipe(
                id = "r1", title = "Pozole rojo", role = PairRole.FOOD, course = Course.MAIN,
                flavor = FlavorProfile(salty = 2, spicy = 1), serveTemp = ServeTemp.HOT, moments = setOf(Moment.DINNER),
                totalMinutes = 90, favorite = true, alcoholic = false, ingredientNames = listOf("Maíz", "Cerdo"),
                coverPhoto = "f.jpg",
            ),
        )
    }

    @Test
    fun baseType_toPairRole() {
        assertThat(pairRoleOf(BaseType.FOOD)).isEqualTo(PairRole.FOOD)
        assertThat(pairRoleOf(BaseType.DRINK)).isEqualTo(PairRole.DRINK)
        assertThat(pairRoleOf(BaseType.DESSERT)).isEqualTo(PairRole.DESSERT)
        assertThat(pairRoleOf(BaseType.OTHER)).isEqualTo(PairRole.SIDE)
        // Una receta cuya categoría no se encuentra cuenta como comida.
        assertThat(summary().toTodayRecipe(null).role).isEqualTo(PairRole.FOOD)
    }

    @Test
    fun eyebrow_momentAndSeason() {
        assertThat(todayEyebrow(Moment.DINNER, Season.AUTUMN)).isEqualTo("Cena de hoy · Otoño")
        assertThat(todayEyebrow(Moment.NIGHT, Season.RAINY)).isEqualTo("Para esta noche · Temporada de lluvias")
        assertThat(todayEyebrow(Moment.BREAKFAST, Season.DRY)).isEqualTo("Desayuno de hoy · Temporada de secas")
        assertThat(todayEyebrow(Moment.LUNCH, Season.SUMMER)).isEqualTo("Comida de hoy · Verano")
        assertThat(todayEyebrow(Moment.SNACK, Season.WINTER)).isEqualTo("Merienda de hoy · Invierno")
    }

    @Test
    fun slots_perMoment_withLabelsAndEmptyTexts() {
        assertThat(todaySlots(Moment.LUNCH)).containsExactly(TodaySlot.MAIN, TodaySlot.DRINK, TodaySlot.DESSERT).inOrder()
        assertThat(todaySlots(Moment.BREAKFAST)).containsExactly(TodaySlot.MAIN, TodaySlot.DRINK).inOrder()
        assertThat(todaySlots(Moment.NIGHT)).containsExactly(TodaySlot.DRINK, TodaySlot.SNACK).inOrder()
        assertThat(TodaySlot.MAIN.label(Moment.DINNER)).isEqualTo("Principal")
        assertThat(TodaySlot.MAIN.label(Moment.BREAKFAST)).isEqualTo("Plato")
        assertThat(TodaySlot.DRINK.missing).isEqualTo("Aún no tienes bebidas")
        assertThat(TodaySlot.DESSERT.missing).isEqualTo("Aún no tienes postres")
        assertThat(TodaySlot.SNACK.missing).isEqualTo("Aún no tienes nada para picar")
        assertThat(TodaySlot.MAIN.missing).isEqualTo("Aún no tienes platos")
    }

    @Test
    fun weatherEmoji_andDescription() {
        assertThat(weatherEmoji(WeatherKind.CLEAR, isDay = true)).isEqualTo("☀️")
        assertThat(weatherEmoji(WeatherKind.CLEAR, isDay = false)).isEqualTo("🌙")
        assertThat(weatherEmoji(WeatherKind.RAIN, isDay = true)).isEqualTo("🌧️")
        assertThat(weatherChipText(8.6, "Madrid")).isEqualTo("9 °C · Madrid")
        assertThat(weatherChipText(24.0, null)).isEqualTo("24 °C")
        assertThat(weatherChipDescription("Lluvia", 8.6, "Madrid")).isEqualTo("El tiempo ahora: lluvia, 9 °C en Madrid")
        assertThat(weatherChipDescription("Soleado", 24.0, null)).isEqualTo("El tiempo ahora: soleado, 24 °C")
    }

    @Test
    fun seed_sameDayAndMoment_sameSeed_changesWithRoundAndDay() {
        val day = LocalDate.of(2026, 11, 13)
        assertThat(todaySeed(day, Moment.DINNER, 0)).isEqualTo(todaySeed(day, Moment.DINNER, 0))
        assertThat(todaySeed(day, Moment.DINNER, 1)).isNotEqualTo(todaySeed(day, Moment.DINNER, 0))
        assertThat(todaySeed(day, Moment.LUNCH, 0)).isNotEqualTo(todaySeed(day, Moment.DINNER, 0))
        assertThat(todaySeed(day.plusDays(1), Moment.DINNER, 0)).isNotEqualTo(todaySeed(day, Moment.DINNER, 0))
    }
}
