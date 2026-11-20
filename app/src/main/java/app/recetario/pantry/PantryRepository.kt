package app.recetario.pantry

import androidx.room.withTransaction
import app.recetario.core.data.AppDatabase
import app.recetario.pantry.data.PantryItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

/** La despensa (§7.2): lo que hay en casa, sin repetir (tildes, mayúsculas y plural básico no cuentan). */
interface PantryRepository {
    /** Todos los artículos, en orden alfabético. */
    fun observe(): Flow<List<PantryItem>>

    /** Añade [name]; false si está vacío o si ya estaba (mismo nombre normalizado, también en plural). */
    suspend fun add(name: String): Boolean

    /** Borra el artículo y lo devuelve (para «Deshacer»). */
    suspend fun delete(id: String): PantryItem?

    /** Vuelve a poner un artículo borrado; si mientras tanto se añadió otro con el mismo nombre, no hace nada. */
    suspend fun restore(item: PantryItem)
}

class RoomPantryRepository(private val db: AppDatabase, private val clock: Clock) : PantryRepository {
    private val dao = db.pantryDao()

    override fun observe(): Flow<List<PantryItem>> = dao.observeAll().map { rows ->
        rows.map { it.toModel() }.sortedWith(compareBy<PantryItem> { pantryKey(it.name) }.thenBy { it.name }.thenBy { it.id })
    }

    override suspend fun add(name: String): Boolean {
        val clean = name.trim()
        val key = pantryKey(clean)
        if (key.isEmpty()) return false
        return insertUnlessPresent(PantryItem(UUID.randomUUID().toString(), clean, key, clock.instant()))
    }

    override suspend fun delete(id: String): PantryItem? = db.withTransaction {
        val row = dao.get(id) ?: return@withTransaction null
        dao.delete(id)
        row.toModel()
    }

    override suspend fun restore(item: PantryItem) {
        insertUnlessPresent(item)
    }

    /** Comprueba y escribe en la misma transacción; el índice único de `normalized` es la última red. */
    private suspend fun insertUnlessPresent(item: PantryItem): Boolean = db.withTransaction {
        val present = dao.getAll().any { it.normalized == item.normalized || samePantryName(it.name, item.name) }
        !present && dao.insertIfAbsent(item.toEntity()) != -1L
    }
}

private fun PantryItemEntity.toModel() = PantryItem(id = id, name = name, normalized = normalized, addedAt = addedAt)

private fun PantryItem.toEntity() = PantryItemEntity(id = id, name = name, normalized = normalized, addedAt = addedAt)
