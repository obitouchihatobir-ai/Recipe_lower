package app.recetario.recipes.detail

import org.junit.Rule
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.MainDispatcherRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.recetario.recipes.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DetailViewModelTest {
    /** Se cierra al final: después de desechar los ViewModel (`main`). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    private fun eight() = (1..8).map { ingredient("i$it", "Ingrediente $it", 100.0 * it, MeasureUnit.GRAM) }

    private fun save(vararg ingredients: app.recetario.recipes.model.Ingredient) = runBlocking {
        container.recipeRepository.save(sampleRecipe("r1", "Crema", ingredients = ingredients.toList()))
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        main.track(DetailViewModel("r1", container.recipeRepository, container.shoppingRepository, container.photoStore, handle))

    private fun DetailViewModel.loaded(): DetailUiState =
        runBlocking { withTimeout(10_000) { state.first { !it.loading } } }

    /** Espera a que el estado cumpla [predicate] (la combinación de flujos puede tardar un instante en otro hilo). */
    private fun DetailViewModel.await(predicate: (DetailUiState) -> Boolean): DetailUiState =
        runBlocking { withTimeout(10_000) { state.first(predicate) } }

    @Test
    fun stateStartsLoadingWithoutRecipe() {
        save()
        val s = vm().state.value
        assertThat(s.loading).isTrue()
        assertThat(s.recipe).isNull()
    }

    @Test
    fun loadedState_hasRecipeServingsEmojiAndQuantities() {
        save(ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM))
        val s = vm().loaded()
        assertThat(s.recipe?.title).isEqualTo("Crema")
        assertThat(s.servings).isEqualTo(4)
        assertThat(s.categoryEmoji).isEqualTo("🍲")
        assertThat(s.quantities["i1"]).isEqualTo("800 g")
    }

    @Test
    fun setServings_6_updatesFormatted() {
        save(ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM))
        val vm = vm()
        vm.loaded()
        vm.setServings(6)
        assertThat(vm.await { it.servings == 6 }.quantities["i1"]).isEqualTo("1,2 kg")
    }

    @Test
    fun setServings_isClampedTo1And99() {
        save(ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM))
        val vm = vm()
        vm.loaded()
        vm.setServings(0)
        vm.await { it.servings == 1 }
        vm.setServings(150)
        vm.await { it.servings == 99 }
    }

    @Test
    fun toggleIngredient_marksAndUnmarks_andSurvivesANewViewModelWithTheSameHandle() {
        save(*eight().toTypedArray())
        val handle = SavedStateHandle()
        val vm = vm(handle)
        vm.loaded()
        vm.toggleIngredient("i3")
        assertThat(vm.await { "i3" in it.checkedIngredientIds }.checkedIngredientIds).containsExactly("i3")
        assertThat(vm(handle).loaded().checkedIngredientIds).containsExactly("i3")
        vm.toggleIngredient("i3")
        vm.await { it.checkedIngredientIds.isEmpty() }
    }

    @Test
    fun sendMissingToShopping_excludesChecked_returnsCount() {
        save(*eight().toTypedArray())
        val vm = vm()
        vm.loaded()
        vm.toggleIngredient("i1")
        // El cambio llega al estado por otro hilo: se espera antes de enviar (si no, a veces se enviaban los 8).
        vm.await { "i1" in it.checkedIngredientIds }
        val added = runBlocking { vm.sendMissingToShopping() }
        assertThat(added).isEqualTo(7)
        val items = runBlocking { container.shoppingRepository.observe().first() }.values.flatten()
        assertThat(items).hasSize(7)
        assertThat(items.map { it.name }).doesNotContain("Ingrediente 1")
    }

    @Test
    fun toggleFavorite_flipsTheFlag() {
        save()
        val vm = vm()
        vm.loaded()
        vm.toggleFavorite()
        runBlocking { withTimeout(10_000) { vm.state.first { it.recipe?.favorite == true } } }
        vm.toggleFavorite()
        runBlocking { withTimeout(10_000) { vm.state.first { it.recipe?.favorite == false } } }
    }

    @Test
    fun delete_removesRecipe() {
        save()
        val vm = vm()
        vm.loaded()
        vm.delete()
        assertThat(runBlocking { withTimeout(10_000) { vm.events.first() } }).isEqualTo(DetailEvent.Deleted)
        assertThat(runBlocking { container.recipeRepository.observeRecipe("r1").first() }).isNull()
    }

    @Test
    fun delete_whenTheRepositoryFails_reportsFailureWithoutCrashing() {
        save()
        val failing = object : RecipeRepository by container.recipeRepository {
            override suspend fun delete(id: String) = throw IllegalStateException("disk I/O error")
        }
        val vm = main.track(DetailViewModel("r1", failing, container.shoppingRepository, container.photoStore, SavedStateHandle()))
        vm.loaded()
        vm.delete()
        assertThat(runBlocking { withTimeout(10_000) { vm.events.first() } }).isEqualTo(DetailEvent.DeleteFailed)
        assertThat(runBlocking { container.recipeRepository.observeRecipe("r1").first() }).isNotNull()
    }

    @Test
    fun baseServingsAbove24_canGoUpAndDown() {
        runBlocking {
            container.recipeRepository.save(
                sampleRecipe("r1", "Paella", ingredients = listOf(ingredient("i1", "Arroz", 300.0, MeasureUnit.GRAM)))
                    .copy(servings = 30),
            )
        }
        val vm = vm()
        assertThat(vm.loaded().servings).isEqualTo(30)
        vm.setServings(vm.state.value.servings + 1)
        vm.await { it.servings == 31 }
        vm.setServings(vm.state.value.servings - 2)
        vm.await { it.servings == 29 }
    }

    @Test
    fun shareText_usesTheChosenServings() {
        save(ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM))
        val vm = vm()
        vm.loaded()
        vm.setServings(6)
        vm.await { it.servings == 6 }
        assertThat(vm.shareText()).contains("• Calabaza — 1,2 kg")
    }

    /** Cuenta cuántas veces se escucha `observeRecipe` (cada suscripción es una consulta más a Room). */
    private class CountingRecipes(private val real: RecipeRepository) : RecipeRepository by real {
        val subscriptions = AtomicInteger()
        override fun observeRecipe(id: String): Flow<Recipe?> = real.observeRecipe(id).onStart { subscriptions.incrementAndGet() }
    }

    @Test
    fun withPairing_theRecipeIsObservedOnce() {
        save(ingredient("i1", "Chile poblano", 2.0, MeasureUnit.PIECE))
        val recipes = CountingRecipes(container.recipeRepository)
        val vm = main.track(DetailViewModel(
            "r1", recipes, container.shoppingRepository, container.photoStore, SavedStateHandle(),
            pairing = container.pairingRepository,
        ))
        val s = vm.await { it.pairingSubject != null }
        assertThat(s.recipe?.title).isEqualTo("Crema")
        assertThat(recipes.subscriptions.get()).isEqualTo(1)
    }
}
