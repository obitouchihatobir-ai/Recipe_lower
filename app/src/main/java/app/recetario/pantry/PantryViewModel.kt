package app.recetario.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.recipes.data.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PantryState(
    /** False hasta que llegan los datos (así no parpadea el estado vacío). */
    val loaded: Boolean = false,
    /** En orden alfabético. */
    val items: List<PantryItem> = emptyList(),
    /** Ingredientes de todas las recetas, para el autocompletado. */
    val ingredientNames: List<String> = emptyList(),
)

class PantryViewModel(private val repo: PantryRepository, recipes: RecipeRepository) : ViewModel() {
    val state: StateFlow<PantryState> = combine(repo.observe(), recipes.observeSummaries()) { items, summaries ->
        PantryState(loaded = true, items = items, ingredientNames = summaries.flatMap { it.ingredientNames })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryState())

    /** False si ya estaba (o si el nombre está vacío). */
    suspend fun add(name: String): Boolean = repo.add(name)

    /** Borra y devuelve el artículo, para ofrecer «Deshacer». */
    suspend fun delete(id: String): PantryItem? = repo.delete(id)

    fun restore(item: PantryItem) {
        viewModelScope.launch { repo.restore(item) }
    }

    companion object {
        fun factory(repo: PantryRepository, recipes: RecipeRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { PantryViewModel(repo, recipes) } }
    }
}
