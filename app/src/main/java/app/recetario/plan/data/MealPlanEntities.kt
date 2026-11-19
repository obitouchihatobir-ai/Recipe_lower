package app.recetario.plan.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.recetario.recipes.data.RecipeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Un hueco del menú semanal: [date] es la fecha local en ISO y [slot] `DESAYUNO`, `COMIDA` o `CENA`.
 * Uno por (fecha, hueco). Si se borra la receta, se borran sus huecos.
 */
@Entity(
    tableName = "meal_plan",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("date", "slot", unique = true),
        Index("date"),
        Index("recipeId"),
    ],
)
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val date: String,
    val slot: String,
    val recipeId: String,
    val servings: Int,
)

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plan ORDER BY date, slot")
    fun observeAll(): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plan ORDER BY date, slot")
    suspend fun getAll(): List<MealPlanEntity>

    @Upsert
    suspend fun upsert(items: List<MealPlanEntity>)

    @Query("DELETE FROM meal_plan WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM meal_plan")
    suspend fun deleteAll()
}
