package app.recetario.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.pantry.PantryRepository
import app.recetario.pantry.pantryCovers
import app.recetario.recipes.quantity.MeasureUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingState(
    /** False hasta que llegan los datos (así no parpadea el estado vacío). */
    val loaded: Boolean = false,
    val groups: List<Pair<ShopSection, List<ShoppingItem>>> = emptyList(),
    val checkedCount: Int = 0,
    /** Texto para compartir; vacío si no queda nada por comprar. */
    val shareText: String = "",
    /** Artículos sin marcar que ya están en la despensa («ya lo tienes»), con la coincidencia de «¿Qué puedo hacer?». */
    val inPantry: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

class ShoppingViewModel(
    private val repo: ShoppingRepository,
    private val pantry: PantryRepository? = null,
) : ViewModel() {
    val state: StateFlow<ShoppingState> = combine(repo.observe(), pantry?.observe() ?: flowOf(emptyList())) { map, pantryItems ->
        val items = map.values.flatten()
        ShoppingState(
            loaded = true,
            groups = map.entries.map { it.key to it.value },
            checkedCount = items.count { it.checked },
            shareText = if (items.any { !it.checked }) asShareText(items) else "",
            inPantry = items.filter { item -> !item.checked && pantryItems.any { pantryCovers(it.name, item.name) } }
                .map { it.id }.toSet(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())

    /** «A la despensa»: añade el nombre sin duplicarlo; false si ya estaba. */
    suspend fun addToPantry(name: String): Boolean = pantry?.add(name) ?: false

    fun toggle(id: String) {
        viewModelScope.launch { repo.toggle(id) }
    }

    /** Borra y entrega el artículo tal como estaba justo antes, para ofrecer «Deshacer». */
    suspend fun delete(id: String): ShoppingItem? = repo.delete(id)

    fun setSection(id: String, section: ShopSection) {
        viewModelScope.launch { repo.setSection(id, section) }
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
        fun factory(repo: ShoppingRepository, pantry: PantryRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { ShoppingViewModel(repo, pantry) } }
    }
}
