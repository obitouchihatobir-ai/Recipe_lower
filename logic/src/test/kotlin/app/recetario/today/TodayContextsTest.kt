package app.recetario.today

import app.recetario.recipes.model.Moment
import app.recetario.weather.CurrentWeather
import app.recetario.weather.DailyForecast
import app.recetario.weather.WeatherKind
import java.time.Instant
import java.time.LocalDate
import app.recetario.diary.CookStats
import app.recetario.pairing.PairRole
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.RecipeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayContextsTest {
    private val wednesday = LocalDate.of(2026, 11, 18)
    private val saturday = LocalDate.of(2026, 11, 21)
    private val madrid = 40.4

    @Test
    fun todayContext_withCurrentWeather() {
        val c = todayContext(wednesday, Moment.DINNER, madrid, ThermalThresholds(), CurrentWeather(9.0, 1.2, 61, false))
        assertEquals(wednesday.atStartOfDay(), c.now)
        assertEquals(Moment.DINNER, c.moment)
        assertEquals(seasonFor(wednesday, madrid), c.season)
        assertEquals(Thermal.COLD, c.thermal)
        assertEquals(WeatherKind.RAIN, c.weatherKind)
        assertEquals(9.0, c.apparentC!!, 0.0)
        assertTrue(c.rainingNow)
        assertFalse(c.weekend)
        assertEquals(false, c.isDay)
    }

    @Test
    fun todayContext_withoutWeather_onlySeason() {
        val c = todayContext(saturday, Moment.LUNCH, null, ThermalThresholds(), null)
        assertNull(c.thermal)
        assertNull(c.weatherKind)
        assertNull(c.apparentC)
        assertFalse(c.rainingNow)
        assertTrue(c.weekend)
        assertEquals(seasonFor(saturday, null), c.season)
    }

    @Test
    fun dayContext_usesThatDaysForecast() {
        val daily = DailyForecast(saturday, 0, maxC = 30.0, minC = 20.0, rainProbability = 0, sunrise = null, sunset = null)
        val lunch = dayContext(saturday, Moment.LUNCH, madrid, ThermalThresholds(), daily)
        // La comida, con la máxima; el desayuno y la cena, con la media del día.
        assertEquals(30.0, lunch.apparentC!!, 0.0)
        assertEquals(Thermal.HOT, lunch.thermal)
        assertEquals(WeatherKind.CLEAR, lunch.weatherKind)
        assertTrue(lunch.weekend)
        assertFalse(lunch.rainingNow)
        val dinner = dayContext(saturday, Moment.DINNER, madrid, ThermalThresholds(), daily)
        assertEquals(25.0, dinner.apparentC!!, 0.0)
        assertEquals(saturday.atStartOfDay(), dinner.now)
    }

    @Test
    fun dayContext_forecastOfAnotherDay_isIgnored_seasonOnly() {
        val other = DailyForecast(wednesday, 61, 10.0, 5.0, 90, null, null)
        val c = dayContext(saturday, Moment.LUNCH, madrid, ThermalThresholds(), other)
        assertNull(c.thermal)
        assertNull(c.weatherKind)
        assertEquals(seasonFor(saturday, madrid), c.season)
    }

    @Test
    fun todayInputs_rolesFromCategories() {
        fun summary(id: String, category: String) = RecipeSummary(
            id, id, category, null, null, FlavorProfile(), null, emptySet(), null, Difficulty.EASY, false,
            emptyList(), emptyList(), Instant.EPOCH,
        )
        val drinks = Category("bebidas", "Bebidas", "🥤", "#000000", BaseType.DRINK, 0, true)
        val stats = mapOf("a" to CookStats(1, wednesday, null))
        val inputs = todayInputs(
            summaries = listOf(summary("a", "bebidas"), summary("b", "sin-categoria")),
            categories = listOf(drinks),
            stats = stats,
            pantry = listOf("arroz"),
            basics = listOf("sal"),
            ratings = mapOf("k" to true),
        )
        assertEquals(listOf(PairRole.DRINK, PairRole.FOOD), inputs.recipes.map { it.role })
        assertEquals(stats, inputs.stats)
        assertEquals(listOf("arroz"), inputs.pantry)
        assertEquals(listOf("sal"), inputs.basics)
        assertEquals(mapOf("k" to true), inputs.ratings)
    }
}
