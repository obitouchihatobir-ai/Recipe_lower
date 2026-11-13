package app.recetario.recipes.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class StepWithChildren(
    @Embedded val step: StepEntity,
    @Relation(parentColumn = "id", entityColumn = "stepId")
    val photos: List<StepPhotoEntity>,
    @Relation(parentColumn = "id", entityColumn = "stepId")
    val links: List<StepIngredientEntity>,
)

data class RecipeTagWithTag(
    @Embedded val link: RecipeTagEntity,
    @Relation(parentColumn = "tagId", entityColumn = "id")
    val tag: TagEntity,
)

data class RecipeWithChildren(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(entity = StepEntity::class, parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepWithChildren>,
    @Relation(entity = RecipeTagEntity::class, parentColumn = "id", entityColumn = "recipeId")
    val tags: List<RecipeTagWithTag>,
)

/** Lo justo para las tarjetas: receta, ingredientes y etiquetas (sin pasos). */
data class RecipeListRow(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(entity = RecipeTagEntity::class, parentColumn = "id", entityColumn = "recipeId")
    val tags: List<RecipeTagWithTag>,
)

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC, title")
    fun observeRows(): Flow<List<RecipeListRow>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeRecipe(id: String): Flow<RecipeWithChildren?>

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    // Borrar los ingredientes y los pasos arrastra, en cascada, fotos y enlaces de paso.
    @Query("DELETE FROM ingredients WHERE recipeId = :id")
    suspend fun deleteIngredients(id: String)

    @Query("DELETE FROM steps WHERE recipeId = :id")
    suspend fun deleteSteps(id: String)

    @Query("DELETE FROM recipe_tags WHERE recipeId = :id")
    suspend fun deleteRecipeTags(id: String)

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert
    suspend fun insertSteps(items: List<StepEntity>)

    @Insert
    suspend fun insertStepPhotos(items: List<StepPhotoEntity>)

    @Insert
    suspend fun insertStepIngredients(items: List<StepIngredientEntity>)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT coverPhoto FROM recipes WHERE id = :id")
    suspend fun coverPhoto(id: String): String?

    @Query("SELECT p.fileName FROM step_photos p JOIN steps s ON s.id = p.stepId WHERE s.recipeId = :id")
    suspend fun stepPhotos(id: String): List<String>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM recipes WHERE coverPhoto = :name) OR " +
            "EXISTS(SELECT 1 FROM step_photos WHERE fileName = :name)",
    )
    suspend fun isPhotoReferenced(name: String): Boolean

    @Query("UPDATE recipes SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE recipes SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: String, to: String)
}
