package app.recetario.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.recetario.backup.BackupDao
import app.recetario.diary.data.CookLogEntity
import app.recetario.diary.data.CookLogPhotoEntity
import app.recetario.diary.data.DiaryDao
import app.recetario.pairing.data.PairingRatingDao
import app.recetario.pairing.data.PairingRatingEntity
import app.recetario.pantry.data.PantryDao
import app.recetario.pantry.data.PantryItemEntity
import app.recetario.plan.data.MealPlanDao
import app.recetario.plan.data.MealPlanEntity
import app.recetario.recipes.data.CategoryDao
import app.recetario.recipes.data.CategoryEntity
import app.recetario.recipes.data.IngredientEntity
import app.recetario.recipes.data.RecipeDao
import app.recetario.recipes.data.RecipeEntity
import app.recetario.recipes.data.RecipeTagEntity
import app.recetario.recipes.data.StepEntity
import app.recetario.recipes.data.StepIngredientEntity
import app.recetario.recipes.data.StepPhotoEntity
import app.recetario.recipes.data.TagDao
import app.recetario.recipes.data.TagEntity
import app.recetario.shopping.ShoppingDao
import app.recetario.shopping.ShoppingItemEntity

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        StepPhotoEntity::class,
        StepIngredientEntity::class,
        TagEntity::class,
        RecipeTagEntity::class,
        CategoryEntity::class,
        ShoppingItemEntity::class,
        CookLogEntity::class,
        CookLogPhotoEntity::class,
        PantryItemEntity::class,
        MealPlanEntity::class,
        PairingRatingEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun backupDao(): BackupDao
    abstract fun diaryDao(): DiaryDao
    abstract fun pantryDao(): PantryDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun pairingRatingDao(): PairingRatingDao

    companion object {
        /** Versión actual del esquema. Cada subida lleva su migración (`Migrations.kt`) y su prueba. */
        const val VERSION = 2

        const val FILE_NAME = "recetario.db"

        // Sin fallbackToDestructiveMigration: un cambio de esquema exige migración.
        fun create(context: Context, name: String = FILE_NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addCallback(DefaultCategories.callback)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
