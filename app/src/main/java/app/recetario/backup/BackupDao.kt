package app.recetario.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.recetario.diary.data.CookLogEntity
import app.recetario.diary.data.CookLogPhotoEntity
import app.recetario.pairing.data.PairingRatingEntity
import app.recetario.pantry.data.PantryItemEntity
import app.recetario.plan.data.MealPlanEntity
import app.recetario.recipes.data.CategoryEntity
import app.recetario.recipes.data.RecipeWithChildren
import app.recetario.shopping.ShoppingItemEntity

/** Consultas de las copias de seguridad: leer todo para exportar y escribir o vaciar tablas al importar. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun categories(): List<CategoryEntity>

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY createdAt, id")
    suspend fun recipes(): List<RecipeWithChildren>

    @Query("SELECT * FROM shopping_items ORDER BY createdAt, rowid")
    suspend fun shoppingItems(): List<ShoppingItemEntity>

    @Query("SELECT * FROM cook_log ORDER BY date, createdAt, id")
    suspend fun cookLog(): List<CookLogEntity>

    @Query("SELECT * FROM cook_log_photos ORDER BY logId, position")
    suspend fun cookLogPhotos(): List<CookLogPhotoEntity>

    @Query("SELECT * FROM pantry_items ORDER BY addedAt, rowid")
    suspend fun pantryItems(): List<PantryItemEntity>

    @Query("SELECT * FROM meal_plan ORDER BY date, slot")
    suspend fun mealPlan(): List<MealPlanEntity>

    @Query("SELECT * FROM pairing_ratings ORDER BY ratedAt, rowid")
    suspend fun pairingRatings(): List<PairingRatingEntity>

    @Query("SELECT COUNT(*) FROM cook_log")
    suspend fun cookLogCount(): Int

    /** `updatedAt` en milisegundos, o null si la entrada del diario no existe. */
    @Query("SELECT updatedAt FROM cook_log WHERE id = :id")
    suspend fun cookLogUpdatedAt(id: String): Long?

    /** `ratedAt` en milisegundos, o null si la pareja no está valorada. */
    @Query("SELECT ratedAt FROM pairing_ratings WHERE pairKey = :pairKey")
    suspend fun ratingRatedAt(pairKey: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM recipes WHERE id = :id)")
    suspend fun recipeExists(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM meal_plan WHERE date = :date AND slot = :slot)")
    suspend fun mealSlotTaken(date: String, slot: String): Boolean

    @Query("SELECT id FROM categories")
    suspend fun categoryIds(): List<String>

    @Query("SELECT id FROM shopping_items")
    suspend fun shoppingIds(): List<String>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun recipeCount(): Int

    /** `updatedAt` en milisegundos, o null si la receta no existe. */
    @Query("SELECT updatedAt FROM recipes WHERE id = :id")
    suspend fun recipeUpdatedAt(id: String): Long?

    /** Fotos en uso: portadas, fotos de paso y fotos del diario. */
    @Query(
        "SELECT coverPhoto FROM recipes WHERE coverPhoto IS NOT NULL UNION SELECT fileName FROM step_photos " +
            "UNION SELECT fileName FROM cook_log_photos",
    )
    suspend fun photoNames(): List<String>

    @Insert
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert
    suspend fun insertShoppingItems(items: List<ShoppingItemEntity>)

    /** Inserta o sustituye la entrada (solo tiene la clave `id`: aquí `@Upsert` no tiene trampa). */
    @Upsert
    suspend fun upsertCookLog(entry: CookLogEntity)

    @Query("DELETE FROM cook_log_photos WHERE logId = :logId")
    suspend fun deleteCookLogPhotos(logId: String)

    @Insert
    suspend fun insertCookLogPhotos(photos: List<CookLogPhotoEntity>)

    /**
     * Inserta si no choca ni el id ni el hueco `(date, slot)`; -1 si no inserta. (`@Upsert` con un id nuevo y un hueco
     * ocupado no hace nada en silencio.)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMealIfAbsent(item: MealPlanEntity): Long

    /** `pairKey` es la única clave de la tabla: aquí `@Upsert` no tiene trampa. */
    @Upsert
    suspend fun upsertPairingRatings(items: List<PairingRatingEntity>)

    // «Reemplazar todo»: de las tablas hijas a las madres, sin depender de la cascada.
    @Query("DELETE FROM step_ingredients")
    suspend fun clearStepIngredients()

    @Query("DELETE FROM step_photos")
    suspend fun clearStepPhotos()

    @Query("DELETE FROM recipe_tags")
    suspend fun clearRecipeTags()

    @Query("DELETE FROM steps")
    suspend fun clearSteps()

    @Query("DELETE FROM ingredients")
    suspend fun clearIngredients()

    @Query("DELETE FROM recipes")
    suspend fun clearRecipes()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Query("DELETE FROM shopping_items")
    suspend fun clearShoppingItems()

    @Query("DELETE FROM cook_log_photos")
    suspend fun clearCookLogPhotos()

    @Query("DELETE FROM cook_log")
    suspend fun clearCookLog()

    @Query("DELETE FROM meal_plan")
    suspend fun clearMealPlan()

    @Query("DELETE FROM pantry_items")
    suspend fun clearPantry()

    @Query("DELETE FROM pairing_ratings")
    suspend fun clearPairingRatings()

    /** Las de serie ([keep]) no se borran nunca: la copia las actualiza (si las trae) con su nombre de entonces. */
    @Query("DELETE FROM categories WHERE id NOT IN (:keep)")
    suspend fun clearCategoriesExcept(keep: List<String>)

    @Upsert
    suspend fun upsertCategories(items: List<CategoryEntity>)
}
