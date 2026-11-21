package app.recetario.pairing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.AppContainer
import app.recetario.core.text.normalize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Cuántas parejas se muestran en la pestaña para el elemento elegido. */
const val PAIRING_TAB_LIMIT = 30

/** Cuántos resultados da el buscador. */
private const val SEARCH_LIMIT = 20

data class PairingUiState(
    val loaded: Boolean = false,
    val query: String = "",
    /** Lo que coincide con [query]: recetas propias (primero) y bebidas del catálogo. */
    val matches: List<PairingItem> = emptyList(),
    /** El elemento elegido (null: ninguno). */
    val subject: PairingItem? = null,
    /** «Tus recetas»: parejas de recetas propias para [subject]. */
    val own: List<Suggestion> = emptyList(),
    /** «Ideas»: parejas del catálogo para [subject]. */
    val ideas: List<Suggestion> = emptyList(),
)

/** «kind:id» de un elemento (así se guarda el elegido y se pasa en la ruta). */
fun PairingItem.sideKey(): String = "$kind:$id"

/** Lo que busca «¿Con qué combina…?»: recetas propias de cualquier tipo y bebidas del catálogo. */
fun pairingMatches(query: String, data: PairingData, limit: Int = SEARCH_LIMIT): List<PairingItem> {
    val q = normalize(query).trim()
    if (q.isEmpty()) return emptyList()
    val own = data.recipes.filter { normalize(it.name).contains(q) }.sortedBy { normalize(it.name) }
    val drinks = data.catalog.filter { it.role == PairRole.DRINK && normalize(it.name).contains(q) }.sortedBy { normalize(it.name) }
    return (own + drinks).take(limit)
}

/**
 * Pestaña Maridaje: el buscador y el elemento elegido se guardan en el [handle] (sobreviven al giro). Al abrirla desde
 * «Ver más» del detalle, la ruta trae `subject` («RECIPE:id») y la receta sale ya elegida.
 */
class PairingViewModel(
    private val repository: PairingRepository,
    private val handle: SavedStateHandle,
    /** Donde se guardan las valoraciones: no se cortan al salir de la pantalla. */
    private val writeScope: CoroutineScope? = null,
) : ViewModel() {

    init {
        // La receta de «Ver más» solo se elige una vez (después manda lo que elija el usuario).
        val fromRoute = handle.get<String>(SUBJECT_ARG)
        if (fromRoute != null && handle.get<Boolean>(ROUTE_USED_KEY) != true) {
            handle[SELECTED_KEY] = fromRoute
            handle[QUERY_KEY] = ""
            handle[ROUTE_USED_KEY] = true
        }
    }

    private val query = handle.getStateFlow(QUERY_KEY, "")
    private val selected = handle.getStateFlow<String?>(SELECTED_KEY, null)

    val state: StateFlow<PairingUiState> = combine(repository.observeData(), query, selected) { data, q, sel ->
        val subject = sel?.let { key -> key.split(":", limit = 2).takeIf { it.size == 2 }?.let { data.find(it[0], it[1]) } }
        val suggestions = subject?.let {
            suggestPairings(it, data.all, data.ratings, PAIRING_TAB_LIMIT, data.weights)
        }.orEmpty()
        PairingUiState(
            loaded = true,
            query = q,
            matches = pairingMatches(q, data),
            subject = subject,
            own = suggestions.filter { it.item.kind == "RECIPE" },
            ideas = suggestions.filter { it.item.kind != "RECIPE" },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PairingUiState())

    fun setQuery(text: String) {
        handle[QUERY_KEY] = text
    }

    fun choose(item: PairingItem) {
        handle[SELECTED_KEY] = item.sideKey()
        handle[QUERY_KEY] = ""
    }

    fun clearSubject() {
        handle[SELECTED_KEY] = null
    }

    fun rate(other: PairingItem, liked: Boolean?) {
        val subject = state.value.subject ?: return
        if (ratableKey(subject, other) == null) return
        (writeScope ?: viewModelScope).launch { repository.rate(subject, other, liked) }
    }

    companion object {
        /** Argumento de la ruta `Pairing(subject)`. */
        const val SUBJECT_ARG = "subject"
        private const val SELECTED_KEY = "pairing-selected"
        private const val QUERY_KEY = "pairing-query"
        private const val ROUTE_USED_KEY = "pairing-route-used"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PairingViewModel(container.pairingRepository, createSavedStateHandle(), container.applicationScope)
            }
        }
    }
}
