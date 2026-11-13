package app.recetario.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.recipes.quantity.MeasureUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingState(
    /** False hasta que llegan los datos (así no parpadea el estado vacío). */
    val loaded: Boolean = false,
    val groups: List<Pair<ShopSection, List<ShoppingItem>>> = emptyList(),
    val checkedCount: Int = 0,
    /** Texto para compartir; vacío si no queda nada por comprar. */
    val shareText: String = "",
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

class ShoppingViewModel(private val repo: ShoppingRepository) : ViewModel() {
    val state: StateFlow<ShoppingState> = repo.observe().map { map ->
        val items = map.values.flatten()
        ShoppingState(
            loaded = true,
            groups = map.entries.map { it.key to it.value },
            checkedCount = items.count { it.checked },
            shareText = if (items.any { !it.checked }) asShareText(items) else "",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())

    fun toggle(id: String) {
        viewModelScope.launch { repo.toggle(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun add(name: String, quantity: Double?, unit: MeasureUnit) {
        viewModelScope.launch { repo.addManual(name, quantity, unit) }
    }

    fun restore(items: List<ShoppingItem>) {
        viewModelScope.launch { repo.restore(items) }
    }

    /** Vacía los marcados y entrega lo borrado, para ofrecer «Deshacer». */
    suspend fun clearChecked(): List<ShoppingItem> = repo.clearChecked()

    companion object {
        fun factory(repo: ShoppingRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { ShoppingViewModel(repo) } }
    }
}
