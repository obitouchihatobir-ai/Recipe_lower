package app.recetario.cooking

import android.os.Bundle
import android.os.Parcel
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CookingViewModelTest {
    private lateinit var container: AppContainer
    private var clock = 5_000L
    private val timers = TimerManager { clock }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        container = installTestContainer()
        runBlocking {
            container.recipeRepository.save(
                sampleRecipe(
                    "r1", "Crema de calabaza",
                    ingredients = listOf(
                        ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
                        ingredient("i2", "Mantequilla", 20.0, MeasureUnit.GRAM),
                        ingredient("i3", "Caldo de verduras", 750.0, MeasureUnit.MILLILITER),
                    ),
                    steps = listOf(
                        Step("s1", "Pela la calabaza y córtala en cubos.", emptyList(), null, setOf("i1")),
                        Step("s2", "Sofríe la cebolla y el ajo picados hasta que estén transparentes.", emptyList(), 300, setOf("i2")),
                        Step("s3", "Añade el caldo, cuece y tritura.", emptyList(), 1200, setOf("i1", "i3")),
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        container.database.close()
        Dispatchers.resetMain()
    }

    private fun handle(recipeId: String = "r1", servings: Int = 4) =
        SavedStateHandle(mapOf(CookingViewModel.RECIPE_ID_ARG to recipeId, CookingViewModel.SERVINGS_ARG to servings))

    private fun vm(handle: SavedStateHandle = handle()) = CookingViewModel(handle, container.recipeRepository, timers)

    private fun CookingViewModel.await(predicate: (CookingUiState) -> Boolean): CookingUiState =
        runBlocking { withTimeout(10_000) { state.first(predicate) } }

    private fun CookingViewModel.loaded(): CookingUiState = await { !it.loading }

    /** Lo que haría Android al matar el proceso: el estado pasa por un Bundle y un Parcel y vuelve. */
    private fun SavedStateHandle.afterProcessDeath(): SavedStateHandle {
        val bundle = bundleOf(*keys().map { it to get<Any?>(it) }.toTypedArray())
        val parcel = Parcel.obtain()
        try {
            bundle.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Bundle.CREATOR.createFromParcel(parcel).apply { classLoader = javaClass.classLoader }
            @Suppress("DEPRECATION")
            return SavedStateHandle(restored.keySet().associateWith { restored.get(it) })
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun markDone_advancesAndFinishesAtEnd() {
        val vm = vm()
        val start = vm.loaded()
        assertThat(start.recipe?.title).isEqualTo("Crema de calabaza")
        assertThat(start.currentIndex).isEqualTo(0)
        assertThat(start.doneSteps).isEmpty()
        assertThat(start.finished).isFalse()

        vm.markDone()
        val second = vm.await { it.currentIndex == 1 }
        assertThat(second.doneSteps).containsExactly(0)
        assertThat(vm.finished).isFalse()

        vm.markDone()
        vm.markDone()
        val end = vm.await { it.finished }
        assertThat(end.currentIndex).isEqualTo(3)
        assertThat(end.doneSteps).containsExactly(0, 1, 2)
        assertThat(vm.finished).isTrue()

        // Ya está lista: otro «Hecho» no hace nada.
        vm.markDone()
        assertThat(vm.await { it.finished }.currentIndex).isEqualTo(3)
    }

    @Test
    fun goTo_previousStep() {
        val vm = vm()
        vm.loaded()
        vm.markDone()
        vm.markDone()
        vm.await { it.currentIndex == 2 }

        vm.goTo(0)
        val back = vm.await { it.currentIndex == 0 }
        assertThat(back.doneSteps).containsExactly(0, 1) // volver no deshace lo hecho
        assertThat(vm.finished).isFalse()

        // Al marcarlo otra vez sigue donde lo dejó: en el primer paso sin hacer.
        vm.markDone()
        assertThat(vm.await { it.currentIndex != 0 }.currentIndex).isEqualTo(2)

        vm.goTo(7) // fuera de rango: no cambia nada
        vm.goTo(-1)
        assertThat(vm.await { true }.currentIndex).isEqualTo(2)
    }

    @Test
    fun goTo_afterFinishing_hidesTheFinishCard() {
        val vm = vm()
        vm.loaded()
        repeat(3) { vm.markDone() }
        vm.await { it.finished }
        vm.goTo(1)
        assertThat(vm.await { it.currentIndex == 1 }.finished).isFalse()
        vm.markDone()
        assertThat(vm.await { it.finished }.currentIndex).isEqualTo(3)
    }

    @Test
    fun state_survivesRecreation_viaSavedStateHandle() {
        val handle = handle()
        val first = vm(handle)
        first.loaded()
        first.markDone()
        first.toggleIngredient("i1")
        first.toggleIngredient("i3")
        first.toggleIngredient("i3")
        first.startTimer(1)
        first.await { it.currentIndex == 1 && it.checkedIngredients == setOf("i1") && "s2" in it.timers }

        // Giro (mismo SavedStateHandle) y muerte del proceso (pasa por un Parcel); los temporizadores viven en el gestor.
        for (restored in listOf(handle, handle.afterProcessDeath())) {
            val again = vm(restored).loaded()
            assertThat(again.currentIndex).isEqualTo(1)
            assertThat(again.doneSteps).containsExactly(0)
            assertThat(again.checkedIngredients).containsExactly("i1")
            assertThat(again.timers.keys).containsExactly("s2")
            assertThat(again.timers.getValue("s2").isRunning).isTrue()
            assertThat(again.servings).isEqualTo(4)
        }
    }

    @Test
    fun quantities_useTheServingsArg() {
        val s = vm(handle(servings = 6)).loaded()
        assertThat(s.servings).isEqualTo(6)
        assertThat(s.quantities["i1"]).isEqualTo("1,2 kg")
        assertThat(s.quantities["i2"]).isEqualTo("30 g")
    }

    @Test
    fun missingServingsArg_usesTheRecipeServings() {
        val s = CookingViewModel(SavedStateHandle(mapOf(CookingViewModel.RECIPE_ID_ARG to "r1")), container.recipeRepository, timers).loaded()
        assertThat(s.servings).isEqualTo(4)
        assertThat(s.quantities["i1"]).isEqualTo("800 g")
    }

    @Test
    fun startTimer_isKeyedByStep_withAStepLabel_andCanBePausedExtendedAndDismissed() {
        val vm = vm()
        vm.loaded()
        vm.startTimer(1)
        val started = vm.await { "s2" in it.timers }.timers.getValue("s2")
        assertThat(started.label).isEqualTo("Paso 2: Sofríe la cebolla y el ajo…")
        assertThat(started.totalSeconds).isEqualTo(300)
        assertThat(started.isRunning).isTrue()
        assertThat(timers.timers.value.single().id).isEqualTo("s2")

        clock += 2_000
        assertThat(vm.now()).isEqualTo(clock)
        vm.toggleTimer("s2")
        assertThat(vm.await { it.timers["s2"]?.isPaused == true }.timers.getValue("s2").remainingMs(clock)).isEqualTo(298_000)
        vm.addMinute("s2")
        assertThat(vm.await { it.timers["s2"]?.totalSeconds == 360 }.timers.getValue("s2").remainingMs(clock)).isEqualTo(358_000)
        vm.toggleTimer("s2")
        vm.await { it.timers["s2"]?.isRunning == true }

        vm.dismissTimer("s2")
        vm.await { it.timers.isEmpty() }
        assertThat(timers.timers.value).isEmpty()
    }

    @Test
    fun startTimer_onAStepWithoutTime_doesNothing() {
        val vm = vm()
        vm.loaded()
        vm.startTimer(0)
        vm.startTimer(9)
        assertThat(timers.timers.value).isEmpty()
    }

    @Test
    fun timersOfOtherRecipes_areNotShown() {
        timers.start("Paso 1: Otra receta", 60, id = "otro-paso")
        val s = vm().loaded()
        assertThat(s.timers).isEmpty()
    }

    @Test
    fun toggleIngredient_marksAndUnmarks() {
        val vm = vm()
        vm.loaded()
        vm.toggleIngredient("i2")
        vm.await { it.checkedIngredients == setOf("i2") }
        vm.toggleIngredient("i2")
        vm.await { it.checkedIngredients.isEmpty() }
    }

    @Test
    fun missingRecipe_stopsLoadingWithoutRecipe() {
        val s = vm(handle(recipeId = "no-existe")).loaded()
        assertThat(s.recipe).isNull()
        assertThat(s.finished).isFalse()
    }
}
