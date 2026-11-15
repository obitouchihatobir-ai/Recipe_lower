package app.recetario.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.backup.BackupNotice
import app.recetario.backup.backupNoticeFor
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.list.RecipeCardData
import app.recetario.recipes.list.toCardData
import app.recetario.recipes.model.Category
import app.recetario.settings.BackupStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class HomeState(
    val greeting: String,
    /** False hasta que llegan los datos (así no parpadea el estado vacío). */
    val loaded: Boolean = false,
    val isEmpty: Boolean = false,
    val categories: List<Category> = emptyList(),
    val recent: List<RecipeCardData> = emptyList(),
    val favorites: List<RecipeCardData> = emptyList(),
    /** Tarjeta sobre las copias: el error de la copia automática o el recordatorio de los 30 días. */
    val backupNotice: BackupNotice? = null,
    /** Hoy, según el reloj (la tarjeta de las copias se puede ocultar hasta mañana). */
    val today: LocalDate? = null,
)

/** Un aviso a cada cambio de minuto del [clock]. */
fun minuteTicks(clock: Clock): Flow<Unit> = flow {
    while (true) {
        delay(millisToNextMinute(clock))
        emit(Unit)
    }
}

/**
 * [ticks] hace que el saludo y la tarjeta de las copias se recalculen (cada minuto, así el saludo cambia a las
 * 12:00 y a las 20:00 y el recordatorio aparece al pasar los 30 días); los datos se combinan en [dispatcher],
 * fuera del hilo principal.
 */
class HomeViewModel(
    repo: RecipeRepository,
    backupStatus: Flow<BackupStatus>,
    private val clock: Clock,
    ticks: Flow<Unit> = minuteTicks(clock),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val now: Flow<Instant> = ticks.onStart { emit(Unit) }.map { clock.instant() }

    val state: StateFlow<HomeState> =
        combine(
            repo.observeSummaries(), repo.observeCategories(), now, backupStatus, repo.observeFirstRecipeAt(),
        ) { summaries, categories, at, status, firstRecipeAt ->
            val byId = categories.associateBy { it.id }
            val local = at.atZone(clock.zone)
            HomeState(
                greeting = greetingFor(local.toLocalTime()),
                loaded = true,
                isEmpty = summaries.isEmpty(),
                categories = categories,
                recent = recentRecipes(summaries).map { it.toCardData(byId) },
                favorites = favoriteRecipes(summaries).map { it.toCardData(byId) },
                backupNotice = backupNoticeFor(at, null, status.lastError, status.lastExternal, firstRecipeAt),
                today = local.toLocalDate(),
            )
        }.flowOn(dispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeState(greeting = greetingFor(LocalTime.now(clock)), today = LocalDate.now(clock)),
            )

    companion object {
        fun factory(repo: RecipeRepository, backupStatus: Flow<BackupStatus>, clock: Clock): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repo, backupStatus, clock) }
        }
    }
}
