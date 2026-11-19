package app.recetario.pairing.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Valoración de una pareja sin orden: los lados van ordenados (`kind:id` menor primero) y
 * [pairKey] es `"<a>|<b>"`. Cada lado es `RECIPE` o `CATALOG`. Sin clave foránea: al borrar
 * una receta, su repositorio borra las valoraciones en las que aparece.
 */
@Entity(tableName = "pairing_ratings", indices = [Index("aId"), Index("bId")])
data class PairingRatingEntity(
    @PrimaryKey val pairKey: String,
    val aKind: String,
    val aId: String,
    val bKind: String,
    val bId: String,
    val liked: Boolean,
    val ratedAt: Instant,
)

@Dao
interface PairingRatingDao {
    @Query("SELECT * FROM pairing_ratings ORDER BY ratedAt, rowid")
    fun observeAll(): Flow<List<PairingRatingEntity>>

    @Query("SELECT * FROM pairing_ratings ORDER BY ratedAt, rowid")
    suspend fun getAll(): List<PairingRatingEntity>

    @Upsert
    suspend fun upsert(ratings: List<PairingRatingEntity>)

    @Query("DELETE FROM pairing_ratings WHERE pairKey = :pairKey")
    suspend fun delete(pairKey: String)

    @Query("DELETE FROM pairing_ratings")
    suspend fun deleteAll()
}
