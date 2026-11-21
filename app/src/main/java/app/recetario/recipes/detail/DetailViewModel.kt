package app.recetario.recipes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.AppContainer
import app.recetario.diary.CookLogEntry
import app.recetario.diary.CookStats
import app.recetario.diary.DiaryRepository
import app.recetario.diary.LogEntryEditor
import app.recetario.diary.statsByRecipe
import app.recetario.pairing.FlavorGuide
import app.recetario.pairing.PairingItem
import app.recetario.pairing.PairingRepository
import app.recetario.pairing.Suggestion
import app.recetario.pairing.ratableKey
import app.recetario.pairing.suggestPairings
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.list.DEFAULT_EMOJI
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.scaleQuantity
import app.recetario.shopping.ShoppingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

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
    /** Lo que dice el diario de esta receta (null si nunca se hizo). */
    val stats: CookStats? = null,
    /** Sus entradas del diario, la más reciente primero. */
    val history: List<CookLogEntry> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
    /** Maridaje: la receta como elemento del motor (null sin maridaje o mientras carga). */
    val pairingSubject: PairingItem? = null,
    /** Las 3 mejores parejas (primero las recetas propias, luego el catálogo); las de 👎 no salen. */
    val pairings: List<Suggestion> = emptyList(),
    /** «Le iría bien…»: hasta 6 sabores que todavía no lleva. */
    val goesWellWith: List<String> = emptyList(),
)

/** Lo que el detalle muestra del maridaje. */
private data class DetailPairingInfo(val subject: PairingItem?, val top: List<Suggestion>, val goesWellWith: List<String>)

/** Cuántas parejas salen en el detalle. */
const val DETAIL_PAIRINGS = 3

/** Estado del detalle. Porciones y casillas se guardan en [savedStateHandle] (sobreviven al giro), no en disco. */
class DetailViewModel(
    private val id: String,
    private val repo: RecipeRepository,
    private val shopping: ShoppingRepository,
    val photoStore: PhotoStore,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    diary: DiaryRepository? = null,
    /** Crea el editor de «Lo hice» (null: sin diario, en algunas pruebas). */
    newLogEditor: ((CoroutineScope) -> LogEntryEditor)? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** Maridaje (null: sin maridaje, en algunas pruebas). */
    private val pairing: PairingRepository? = null,
    /** Donde se guardan las valoraciones: no se cortan al salir de la pantalla. */
    private val writeScope: CoroutineScope? = null,
) : ViewModel() {

    /** «Lo hice»: la entrada del diario que se está apuntando a mano. */
    val logEditor: LogEntryEditor? = newLogEditor?.invoke(viewModelScope)

    private val history = diary?.observeEntries()?.map { all -> all.filter { it.recipeId == id } } ?: flowOf(emptyList<CookLogEntry>())

    private val servingsOverride = savedStateHandle.getStateFlow(SERVINGS_KEY, 0)
    private val checked = savedStateHandle.getStateFlow(CHECKED_KEY, arrayListOf<String>())

    /** La receta, escuchada una sola vez: la usan el detalle y «Le iría bien…». */
    private val recipe: Flow<Recipe?> = repo.observeRecipe(id)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val pairingInfo: Flow<DetailPairingInfo?> = pairing?.let { p ->
        val top = p.observeData().map { data ->
            val subject = data.recipes.firstOrNull { it.id == id }
            subject to subject?.let { suggestPairings(it, data.all, data.ratings, DETAIL_PAIRINGS, data.weights) }.orEmpty()
        }
        // «Le iría bien…» solo depende de los ingredientes: no se recalcula al marcar favorita ni al cambiar otra cosa.
        val goesWell = recipe.map { r -> r?.ingredients?.map { it.name }.orEmpty() }
            .distinctUntilChanged()
            .map { FlavorGuide.goesWellWith(it) }
        combine(top, goesWell) { (subject, suggestions), flavors ->
            DetailPairingInfo(subject = subject, top = suggestions, goesWellWith = flavors)
        }.flowOn(Dispatchers.Default)
            // El detalle no espera al maridaje: sale en cuanto llega la receta y el maridaje se suma después.
            .onStart<DetailPairingInfo?> { emit(null) }
    } ?: flowOf(null)

    private val base: Flow<DetailUiState> = combine(
        recipe, repo.observeCategories(), servingsOverride, checked, history,
    ) { recipe, categories, override, checkedIds, entries ->
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
                stats = statsByRecipe(entries)[id],
                history = entries,
                today = LocalDate.now(clock),
            )
        }
    }

    val state: StateFlow<DetailUiState> = combine(base, pairingInfo) { s, info ->
        if (info == null || s.recipe == null) s
        else s.copy(pairingSubject = info.subject, pairings = info.top, goesWellWith = info.goesWellWith)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    /** 👍/👎 de una pareja del detalle ([liked] null la quita). Catálogo con catálogo no llega aquí. */
    fun ratePairing(other: PairingItem, liked: Boolean?) {
        val subject = state.value.pairingSubject ?: return
        val repository = pairing ?: return
        if (ratableKey(subject, other) == null) return
        (writeScope ?: viewModelScope).launch { repository.rate(subject, other, liked) }
    }

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

    /** «Lo hice»: abre el panel con hoy (la fecha se puede cambiar, nunca a un día futuro). */
    fun startLog() {
        val recipe = state.value.recipe ?: return
        logEditor?.startNew(recipe.id, recipe.title)
    }

    override fun onCleared() {
        logEditor?.cancel()
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
        // Fuera del hilo principal: el resultado no depende de que éste se desocupe para reanudar la corrutina.
        viewModelScope.launch(Dispatchers.Default) {
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
                    diary = container.diaryRepository,
                    newLogEditor = container::newLogEditor,
                    clock = container.clock,
                    pairing = container.pairingRepository,
                    writeScope = container.applicationScope,
                )
            }
        }
    }
}
