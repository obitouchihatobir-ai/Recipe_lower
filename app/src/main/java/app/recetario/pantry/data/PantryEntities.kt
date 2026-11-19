package app.recetario.pantry.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** [name] conserva lo que escribió el usuario; [normalized] es la clave única. */
@Entity(tableName = "pantry_items", indices = [Index("normalized", unique = true)])
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalized: String,
    val addedAt: Instant,
)

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items ORDER BY addedAt, rowid")
    fun observeAll(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items ORDER BY addedAt, rowid")
    suspend fun getAll(): List<PantryItemEntity>

    @Upsert
    suspend fun upsert(items: List<PantryItemEntity>)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pantry_items")
    suspend fun deleteAll()
}
