package app.recetario.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    @Query("SELECT id FROM categories")
    suspend fun categoryIds(): List<String>

    @Query("SELECT id FROM shopping_items")
    suspend fun shoppingIds(): List<String>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun recipeCount(): Int

    /** `updatedAt` en milisegundos, o null si la receta no existe. */
    @Query("SELECT updatedAt FROM recipes WHERE id = :id")
    suspend fun recipeUpdatedAt(id: String): Long?

    /** Fotos que usa alguna receta (portadas y pasos). */
    @Query("SELECT coverPhoto FROM recipes WHERE coverPhoto IS NOT NULL UNION SELECT fileName FROM step_photos")
    suspend fun photoNames(): List<String>

    @Insert
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert
    suspend fun insertShoppingItems(items: List<ShoppingItemEntity>)

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

    /** Las de serie ([keep]) no se borran nunca: la copia las actualiza (si las trae) con su nombre de entonces. */
    @Query("DELETE FROM categories WHERE id NOT IN (:keep)")
    suspend fun clearCategoriesExcept(keep: List<String>)

    @Upsert
    suspend fun upsertCategories(items: List<CategoryEntity>)
}
