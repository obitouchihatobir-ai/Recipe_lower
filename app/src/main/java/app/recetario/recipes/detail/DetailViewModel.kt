package app.recetario.recipes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.AppContainer
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.list.DEFAULT_EMOJI
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.scaleQuantity
import app.recetario.shopping.ShoppingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DetailEvent { Deleted, DeleteFailed }

data class DetailUiState(
    /** True hasta que llegan la receta y las categorías (la pantalla no pinta el marcador de posición antes). */
    val loading: Boolean = true,
    /** Null mientras carga o si la receta ya no existe. */
    val recipe: Recipe? = null,
    val categoryName: String = "",
    val categoryEmoji: String = DEFAULT_EMOJI,
    val servings: Int = 1,
    val checkedIngredientIds: Set<String> = emptySet(),
    /** Cantidad ya escalada y formateada de cada ingrediente, por id. */
    val quantities: Map<String, String> = emptyMap(),
)

/** Estado del detalle. Porciones y casillas se guardan en [savedStateHandle] (sobreviven al giro), no en disco. */
class DetailViewModel(
    private val id: String,
    private val repo: RecipeRepository,
    private val shopping: ShoppingRepository,
    val photoStore: PhotoStore,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val servingsOverride = savedStateHandle.getStateFlow(SERVINGS_KEY, 0)
    private val checked = savedStateHandle.getStateFlow(CHECKED_KEY, arrayListOf<String>())

    val state: StateFlow<DetailUiState> = combine(
        repo.observeRecipe(id), repo.observeCategories(), servingsOverride, checked,
    ) { recipe, categories, override, checkedIds ->
        if (recipe == null) {
            DetailUiState(loading = false)
        } else {
            val servings = if (override > 0) override else recipe.servings
            val category = categories.firstOrNull { it.id == recipe.categoryId }
            DetailUiState(
                loading = false,
                recipe = recipe,
                categoryName = category?.name.orEmpty(),
                categoryEmoji = category?.emoji ?: DEFAULT_EMOJI,
                servings = servings,
                checkedIngredientIds = checkedIds.filter { c -> recipe.ingredients.any { it.id == c } }.toSet(),
                quantities = recipe.ingredients.associate {
                    it.id to formatQuantity(scaleQuantity(it.quantity, recipe.servings, servings), it.unit, it.customUnit)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun setServings(n: Int) {
        savedStateHandle[SERVINGS_KEY] = n.coerceIn(MIN_SERVINGS, MAX_SERVINGS)
    }

    fun toggleIngredient(ingredientId: String) {
        val current = savedStateHandle.get<ArrayList<String>>(CHECKED_KEY) ?: arrayListOf()
        savedStateHandle[CHECKED_KEY] =
            if (ingredientId in current) ArrayList(current - ingredientId) else ArrayList(current + ingredientId)
    }

    /** Añade a la lista de compra los ingredientes sin marcar y devuelve cuántos se añadieron. */
    suspend fun sendMissingToShopping(): Int {
        val s = state.value
        val recipe = s.recipe ?: return 0
        return shopping.addFromRecipe(recipe, s.servings, s.checkedIngredientIds)
    }

    fun toggleFavorite() {
        val recipe = state.value.recipe ?: return
        viewModelScope.launch { repo.setFavorite(id, !recipe.favorite) }
    }

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)

    /** Resultado del borrado (una sola vez cada uno). */
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    /**
     * Borra la receta. Corre en el ámbito del ViewModel (no en el de la pantalla); un fallo se avisa con [DetailEvent.DeleteFailed] en vez de cerrar la app.
     */
    fun delete() {
        viewModelScope.launch {
            val event = try {
                repo.delete(id)
                DetailEvent.Deleted
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DetailEvent.DeleteFailed
            }
            _events.trySend(event)
        }
    }

    /** Texto para compartir con las porciones elegidas; vacío si la receta no está cargada. */
    fun shareText(): String {
        val s = state.value
        val recipe = s.recipe ?: return ""
        return formatRecipeAsText(recipe, s.servings, s.categoryEmoji)
    }

    companion object {
        const val MIN_SERVINGS = 1
        const val MAX_SERVINGS = 99
        private const val SERVINGS_KEY = "servings"
        private const val CHECKED_KEY = "checked"

        fun factory(container: AppContainer, recipeId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetailViewModel(
                    id = recipeId,
                    repo = container.recipeRepository,
                    shopping = container.shoppingRepository,
                    photoStore = container.photoStore,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
