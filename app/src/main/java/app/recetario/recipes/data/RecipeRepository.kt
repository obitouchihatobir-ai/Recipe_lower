package app.recetario.recipes.data

import androidx.room.withTransaction
import app.recetario.core.data.AppDatabase
import app.recetario.core.text.normalize
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.model.Step
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

interface RecipeRepository {
    fun observeSummaries(): Flow<List<RecipeSummary>>
    fun observeRecipe(id: String): Flow<Recipe?>

    /** Inserta o actualiza el agregado completo en una transacción; `updatedAt` se fija con el reloj. */
    suspend fun save(recipe: Recipe)

    /** Borra la receta, sus datos hijos y los archivos de foto. */
    suspend fun delete(id: String)
    suspend fun setFavorite(id: String, favorite: Boolean)

    fun observeCategories(): Flow<List<Category>>
    suspend fun saveCategory(category: Category)

    /** Las recetas de la categoría pasan a [reassignTo]. Lanza [IllegalArgumentException] si es de serie. */
    suspend fun deleteCategory(id: String, reassignTo: String)

    fun observeTags(): Flow<List<String>>
    suspend fun renameTag(old: String, new: String)
    suspend fun deleteTag(name: String)
}

class RoomRecipeRepository(
    private val db: AppDatabase,
    private val photos: PhotoStore,
    private val clock: Clock,
) : RecipeRepository {
    private val recipes = db.recipeDao()
    private val categories = db.categoryDao()
    private val tags = db.tagDao()

    override fun observeSummaries(): Flow<List<RecipeSummary>> =
        recipes.observeRows().map { rows -> rows.map { it.toSummary() } }

    override fun observeRecipe(id: String): Flow<Recipe?> =
        recipes.observeRecipe(id).map { it?.toRecipe() }

    override suspend fun save(recipe: Recipe) {
        db.withTransaction {
            recipes.upsert(recipe.toEntity(updatedAt = clock.instant()))
            recipes.deleteIngredients(recipe.id)
            recipes.deleteSteps(recipe.id)
            recipes.deleteRecipeTags(recipe.id)

            recipes.insertIngredients(recipe.ingredients.mapIndexed { i, it -> it.toEntity(recipe.id, i) })
            val ingredientIds = recipe.ingredients.mapTo(HashSet()) { it.id }
            recipes.insertSteps(recipe.steps.mapIndexed { i, it -> it.toEntity(recipe.id, i) })
            recipes.insertStepPhotos(
                recipe.steps.flatMap { s -> s.photos.mapIndexed { i, f -> StepPhotoEntity(s.id, i, f) } },
            )
            recipes.insertStepIngredients(
                recipe.steps.flatMap { s ->
                    s.ingredientIds.filter { it in ingredientIds }.map { StepIngredientEntity(s.id, it) }
                },
            )

            val links = LinkedHashMap<Long, RecipeTagEntity>()
            for (name in recipe.tags) {
                val display = name.trim()
                val key = normalize(display)
                if (key.isEmpty()) continue
                val tag = tags.findByNormalized(key)
                    ?: TagEntity(name = display, normalized = key).let { it.copy(id = tags.insert(it)) }
                links.getOrPut(tag.id) { RecipeTagEntity(recipe.id, tag.id, links.size) }
            }
            tags.linkAll(links.values.toList())
            tags.deleteOrphans()
        }
    }

    override suspend fun delete(id: String) {
        val files = ArrayList<String>()
        db.withTransaction {
            recipes.coverPhoto(id)?.let(files::add)
            files += recipes.stepPhotos(id)
            recipes.delete(id)
            tags.deleteOrphans()
        }
        files.forEach(photos::delete)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) = recipes.setFavorite(id, favorite)

    override fun observeCategories(): Flow<List<Category>> =
        categories.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun saveCategory(category: Category) = categories.upsert(category.toEntity())

    override suspend fun deleteCategory(id: String, reassignTo: String) {
        db.withTransaction {
            val category = categories.get(id) ?: return@withTransaction
            require(!category.isDefault) { "Las categorías de serie no se pueden borrar" }
            recipes.reassignCategory(id, reassignTo)
            categories.delete(id)
        }
    }

    override fun observeTags(): Flow<List<String>> = tags.observeNames()

    override suspend fun renameTag(old: String, new: String) {
        val display = new.trim()
        val newKey = normalize(display)
        if (newKey.isEmpty()) return
        db.withTransaction {
            val source = tags.findByNormalized(normalize(old)) ?: return@withTransaction
            val target = tags.findByNormalized(newKey)
            if (target == null || target.id == source.id) {
                tags.rename(source.id, display, newKey)
            } else {
                // Ya existe otra etiqueta con ese nombre: se fusionan y se conserva la existente.
                tags.linkAll(tags.linksOf(source.id).map { it.copy(tagId = target.id) })
                tags.delete(source.id)
            }
        }
    }

    override suspend fun deleteTag(name: String) {
        tags.findByNormalized(normalize(name))?.let { tags.delete(it.id) }
    }
}

