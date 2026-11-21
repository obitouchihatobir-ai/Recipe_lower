package app.recetario.plan

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.recipes.model.ServeTemp
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.MainDispatcherRule
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.weather.DailyForecast
import app.recetario.weather.WeatherSnapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** «Rellenar» con la previsión de cada día y la semana guardada en el `SavedStateHandle`. */
@RunWith(AndroidJUnit4::class)
class WeekViewModelTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    private lateinit var container: AppContainer

    /** Domingo 15 de noviembre de 2026: «Rellenar» solo toca ese día. */
    private val sunday = LocalDate.of(2026, 11, 15)
    private val sundayClock = Clock.fixed(Instant.parse("2026-11-15T09:00:00Z"), ZoneId.of("Europe/Madrid"))

    private fun vm(forecast: WeatherSnapshot?, handle: SavedStateHandle = SavedStateHandle()) = main.track(
        WeekViewModel(
            plan = container.mealPlanRepository,
            summaries = container.recipeRepository.observeSummaries(),
            categories = container.recipeRepository.observeCategories(),
            stats = container.diaryRepository.observeStats(),
            pantry = container.pantryRepository.observe(),
            basics = container.settingsRepository.pantryBasics,
            ratings = container.pairingRepository.observeRatings(),
            thresholds = container.settingsRepository.thermalThresholds,
            forecast = flowOf(forecast),
            place = flowOf(null),
            recipe = { id -> container.recipeRepository.observeRecipe(id).first() },
            shopping = container.shoppingRepository,
            cupMl = container.settingsRepository.cupMl,
            clock = container.clock,
            handle = handle,
        ),
    )

    private fun seed() = runBlocking {
        container.recipeRepository.save(sampleRecipe("caldo", "Caldo", serveTemp = ServeTemp.HOT, minutes = null))
        container.recipeRepository.save(sampleRecipe("guiso", "Guiso", serveTemp = ServeTemp.HOT, minutes = null))
        container.recipeRepository.save(sampleRecipe("gazpacho", "Gazpacho", serveTemp = ServeTemp.COLD, minutes = null))
        container.recipeRepository.save(sampleRecipe("ensalada", "Ensalada", serveTemp = ServeTemp.COLD, minutes = null))
    }

    private fun day(maxC: Double, minC: Double) =
        WeatherSnapshot(null, listOf(DailyForecast(sunday, 0, maxC, minC, 0, null, null)), sundayClock.instant())

    private fun firstTwo(added: List<PlannedMeal>): Set<String> =
        added.filter { it.slot != MealSlot.DINNER }.map { it.recipeId }.toSet()

    @Test
    fun fill_usesThatDaysForecast() {
        container = installTestContainer(clock = sundayClock)
        seed()
        val hot = runBlocking { vm(day(36.0, 26.0)).fill() }
        assertThat(hot).hasSize(3)
        assertThat(hot.map { it.date }.toSet()).containsExactly(sunday)
        // Con calor, el desayuno y la comida son lo fresco.
        assertThat(firstTwo(hot)).containsExactly("gazpacho", "ensalada")

        runBlocking { container.mealPlanRepository.removeAll(hot.map { it.id }) }
        val cold = runBlocking { vm(day(4.0, -2.0)).fill() }
        assertThat(firstTwo(cold)).containsExactly("caldo", "guiso")
    }

    @Test
    fun weekOffset_survivesInTheSavedState() {
        container = installTestContainer()
        seed()
        val handle = SavedStateHandle()
        val first = vm(null, handle)
        runBlocking { withTimeout(10_000) { first.state.first { it.loaded } } }
        first.nextWeek()
        first.nextWeek()
        val again = vm(null, handle)
        val state = runBlocking { withTimeout(10_000) { again.state.map { it }.first { it.loaded } } }
        assertThat(state.offset).isEqualTo(2)
        assertThat(state.title).isEqualTo("Semana del 23 de noviembre")
        assertThat(state.days.first().date).isEqualTo(LocalDate.of(2026, 11, 23))
    }
}
