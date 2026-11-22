package app.recetario.plan.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    /** Los platos entre [from] y [to] (fechas ISO, ambas incluidas). */
    @Query("SELECT * FROM meal_plan WHERE date BETWEEN :from AND :to ORDER BY date, slot")
    fun observeRange(from: String, to: String): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plan WHERE date = :date AND slot = :slot")
    suspend fun getSlot(date: String, slot: String): MealPlanEntity?

    /** Cambia la receta y las porciones de un hueco ya ocupado (mismo id). */
    @Query("UPDATE meal_plan SET recipeId = :recipeId, servings = :servings WHERE id = :id")
    suspend fun updateSlot(id: String, recipeId: String, servings: Int)

    /** Inserta si el hueco `(date, slot)` (y el id) están libres; -1 si no. Nunca pisa otro plato. */
    /** Suma [delta] a las porciones del hueco, entre [min] y [max], en la base de datos (dos toques seguidos suman dos). */
    @Query("UPDATE meal_plan SET servings = MAX(:min, MIN(:max, servings + :delta)) WHERE date = :date AND slot = :slot")
    suspend fun addServings(date: String, slot: String, delta: Int, min: Int, max: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfFree(item: MealPlanEntity): Long

    @Query("DELETE FROM meal_plan WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("SELECT EXISTS(SELECT 1 FROM recipes WHERE id = :recipeId)")
    suspend fun recipeExists(recipeId: String): Boolean
}
