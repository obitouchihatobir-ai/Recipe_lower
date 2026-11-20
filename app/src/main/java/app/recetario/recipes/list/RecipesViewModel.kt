package app.recetario.recipes.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.diary.CookStats
import app.recetario.diary.DiaryRepository
import app.recetario.recipes.data.RecipeRepository
import app.recetario.pantry.DEFAULT_PANTRY_BASICS
import app.recetario.pantry.PantryRepository
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.search.RecipeFilter
import app.recetario.recipes.search.applyFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

private data class RecipeInputs(
    val summaries: List<RecipeSummary>,
    val stats: Map<String, CookStats>,
    val pantry: List<String>,
    val basics: List<String>,
)

class RecipesViewModel(
    private val savedStateHandle: SavedStateHandle,
    repo: RecipeRepository,
    /** Estadísticas del diario, para «Más cocinadas» y «Mejor valoradas». */
    diaryStats: Flow<Map<String, CookStats>> = flowOf(emptyMap()),
    /** Nombres de la despensa y sus básicos, para «Con mi despensa». */
    pantry: Flow<List<String>> = flowOf(emptyList()),
    basics: Flow<List<String>> = flowOf(DEFAULT_PANTRY_BASICS),
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
        combine(repo.observeSummaries(), diaryStats, pantry, basics) { recipes, stats, p, b -> RecipeInputs(recipes, stats, p, b) },
        repo.observeCategories(), repo.observeTags(), _filter, grid,
    ) { inputs, categories, tags, filter, grid ->
        val summaries = inputs.summaries
        val byId = categories.associateBy { it.id }
        RecipesState(
            loaded = true,
            totalCount = summaries.size,
            results = summaries.applyFilter(filter, stats = inputs.stats, pantry = inputs.pantry, basics = inputs.basics).map { it.toCardData(byId) },
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

        fun factory(
            repo: RecipeRepository,
            diary: DiaryRepository,
            pantry: PantryRepository,
            basics: Flow<List<String>>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecipesViewModel(
                    createSavedStateHandle(), repo, diary.observeStats(),
                    pantry.observe().map { items -> items.map { it.name } }, basics,
                )
            }
        }
    }
}
