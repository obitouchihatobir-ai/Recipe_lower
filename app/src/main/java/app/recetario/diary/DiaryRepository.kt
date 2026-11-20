package app.recetario.diary

import androidx.room.withTransaction
import app.recetario.core.data.AppDatabase
import app.recetario.diary.data.CookLogEntity
import app.recetario.diary.data.CookLogPhotoEntity
import app.recetario.diary.data.CookLogWithPhotos
import app.recetario.recipes.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/** El diario de cocina (§7.1): lo que se cocinó, cuándo, con valoración, nota y hasta 4 fotos. */
interface DiaryRepository {
    /** Todas las entradas, la más reciente primero. */
    fun observeEntries(): Flow<List<CookLogEntry>>

    /** Estadísticas por receta (las entradas sin receta no cuentan). */
    fun observeStats(): Flow<Map<String, CookStats>>

    /**
     * Inserta o sustituye la entrada (con sus fotos); `updatedAt` se fija con el reloj. Si su receta ya no existe, se
     * guarda sin receta. Las fotos que la entrada deja de usar y nadie más usa se borran.
     */
    suspend fun save(entry: CookLogEntry)

    /** Borra la entrada y la devuelve (para «Deshacer»); sus archivos de foto NO se borran (ver [deletePhotosIfUnused]). */
    suspend fun delete(id: String): CookLogEntry?

    /** Vuelve a escribir una entrada borrada tal cual (con su `updatedAt`). */
    suspend fun restore(entry: CookLogEntry)

    /** Borra los archivos de foto que ya no usa nadie (ni recetas ni el diario). */
    suspend fun deletePhotosIfUnused(names: Collection<String>)
}

class RoomDiaryRepository(
    private val db: AppDatabase,
    private val photos: PhotoStore,
    private val clock: Clock,
) : DiaryRepository {
    private val dao = db.diaryDao()

    override fun observeEntries(): Flow<List<CookLogEntry>> =
        dao.observeWithPhotos().map { rows -> rows.map { it.toModel() } }

    override fun observeStats(): Flow<Map<String, CookStats>> = observeEntries().map(::statsByRecipe)

    override suspend fun save(entry: CookLogEntry) {
        val previous = write(entry.copy(updatedAt = clock.instant()))
        deletePhotosIfUnused(previous - entry.photos.toSet())
    }

    override suspend fun delete(id: String): CookLogEntry? = db.withTransaction {
        val existing = dao.getWithPhotos(id) ?: return@withTransaction null
        dao.delete(id) // las filas de fotos se van en cascada
        existing.toModel()
    }

    override suspend fun restore(entry: CookLogEntry) {
        write(entry)
    }

    override suspend fun deletePhotosIfUnused(names: Collection<String>) {
        if (names.isEmpty()) return
        val unused = names.toSet().filterNot { db.recipeDao().isPhotoReferenced(it) }
        withContext(Dispatchers.IO) { unused.forEach(photos::delete) }
    }

    /** Escribe la entrada y sus fotos en una transacción; devuelve las fotos que tenía antes. */
    private suspend fun write(entry: CookLogEntry): Set<String> = db.withTransaction {
        val previous = dao.getWithPhotos(entry.id)?.photos?.map { it.fileName }.orEmpty().toSet()
        val recipeId = entry.recipeId?.takeIf { dao.recipeExists(it) }
        dao.upsert(entry.toEntity(recipeId))
        dao.deletePhotos(entry.id)
        dao.upsertPhotos(entry.photos.take(MAX_PHOTOS).mapIndexed { i, name -> CookLogPhotoEntity(entry.id, i, name) })
        previous
    }

    companion object {
        const val MAX_PHOTOS = 4
    }
}

private fun CookLogEntry.toEntity(recipeId: String?) = CookLogEntity(
    id = id, recipeId = recipeId, recipeTitle = recipeTitle, date = date.toString(), rating = rating?.coerceIn(1, 5),
    note = note, createdAt = createdAt, updatedAt = updatedAt,
)

private fun CookLogWithPhotos.toModel() = CookLogEntry(
    id = entry.id,
    recipeId = entry.recipeId,
    recipeTitle = entry.recipeTitle,
    date = LocalDate.parse(entry.date),
    rating = entry.rating,
    note = entry.note,
    photos = photos.sortedBy { it.position }.map { it.fileName },
    createdAt = entry.createdAt,
    updatedAt = entry.updatedAt,
)
