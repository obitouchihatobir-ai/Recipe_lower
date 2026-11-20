package app.recetario.diary.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import app.recetario.recipes.data.RecipeEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Una entrada del diario. [recipeId] pasa a null si se borra la receta: la entrada sigue,
 * con [recipeTitle] copiado. [date] es la fecha local en ISO (`AAAA-MM-DD`).
 */
@Entity(
    tableName = "cook_log",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("recipeId", "date")],
)
data class CookLogEntity(
    @PrimaryKey val id: String,
    val recipeId: String?,
    val recipeTitle: String,
    val date: String,
    val rating: Int?,
    val note: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Hasta 4 fotos por entrada ([position] de 0 a 3); se borran en cascada con ella. */
@Entity(
    tableName = "cook_log_photos",
    primaryKeys = ["logId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CookLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["logId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CookLogPhotoEntity(
    val logId: String,
    val position: Int,
    val fileName: String,
)

/** Una entrada con sus fotos (sin ordenar: se ordenan por `position`). */
data class CookLogWithPhotos(
    @Embedded val entry: CookLogEntity,
    @Relation(parentColumn = "id", entityColumn = "logId")
    val photos: List<CookLogPhotoEntity>,
)

@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM cook_log ORDER BY date DESC, createdAt DESC")
    fun observeWithPhotos(): Flow<List<CookLogWithPhotos>>

    @Transaction
    @Query("SELECT * FROM cook_log WHERE id = :id")
    suspend fun getWithPhotos(id: String): CookLogWithPhotos?

    @Query("SELECT EXISTS(SELECT 1 FROM recipes WHERE id = :recipeId)")
    suspend fun recipeExists(recipeId: String): Boolean

    @Query("SELECT * FROM cook_log ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<CookLogEntity>>

    @Query("SELECT * FROM cook_log_photos ORDER BY logId, position")
    fun observeAllPhotos(): Flow<List<CookLogPhotoEntity>>

    @Query("SELECT * FROM cook_log ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<CookLogEntity>

    @Query("SELECT * FROM cook_log_photos ORDER BY logId, position")
    suspend fun getAllPhotos(): List<CookLogPhotoEntity>

    @Upsert
    suspend fun upsert(entry: CookLogEntity)

    @Upsert
    suspend fun upsertPhotos(photos: List<CookLogPhotoEntity>)

    @Query("DELETE FROM cook_log_photos WHERE logId = :logId")
    suspend fun deletePhotos(logId: String)

    @Query("DELETE FROM cook_log WHERE id = :id")
    suspend fun delete(id: String)

    /** Las fotos se van en cascada. */
    @Query("DELETE FROM cook_log")
    suspend fun deleteAll()
}
