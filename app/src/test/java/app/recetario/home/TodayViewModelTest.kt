package app.recetario.home

import org.junit.Rule
import app.recetario.testing.MainDispatcherRule
import androidx.lifecycle.SavedStateHandle
import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.model.ServeTemp
import app.recetario.today.MealTimes
import app.recetario.today.ThermalThresholds
import app.recetario.today.TodaySlot
import app.recetario.today.planToday
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Un reloj que las pruebas mueven a mano, en Madrid. */
private class TodayClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("Europe/Madrid")
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

/**
 * El ViewModel de la tarjeta Hoy, sin pantalla: cuándo se recalcula el menú, «Otra idea» con toques rápidos, el giro
 * (mismo `SavedStateHandle`), el cambio de momento y una 👎 de maridaje.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val categories = listOf(
        Category(DefaultCategories.FOOD_ID, "Comidas", "🍲", "#C8553D", BaseType.FOOD, 0, true),
        Category(DefaultCategories.DRINK_ID, "Bebidas", "🍹", "#2A7F8E", BaseType.DRINK, 1, true),
        Category(DefaultCategories.DESSERT_ID, "Postres", "🍮", "#B5733A", BaseType.DESSERT, 2, true),
    )

    private fun summary(
        id: String,
        title: String,
        category: String = DefaultCategories.FOOD_ID,
        sweet: Int = 0,
        ingredients: List<String> = emptyList(),
    ) = RecipeSummary(
        id = id, title = title, categoryId = category, coverPhoto = null, totalMinutes = 30,
        flavor = FlavorProfile(sweet = sweet), serveTemp = ServeTemp.HOT, moments = emptySet(), alcoholic = null,
        difficulty = Difficulty.EASY, favorite = false, tags = emptyList(), ingredientNames = ingredients,
        updatedAt = Instant.EPOCH, servings = 4,
    )

    private val threeMains = listOf(
        summary("sopa", "Crema de calabaza"),
        summary("tacos", "Tacos de canasta"),
        summary("arroz", "Arroz a la mexicana"),
    )

    /** Lo que se pasó como `exclude` en cada llamada al motor. */
    private val calls = mutableListOf<List<String>>()

    /** Viernes 13 de noviembre de 2026, 21:40 en Madrid: cena. */
    private val clock = TodayClock(Instant.parse("2026-11-13T20:40:00Z"))
    private val ticks = MutableSharedFlow<Unit>()
    private val ratings = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private fun vm(recipes: List<RecipeSummary> = threeMains, handle: SavedStateHandle = SavedStateHandle()) = main.track(TodayViewModel(
        summaries = flowOf(recipes),
        categories = flowOf(categories),
        stats = flowOf(emptyMap()),
        pantry = flowOf(emptyList()),
        basics = flowOf(emptyList()),
        mealTimes = flowOf(MealTimes()),
        thresholds = flowOf(ThermalThresholds()),
        weather = flowOf(null),
        place = flowOf(null),
        ratings = ratings,
        refresh = {},
        clock = clock,
        handle = handle,
        ticks = ticks,
        dispatcher = dispatcher,
        plan = { ctx, inputs, exclude, random -> calls += exclude; planToday(ctx, inputs, exclude, random) },
    ))

    private fun TestScope.collect(vm: TodayViewModel) {
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
    }

    @Test
    fun ticks_inTheSameDayAndMoment_doNotRecompute() = runTest(dispatcher) {
        val vm = vm()
        collect(vm)
        assertThat(vm.state.value.loaded).isTrue()
        assertThat(calls).hasSize(1)

        // Tres minutos más de la misma cena: el menú no se vuelve a calcular.
        repeat(3) {
            clock.now = clock.now.plusSeconds(60)
            ticks.emit(Unit)
            runCurrent()
        }
        assertThat(calls).hasSize(1)

        // El sábado a las 8:00 es otro día y otro momento (desayuno): sí.
        clock.now = Instant.parse("2026-11-14T07:00:00Z")
        ticks.emit(Unit)
        runCurrent()
        assertThat(calls).hasSize(2)
        assertThat(vm.state.value.eyebrow).startsWith("Desayuno")
    }

    @Test
    fun anotherIdea_twoQuickTaps_advanceOnlyOnce() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = vm(handle = handle)
        collect(vm)
        val first = vm.state.value.lead!!.id

        // Dos toques antes de que llegue el menú nuevo: el segundo no se salta una propuesta.
        vm.anotherIdea()
        vm.anotherIdea()
        runCurrent()
        assertThat(handle.get<Int>("today-round")).isEqualTo(1)
        assertThat(handle.get<ArrayList<String>>("today-exclude")).containsExactly(first)
        val second = vm.state.value.lead!!.id
        assertThat(second).isNotEqualTo(first)

        // Con el menú nuevo ya a la vista, otro toque sí avanza.
        vm.anotherIdea()
        runCurrent()
        assertThat(handle.get<Int>("today-round")).isEqualTo(2)
        assertThat(vm.state.value.lead!!.id).isNoneOf(first, second)
    }

    @Test
    fun rotation_keepsTheProposal_andANewMomentResetsExclude() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val before = vm(handle = handle)
        collect(before)
        before.anotherIdea()
        runCurrent()
        val shown = before.state.value.lead!!.id

        // Girar la pantalla: un ViewModel nuevo con el mismo `SavedStateHandle` propone lo mismo.
        val after = vm(handle = handle)
        collect(after)
        assertThat(after.state.value.lead!!.id).isEqualTo(shown)
        assertThat(calls.last()).isNotEmpty()

        // Pasa al día siguiente por la mañana: otro momento, se empieza de cero.
        clock.now = Instant.parse("2026-11-14T07:00:00Z")
        ticks.emit(Unit)
        runCurrent()
        assertThat(calls.last()).isEmpty()
    }

    @Test
    fun dislikeInPairingRatings_changesTheDrink() = runTest(dispatcher) {
        val recipes = listOf(
            summary("sopa", "Crema de calabaza"),
            summary("choco", "Chocolate caliente", DefaultCategories.DRINK_ID, sweet = 3),
            summary("atole", "Atole de vainilla", DefaultCategories.DRINK_ID, sweet = 2),
        )
        val vm = vm(recipes)
        collect(vm)
        fun drink() = vm.state.value.items.first { it.slot == TodaySlot.DRINK }.recipe?.id
        val before = drink()!!
        val other = if (before == "choco") "atole" else "choco"

        // 👎 a la pareja de la sopa con esa bebida: Hoy propone la otra.
        ratings.value = mapOf(listOf("RECIPE:sopa", "RECIPE:$before").sorted().joinToString("|") to false)
        runCurrent()
        assertThat(drink()).isEqualTo(other)
    }
    @Test
    fun today_inSeasonRecipe_preferred() = runTest(dispatcher) {
        // Tres platos iguales salvo los ingredientes: la crema lleva calabaza y chayote, en su mejor momento en
        // noviembre (+8, más que el azar del motor, 6 como mucho).
        val recipes = listOf(
            summary("crema", "Crema de calabaza", ingredients = listOf("calabaza", "chayote", "leche")),
            summary("tacos", "Tacos de canasta", ingredients = listOf("tortillas", "frijoles")),
            summary("arroz", "Arroz blanco", ingredients = listOf("arroz", "agua")),
        )
        val vm = vm(recipes)
        collect(vm)
        // Cinco cenas seguidas (cada una con su semilla): siempre manda la de temporada.
        for (day in 13..17) {
            clock.now = Instant.parse("2026-11-${day}T20:40:00Z")
            ticks.emit(Unit)
            runCurrent()
            assertThat(vm.state.value.lead!!.id).isEqualTo("crema")
        }
    }
}
