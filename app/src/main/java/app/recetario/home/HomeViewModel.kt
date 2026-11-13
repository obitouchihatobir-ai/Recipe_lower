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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

class HomeViewModel(repo: RecipeRepository, private val clock: Clock) : ViewModel() {
    val state: StateFlow<HomeState> =
        combine(repo.observeSummaries(), repo.observeCategories()) { summaries, categories ->
            val byId = categories.associateBy { it.id }
            HomeState(
                greeting = greeting(),
                loaded = true,
                isEmpty = summaries.isEmpty(),
                categories = categories,
                recent = recentRecipes(summaries).map { it.toCardData(byId) },
                favorites = favoriteRecipes(summaries).map { it.toCardData(byId) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(greeting = greeting()))

    private fun greeting() = greetingFor(LocalTime.now(clock))

    companion object {
        fun factory(repo: RecipeRepository, clock: Clock): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repo, clock) }
        }
    }
}
