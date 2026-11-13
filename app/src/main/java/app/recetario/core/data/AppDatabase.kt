package app.recetario.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao

    companion object {
        // Sin fallbackToDestructiveMigration: un cambio de esquema exige migración.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "recetario.db")
                .addCallback(DefaultCategories.callback)
                .build()
    }
}