private fun Recipe.toEntity(updatedAt: java.time.Instant) = RecipeEntity(
    id = id, title = title, categoryId = categoryId, coverPhoto = coverPhoto, description = description,
    totalMinutes = totalMinutes, servings = servings, difficulty = difficulty,
    sweet = flavor.sweet, salty = flavor.salty, sour = flavor.sour, bitter = flavor.bitter, spicy = flavor.spicy,
    intensity = flavor.intensity, rich = flavor.rich, serveTemp = serveTemp, moments = moments,
    alcoholic = alcoholic, course = course, notes = notes, favorite = favorite, sourceUrl = sourceUrl,
    aiGenerated = aiGenerated, createdAt = createdAt, updatedAt = updatedAt,
)

private fun Ingredient.toEntity(recipeId: String, position: Int) = IngredientEntity(
    id = id, recipeId = recipeId, position = position, section = section, name = name,
    quantity = quantity, unit = unit, customUnit = customUnit, note = note,
)

private fun Step.toEntity(recipeId: String, position: Int) =
    StepEntity(id = id, recipeId = recipeId, position = position, text = text, timerSeconds = timerSeconds)

private fun IngredientEntity.toModel() = Ingredient(id, section, name, quantity, unit, customUnit, note)

private fun RecipeEntity.flavor() = FlavorProfile(sweet, salty, sour, bitter, spicy, intensity, rich)

private fun List<RecipeTagWithTag>.names() = sortedBy { it.link.position }.map { it.tag.name }

private fun RecipeWithChildren.toRecipe() = Recipe(
    id = recipe.id, title = recipe.title, categoryId = recipe.categoryId, coverPhoto = recipe.coverPhoto,
    description = recipe.description, totalMinutes = recipe.totalMinutes, servings = recipe.servings,
    difficulty = recipe.difficulty, flavor = recipe.flavor(), serveTemp = recipe.serveTemp,
    moments = recipe.moments, alcoholic = recipe.alcoholic, course = recipe.course,
    ingredients = ingredients.sortedBy { it.position }.map { it.toModel() },
    steps = steps.sortedBy { it.step.position }.map { s ->
        Step(
            id = s.step.id,
            text = s.step.text,
            photos = s.photos.sortedBy { it.position }.map { it.fileName },
            timerSeconds = s.step.timerSeconds,
            ingredientIds = s.links.mapTo(LinkedHashSet()) { it.ingredientId },
        )
    },
    tags = tags.names(), notes = recipe.notes, favorite = recipe.favorite, sourceUrl = recipe.sourceUrl,
    aiGenerated = recipe.aiGenerated, createdAt = recipe.createdAt, updatedAt = recipe.updatedAt,
)

private fun RecipeListRow.toSummary() = RecipeSummary(
    id = recipe.id, title = recipe.title, categoryId = recipe.categoryId, coverPhoto = recipe.coverPhoto,
    totalMinutes = recipe.totalMinutes, flavor = recipe.flavor(), serveTemp = recipe.serveTemp,
    moments = recipe.moments, alcoholic = recipe.alcoholic, difficulty = recipe.difficulty,
    favorite = recipe.favorite, tags = tags.names(),
    ingredientNames = ingredients.sortedBy { it.position }.map { it.name },
    updatedAt = recipe.updatedAt,
)

private fun CategoryEntity.toModel() = Category(id, name, emoji, colorHex, baseType, sortOrder, isDefault)

private fun Category.toEntity() = CategoryEntity(id, name, emoji, colorHex, baseType, sortOrder, isDefault)
