package app.recetario.home

import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.RecipeSummary
import app.recetario.backup.BackupMessages
import app.recetario.backup.BackupNotice
import app.recetario.settings.BackupStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
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

private class EmptyRepository(private val firstRecipeAt: Flow<Instant?> = flowOf(null)) : RecipeRepository {
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
    override fun observeFirstRecipeAt(): Flow<Instant?> = firstRecipeAt
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
        val vm = HomeViewModel(EmptyRepository(), flowOf(BackupStatus()), clock, ticks = ticks, dispatcher = dispatcher, dismissNotice = {})
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

    @Test
    fun backupNotice_followsTheStatus_theFirstRecipe_andTheClock() = runTest(dispatcher) {
        val clock = MovableClock(Instant.parse("2026-11-15T10:00:00Z"))
        val ticks = MutableSharedFlow<Unit>()
        val firstRecipeAt = MutableStateFlow<Instant?>(null)
        val status = MutableStateFlow(BackupStatus(lastExternal = Instant.parse("2026-10-20T10:00:00Z")))
        val vm = HomeViewModel(EmptyRepository(firstRecipeAt), status, clock, ticks = ticks, dispatcher = dispatcher, dismissNotice = {})
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        // Sin recetas no hay nada que recordar.
        assertThat(vm.state.value.backupNotice).isNull()
        assertThat(vm.state.value.today).isEqualTo(LocalDate.of(2026, 11, 15))

        firstRecipeAt.value = Instant.parse("2026-01-01T10:00:00Z")
        runCurrent()
        // 26 días desde la última copia fuera del teléfono.
        assertThat(vm.state.value.backupNotice).isNull()

        // Pasan los días: a los 31, el recordatorio.
        clock.now = Instant.parse("2026-11-20T10:00:01Z")
        ticks.emit(Unit)
        runCurrent()
        assertThat(vm.state.value.backupNotice).isEqualTo(BackupNotice.Reminder)
        assertThat(vm.state.value.today).isEqualTo(LocalDate.of(2026, 11, 20))

        // Un error de la copia automática va antes que el recordatorio.
        status.value = status.value.copy(lastError = BackupMessages.DRIVE_FAILED)
        runCurrent()
        assertThat(vm.state.value.backupNotice).isEqualTo(BackupNotice.Error(localError = null, driveError = BackupMessages.DRIVE_FAILED))
        // Y si además falló la copia del teléfono, las dos.
        status.value = status.value.copy(localError = BackupMessages.LOCAL_FAILED)
        runCurrent()
        assertThat(vm.state.value.backupNotice)
            .isEqualTo(BackupNotice.Error(localError = BackupMessages.LOCAL_FAILED, driveError = BackupMessages.DRIVE_FAILED))

        status.value = BackupStatus(lastExternal = clock.now)
        runCurrent()
        assertThat(vm.state.value.backupNotice).isNull()
    }

    @Test
    fun dismissingTheNotice_savesTheDayAndKind_andAnotherNoticeShowsAgain() = runTest(dispatcher) {
        val clock = MovableClock(Instant.parse("2026-11-20T10:00:00Z"))
        val status = MutableStateFlow(BackupStatus())
        val saved = mutableListOf<String>()
        val vm = HomeViewModel(
            EmptyRepository(flowOf(Instant.parse("2026-01-01T10:00:00Z"))), status, clock,
            ticks = MutableSharedFlow(), dispatcher = dispatcher,
            dismissNotice = { key -> saved += key; status.value = status.value.copy(noticeDismissed = key) },
        )
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertThat(vm.state.value.backupNotice).isEqualTo(BackupNotice.Reminder)

        vm.dismissBackupNotice()
        runCurrent()

        assertThat(saved).containsExactly("2026-11-20|recordatorio")
        assertThat(vm.state.value.backupNotice).isNull()
        // Otro aviso (un error de Drive) sí sale el mismo día.
        status.value = status.value.copy(lastError = BackupMessages.DRIVE_FAILED)
        runCurrent()
        assertThat(vm.state.value.backupNotice).isEqualTo(BackupNotice.Error(localError = null, driveError = BackupMessages.DRIVE_FAILED))
    }
}
