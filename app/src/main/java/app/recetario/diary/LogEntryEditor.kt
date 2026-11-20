package app.recetario.diary

import app.recetario.recipes.data.PhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import java.util.UUID

/** Lo que se está escribiendo en el panel (o en la tarjeta de «¡Listo!») antes de guardarlo en el diario. */
data class LogDraft(
    /** Null si es una entrada nueva. */
    val entryId: String?,
    val recipeId: String?,
    val recipeTitle: String,
    val date: LocalDate,
    val rating: Int? = null,
    val note: String = "",
    val photos: List<String> = emptyList(),
    /** Null si es nueva (se fija al guardar). */
    val createdAt: Instant? = null,
    /** Aviso si no se pudo guardar o no se pudo leer una foto. */
    val error: String? = null,
) {
    val canAddPhoto: Boolean get() = photos.size < RoomDiaryRepository.MAX_PHOTOS
}

/**
 * Edita una entrada del diario: la crea en blanco ([startNew]) o parte de una que ya existe ([startEdit]). La usan el
 * modo cocina, el detalle y la pantalla Diario. Las fotos se guardan en el [PhotoStore] al elegirlas; las que se
 * importaron y no acaban guardadas se borran. Guardar sigue en [appScope] aunque la pantalla se cierre.
 */
class LogEntryEditor(
    private val repo: DiaryRepository,
    private val photoStore: PhotoStore,
    private val clock: Clock,
    /** Ámbito de la pantalla (importar fotos). */
    private val scope: CoroutineScope,
    /** Ámbito de la app: guardar y borrar fotos no debe cortarse al salir de la pantalla. */
    private val appScope: CoroutineScope,
) {
    private val _draft = MutableStateFlow<LogDraft?>(null)
    val draft: StateFlow<LogDraft?> = _draft.asStateFlow()

    /** Fotos importadas mientras se edita; si no se guardan, sobran. */
    private val imported: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

    fun today(): LocalDate = LocalDate.now(clock)

    fun startNew(recipeId: String?, recipeTitle: String, date: LocalDate = today()) {
        discardImported()
        _draft.value = LogDraft(entryId = null, recipeId = recipeId, recipeTitle = recipeTitle, date = minOf(date, today()))
    }

    fun startEdit(entry: CookLogEntry) {
        discardImported()
        _draft.value = LogDraft(
            entryId = entry.id, recipeId = entry.recipeId, recipeTitle = entry.recipeTitle, date = entry.date,
            rating = entry.rating, note = entry.note, photos = entry.photos, createdAt = entry.createdAt,
        )
    }

    /** Tocar la estrella ya elegida quita la valoración (es opcional). */
    fun rate(stars: Int) = _draft.update { d -> d?.copy(rating = if (d.rating == stars) null else stars.coerceIn(1, 5)) }

    fun setNote(note: String) = _draft.update { it?.copy(note = note) }

    /** Mueve la fecha [days] días; nunca pasa de hoy. */
    fun shiftDate(days: Long) = _draft.update { d -> d?.copy(date = minOf(d.date.plusDays(days), today())) }

    fun setDate(date: LocalDate) = _draft.update { it?.copy(date = minOf(date, today())) }

    fun removePhoto(name: String) {
        _draft.update { it?.copy(photos = it.photos - name) }
        // Una foto recién importada no la usa nadie más: fuera. Las que ya estaban se borran al guardar.
        if (imported.remove(name)) deleteLater(listOf(name))
    }

    /** Guarda la foto elegida en el [PhotoStore] y la añade (si sigue abierto y caben más). */
    fun importPhoto(source: InputStream) {
        scope.launch {
            val name = try {
                photoStore.import(source)
            } catch (_: IOException) {
                _draft.update { it?.copy(error = PHOTO_FAILED) }
                return@launch
            } catch (_: SecurityException) {
                // La URI dejó de tener permiso (p. ej., revocado a mitad de lectura): aviso, sin cerrarse.
                _draft.update { it?.copy(error = PHOTO_FAILED) }
                return@launch
            }
            val current = _draft.value
            if (current == null || !current.canAddPhoto) {
                deleteLater(listOf(name))
            } else {
                imported += name
                _draft.value = current.copy(photos = current.photos + name, error = null)
            }
        }
    }

    fun showError(message: String) = _draft.update { it?.copy(error = message) }

    /**
     * Cierra el borrador y lo guarda en [appScope]. [onSaved] se llama al terminar (en otro hilo); si falla, el
     * borrador vuelve a abrirse con el aviso.
     */
    fun save(onSaved: (CookLogEntry) -> Unit = {}) {
        val d = _draft.value ?: return
        val now = clock.instant()
        val entry = CookLogEntry(
            id = d.entryId ?: UUID.randomUUID().toString(),
            recipeId = d.recipeId,
            recipeTitle = d.recipeTitle,
            date = minOf(d.date, today()),
            rating = d.rating,
            note = d.note.trim(),
            photos = d.photos,
            createdAt = d.createdAt ?: now,
            updatedAt = now,
        )
        val leftovers = synchronized(imported) { imported - d.photos.toSet() }
        imported.clear()
        _draft.value = null
        appScope.launch {
            try {
                repo.save(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                imported += d.photos
                _draft.value = d.copy(entryId = entry.id, createdAt = entry.createdAt, error = SAVE_FAILED)
                return@launch
            }
            safely { repo.deletePhotosIfUnused(leftovers) }
            onSaved(entry)
        }
    }

    /** Cierra sin guardar; las fotos importadas se borran. */
    fun cancel() {
        _draft.value = null
        discardImported()
    }

    private fun discardImported() {
        val names = synchronized(imported) { imported.toList().also { imported.clear() } }
        if (names.isNotEmpty()) deleteLater(names)
    }

    private fun deleteLater(names: List<String>) {
        appScope.launch { safely { repo.deletePhotosIfUnused(names) } }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Una foto que sobra en disco no es grave; se intentará la próxima vez que se toque.
        }
    }

    companion object {
        const val SAVE_FAILED = "No se pudo guardar en el diario."
        const val PHOTO_FAILED = "No se pudo leer la foto."
    }
}
