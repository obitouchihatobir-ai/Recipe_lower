package app.recetario.recipes.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.model.Category
import app.recetario.recipes.search.RecipeFilter
import app.recetario.recipes.search.applyFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class RecipesState(
    val loaded: Boolean = false,
    /** Recetas que hay en total, sin filtrar. */
    val totalCount: Int = 0,
    val results: List<RecipeCardData> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<String> = emptyList(),
    val filter: RecipeFilter = RecipeFilter(),
    val grid: Boolean = true,
)

class RecipesViewModel(
    private val savedStateHandle: SavedStateHandle,
    repo: RecipeRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(
        RecipeFilter(
            query = savedStateHandle.get<String>(QUERY_KEY).orEmpty(),
            categoryIds = setOfNotNull(savedStateHandle.get<String>(CATEGORY_ID_ARG)),
        ),
    )

    /** El filtro se actualiza al momento (el buscador lo lee de aquí); los resultados llegan en [state]. */
    val filter: StateFlow<RecipeFilter> = _filter.asStateFlow()

    private val grid = MutableStateFlow(savedStateHandle.get<Boolean>(GRID_KEY) ?: true)

    val state: StateFlow<RecipesState> = combine(
        repo.observeSummaries(), repo.observeCategories(), repo.observeTags(), _filter, grid,
    ) { summaries, categories, tags, filter, grid ->
        val byId = categories.associateBy { it.id }
        RecipesState(
            loaded = true,
            totalCount = summaries.size,
            results = summaries.applyFilter(filter).map { it.toCardData(byId) },
            categories = categories,
            tags = tags,
            filter = filter,
            grid = grid,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesState(filter = _filter.value, grid = grid.value))

    fun setFilter(transform: (RecipeFilter) -> RecipeFilter) = _filter.update(transform)

    fun setQuery(query: String) {
        _filter.update { it.copy(query = query) }
        savedStateHandle[QUERY_KEY] = query
    }

    fun toggleCategory(id: String) = _filter.update {
        it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id)
    }

    fun clearCategories() = _filter.update { it.copy(categoryIds = emptySet()) }

    fun toggleLayout() {
        grid.value = !grid.value
        savedStateHandle[GRID_KEY] = grid.value
    }

    companion object {
        /** Argumento de la ruta `Recipes(categoryId)`. */
        const val CATEGORY_ID_ARG = "categoryId"
        private const val GRID_KEY = "grid"
        private const val QUERY_KEY = "query"

        fun factory(repo: RecipeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipesViewModel(createSavedStateHandle(), repo) }
        }
    }
}
