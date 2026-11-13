package app.recetario.home

import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.RecipeSummary
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private class EmptyRepository : RecipeRepository {
    override fun observeSummaries(): Flow<List<RecipeSummary>> = flowOf(emptyList())
    override fun observeRecipe(id: String): Flow<Recipe?> = error("no se usa")
    override suspend fun save(recipe: Recipe) = error("no se usa")
    override suspend fun delete(id: String) = error("no se usa")
    override suspend fun setFavorite(id: String, favorite: Boolean) = error("no se usa")
    override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun saveCategory(category: Category) = error("no se usa")
    override suspend fun deleteCategory(id: String, reassignTo: String) = error("no se usa")
    override fun observeTags(): Flow<List<String>> = error("no se usa")
    override suspend fun renameTag(old: String, new: String) = error("no se usa")
    override suspend fun deleteTag(name: String) = error("no se usa")
    override suspend fun isPhotoReferenced(name: String): Boolean = false
}

private class MovableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun greeting_isReevaluatedOnEachTick() = runTest(dispatcher) {
        val clock = MovableClock(Instant.parse("2026-11-13T11:59:00Z"))
        val ticks = MutableSharedFlow<Unit>()
        val vm = HomeViewModel(EmptyRepository(), clock, ticks = ticks, dispatcher = dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertThat(vm.state.value.greeting).isEqualTo("Buenos días")

        clock.now = Instant.parse("2026-11-13T12:00:00Z")
        ticks.emit(Unit)
        runCurrent()
        assertThat(vm.state.value.greeting).isEqualTo("Buenas tardes")

        clock.now = Instant.parse("2026-11-13T20:00:00Z")
        ticks.emit(Unit)
        runCurrent()
        assertThat(vm.state.value.greeting).isEqualTo("Buenas noches")
    }
}
