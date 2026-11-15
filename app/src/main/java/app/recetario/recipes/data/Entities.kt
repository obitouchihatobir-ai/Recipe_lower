package app.recetario.recipes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import java.time.Instant

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val baseType: BaseType,
    val sortOrder: Int,
    val isDefault: Boolean,
)

@Entity(
    tableName = "recipes",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])],
    indices = [Index("categoryId")],
)
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryId: String,
    val coverPhoto: String?,
    val description: String,
    val totalMinutes: Int?,
    val servings: Int,
    val difficulty: Difficulty,
    val sweet: Int,
    val salty: Int,
    val sour: Int,
    val bitter: Int,
    val spicy: Int,
    val intensity: Intensity,
    val rich: Boolean,
    val serveTemp: ServeTemp?,
    val moments: Set<Moment>,
    val alcoholic: Boolean?,
    val course: Course?,
    val notes: String,
    val favorite: Boolean,
    val sourceUrl: String?,
    val aiGenerated: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val section: String?,
    val name: String,
    val quantity: Double?,
    val unit: MeasureUnit,
    val customUnit: String?,
    val note: String,
)

@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class StepEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val text: String,
    val timerSeconds: Int?,
)

@Entity(
    tableName = "step_photos",
    primaryKeys = ["stepId", "position"],
    foreignKeys = [ForeignKey(entity = StepEntity::class, parentColumns = ["id"], childColumns = ["stepId"], onDelete = ForeignKey.CASCADE)],
)
data class StepPhotoEntity(
    val stepId: String,
    val position: Int,
    val fileName: String,
)

@Entity(
    tableName = "step_ingredients",
    primaryKeys = ["stepId", "ingredientId"],
    foreignKeys = [
        ForeignKey(entity = StepEntity::class, parentColumns = ["id"], childColumns = ["stepId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IngredientEntity::class, parentColumns = ["id"], childColumns = ["ingredientId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("ingredientId")],
)
data class StepIngredientEntity(
    val stepId: String,
    val ingredientId: String,
)

/** [name] conserva la grafía de la primera aparición; [normalized] es la clave única. */
@Entity(tableName = "tags", indices = [Index("normalized", unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalized: String,
)

@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipeId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class RecipeTagEntity(
    val recipeId: String,
    val tagId: Long,
    val position: Int,
)
