package app.recetario.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.recetario.AppContainer
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.list.DEFAULT_EMOJI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** Una entrada en la cronología: la foto que se ve (la suya o la portada de la receta) y el emoji de reserva. */
data class DiaryRow(val entry: CookLogEntry, val photo: String?, val emoji: String) {
    /** Sin receta (se borró): se lee con el título guardado y no lleva a ningún detalle. */
    val orphan: Boolean get() = entry.recipeId == null
}

data class DiaryState(
    val loaded: Boolean = false,
    val months: List<Pair<YearMonth, List<DiaryRow>>> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
)

/**
 * La pantalla Diario: cronología por meses, editar una entrada y borrarla con «Deshacer». Las fotos de una entrada
 * borrada solo se borran cuando caduca «Deshacer» ([purge]) o al salir de la pantalla.
 */
class DiaryViewModel(
    private val repo: DiaryRepository,
    recipes: RecipeRepository,
    newLogEditor: (CoroutineScope) -> LogEntryEditor,
    private val clock: Clock,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val editor: LogEntryEditor = newLogEditor(viewModelScope)

    /** Entradas borradas cuyo «Deshacer» sigue a la vista: sus fotos aún no se tocan. */
    private val pendingPurge = LinkedHashMap<String, CookLogEntry>()

    val state: StateFlow<DiaryState> = combine(
        repo.observeEntries(), recipes.observeSummaries(), recipes.observeCategories(),
    ) { entries, summaries, categories ->
        val recipeById = summaries.associateBy { it.id }
        val emojiByCategory = categories.associate { it.id to it.emoji }
        DiaryState(
            loaded = true,
            months = groupByMonth(entries).map { (month, list) ->
                month to list.map { e ->
                    val recipe = e.recipeId?.let(recipeById::get)
                    DiaryRow(
                        entry = e,
                        photo = e.photos.firstOrNull() ?: recipe?.coverPhoto,
                        emoji = recipe?.let { emojiByCategory[it.categoryId] } ?: DEFAULT_EMOJI,
                    )
                }
            },
            today = LocalDate.now(clock),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryState(today = LocalDate.now(clock)))

    fun edit(entry: CookLogEntry) = editor.startEdit(entry)

    /** Borra la entrada que se está editando y la devuelve para «Deshacer» (null si ya no existía o falló). */
    suspend fun delete(id: String): CookLogEntry? {
        editor.cancel()
        val removed: CookLogEntry? = try {
            repo.delete(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (removed == null) return null
        synchronized(pendingPurge) { pendingPurge[removed.id] = removed }
        return removed
    }

    /** «Deshacer». */
    fun restore(entry: CookLogEntry) {
        synchronized(pendingPurge) { pendingPurge.remove(entry.id) }
        appScope.launch { runCatching { repo.restore(entry) } }
    }

    /** «Deshacer» caducó: las fotos que nadie más usa se borran. */
    fun purge(entry: CookLogEntry) {
        val pending = synchronized(pendingPurge) { pendingPurge.remove(entry.id) } ?: return
        appScope.launch { runCatching { repo.deletePhotosIfUnused(pending.photos) } }
    }

    override fun onCleared() {
        editor.cancel()
        val all = synchronized(pendingPurge) { pendingPurge.values.toList().also { pendingPurge.clear() } }
        if (all.isNotEmpty()) appScope.launch { runCatching { repo.deletePhotosIfUnused(all.flatMap { it.photos }) } }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DiaryViewModel(
                    repo = container.diaryRepository,
                    recipes = container.recipeRepository,
                    newLogEditor = container::newLogEditor,
                    clock = container.clock,
                    appScope = container.applicationScope,
                )
            }
        }
    }
}
