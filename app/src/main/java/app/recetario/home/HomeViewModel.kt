package app.recetario.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.backup.BackupNotice
import app.recetario.backup.backupNoticeFor
import app.recetario.backup.noticeDismissalKey
import app.recetario.diary.CookStats
import app.recetario.diary.DiaryRepository
import app.recetario.diary.longTimeNoCook
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.list.RecipeCardData
import app.recetario.recipes.list.toCardData
import app.recetario.recipes.model.Category
import app.recetario.settings.BackupStatus
import app.recetario.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    /** «Hace tiempo que no la haces»: hechas alguna vez, la última hace más de 30 días (como mucho 6, la más antigua primero). */
    val longTimeNoCook: List<RecipeCardData> = emptyList(),
    /**
     * Tarjeta sobre las copias: los errores de la copia automática o el recordatorio de los 30 días; null si no hay
     * o si hoy se ocultó ese mismo aviso.
     */
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
 * fuera del hilo principal. [dismissNotice] guarda (en el estado del teléfono) qué aviso se ocultó y qué día.
 */
class HomeViewModel(
    repo: RecipeRepository,
    backupStatus: Flow<BackupStatus>,
    private val clock: Clock,
    ticks: Flow<Unit> = minuteTicks(clock),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val dismissNotice: suspend (key: String) -> Unit,
    /** Estadísticas del diario por receta. */
    diaryStats: Flow<Map<String, CookStats>> = flowOf(emptyMap()),
) : ViewModel() {
    private val now: Flow<Instant> = ticks.onStart { emit(Unit) }.map { clock.instant() }

    val state: StateFlow<HomeState> =
        combine(
            combine(repo.observeSummaries(), diaryStats) { recipes, stats -> recipes to stats },
            repo.observeCategories(), now, backupStatus, repo.observeFirstRecipeAt(),
        ) { (summaries, stats), categories, at, status, firstRecipeAt ->
            val byId = categories.associateBy { it.id }
            val local = at.atZone(clock.zone)
            val today = local.toLocalDate()
            val notice = backupNoticeFor(at, status.localError, status.lastError, status.lastExternal, firstRecipeAt)
            HomeState(
                greeting = greetingFor(local.toLocalTime()),
                loaded = true,
                isEmpty = summaries.isEmpty(),
                categories = categories,
                recent = recentRecipes(summaries).map { it.toCardData(byId) },
                favorites = favoriteRecipes(summaries).map { it.toCardData(byId) },
                longTimeNoCook = summaries.associateBy { it.id }.let { recipes ->
                    longTimeNoCook(stats.filterKeys { it in recipes }, today).map { recipes.getValue(it).toCardData(byId) }
                },
                backupNotice = notice?.takeIf { noticeDismissalKey(today, it) != status.noticeDismissed },
                today = today,
            )
        }.flowOn(dispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeState(greeting = greetingFor(LocalTime.now(clock)), today = LocalDate.now(clock)),
            )

    /** La ✕ de la tarjeta: ese aviso no vuelve a salir hoy (otro aviso, u otro día, sí). */
    fun dismissBackupNotice() {
        val current = state.value
        val notice = current.backupNotice ?: return
        val today = current.today ?: return
        viewModelScope.launch {
            // Si no se puede guardar, la tarjeta sigue ahí: no es grave.
            runCatching { dismissNotice(noticeDismissalKey(today, notice)) }
        }
    }

    companion object {
        fun factory(
            repo: RecipeRepository,
            settings: SettingsRepository,
            clock: Clock,
            diary: DiaryRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    repo, settings.backupStatus, clock,
                    dismissNotice = { key -> settings.updateBackupStatus { it.copy(noticeDismissed = key) } },
                    diaryStats = diary.observeStats(),
                )
            }
        }
    }
}
