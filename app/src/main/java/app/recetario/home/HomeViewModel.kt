package app.recetario.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.list.RecipeCardData
import app.recetario.recipes.list.toCardData
import app.recetario.recipes.model.Category
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalTime

data class HomeState(
    val greeting: String,
    /** False hasta que llegan los datos (así no parpadea el estado vacío). */
    val loaded: Boolean = false,
    val isEmpty: Boolean = false,
    val categories: List<Category> = emptyList(),
    val recent: List<RecipeCardData> = emptyList(),
    val favorites: List<RecipeCardData> = emptyList(),
)

/** Un aviso a cada cambio de minuto del [clock]. */
fun minuteTicks(clock: Clock): Flow<Unit> = flow {
    while (true) {
        delay(millisToNextMinute(clock))
        emit(Unit)
    }
}

/**
 * [ticks] hace que el saludo se recalcule (cada minuto, así cambia a las 12:00 y a las 20:00); los datos
 * se combinan en [dispatcher], fuera del hilo principal.
 */
class HomeViewModel(
    repo: RecipeRepository,
    private val clock: Clock,
    ticks: Flow<Unit> = minuteTicks(clock),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val greetings: Flow<String> = ticks.onStart { emit(Unit) }.map { greeting() }.distinctUntilChanged()

    val state: StateFlow<HomeState> =
        combine(repo.observeSummaries(), repo.observeCategories(), greetings) { summaries, categories, greeting ->
            val byId = categories.associateBy { it.id }
            HomeState(
                greeting = greeting,
                loaded = true,
                isEmpty = summaries.isEmpty(),
                categories = categories,
                recent = recentRecipes(summaries).map { it.toCardData(byId) },
                favorites = favoriteRecipes(summaries).map { it.toCardData(byId) },
            )
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(greeting = greeting()))

    private fun greeting() = greetingFor(LocalTime.now(clock))

    companion object {
        fun factory(repo: RecipeRepository, clock: Clock): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repo, clock) }
        }
    }
}
