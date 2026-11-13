package app.recetario.recipes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT name FROM tags ORDER BY normalized")
    fun observeNames(): Flow<List<String>>

    @Query("SELECT * FROM tags WHERE normalized = :normalized")
    suspend fun findByNormalized(normalized: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name, normalized = :normalized WHERE id = :id")
    suspend fun rename(id: Long, name: String, normalized: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(links: List<RecipeTagEntity>)

    @Query("SELECT * FROM recipe_tags WHERE tagId = :tagId")
    suspend fun linksOf(tagId: Long): List<RecipeTagEntity>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM recipe_tags)")
    suspend fun deleteOrphans()
}
