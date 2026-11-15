package app.recetario.shopping

import androidx.room.withTransaction
import app.recetario.core.data.AppDatabase
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.scaleQuantity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

interface ShoppingRepository {
    /** Artículos por sección (en el orden del súper, sin las vacías); los marcados van al final de cada una. */
    fun observe(): Flow<Map<ShopSection, List<ShoppingItem>>>

    /** Añade los ingredientes de [recipe] escalados a [servings], salvo [excludeIngredientIds]; devuelve cuántos. */
    suspend fun addFromRecipe(recipe: Recipe, servings: Int, excludeIngredientIds: Set<String>): Int
    suspend fun addManual(name: String, quantity: Double?, unit: MeasureUnit)
    suspend fun toggle(id: String)

    /** Borra el artículo y devuelve su estado actual (leído en la misma transacción) para poder deshacerlo. */
    suspend fun delete(id: String): ShoppingItem?

    /** Cambia el artículo a otra sección del súper. */
    suspend fun setSection(id: String, section: ShopSection)

    /** Borra los marcados y los devuelve, para poder deshacerlo con [restore]. */
    suspend fun clearChecked(): List<ShoppingItem>

    /** Vuelve a poner artículos tal cual estaban (deshacer). */
    suspend fun restore(items: List<ShoppingItem>)
}

class RoomShoppingRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val cupMl: Flow<Int>,
) : ShoppingRepository {
    private val dao = db.shoppingDao()

    override fun observe(): Flow<Map<ShopSection, List<ShoppingItem>>> = dao.observeAll().map { rows ->
        val bySection = rows.map { it.toModel() }.groupBy { it.section }
        ShopSection.entries.mapNotNull { s ->
            bySection[s]?.let { list -> s to list.sortedBy { it.checked } }
        }.toMap()
    }

    override suspend fun addFromRecipe(recipe: Recipe, servings: Int, excludeIngredientIds: Set<String>): Int {
        val now = clock.instant()
        val incoming = recipe.ingredients.filter { it.id !in excludeIngredientIds }.map {
            ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = it.name.trim(),
                quantity = scaleQuantity(it.quantity, recipe.servings, servings),
                unit = it.unit,
                customUnit = it.customUnit,
                section = SectionClassifier.classify(it.name),
                checked = false,
                sourceRecipeId = recipe.id,
                createdAt = now,
            )
        }
        if (incoming.isNotEmpty()) merge(incoming)
        return incoming.size
    }

    override suspend fun addManual(name: String, quantity: Double?, unit: MeasureUnit) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        merge(
            listOf(
                ShoppingItem(
                    id = UUID.randomUUID().toString(), name = clean, quantity = quantity, unit = unit,
                    customUnit = null, section = SectionClassifier.classify(clean), checked = false,
                    sourceRecipeId = null, createdAt = clock.instant(),
                ),
            ),
        )
    }

    /** Funde en una sola transacción y guarda solo lo que cambia. */
    private suspend fun merge(incoming: List<ShoppingItem>) {
        val cup = cupMl.first()
        db.withTransaction {
            val existing = dao.getAll().map { it.toModel() }
            val before = existing.associateBy { it.id }
            val changed = mergeInto(existing, incoming, cup).filter { before[it.id] != it }
            dao.upsert(changed.map { it.toEntity() })
        }
    }

    override suspend fun toggle(id: String) = dao.toggle(id)

    override suspend fun delete(id: String): ShoppingItem? = db.withTransaction {
        val row = dao.get(id)?.toModel()
        dao.delete(id)
        row
    }

    override suspend fun setSection(id: String, section: ShopSection) = dao.setSection(id, section)

    override suspend fun clearChecked(): List<ShoppingItem> = db.withTransaction {
        val checked = dao.getAll().filter { it.checked }.map { it.toModel() }
        dao.deleteChecked()
        checked
    }

    override suspend fun restore(items: List<ShoppingItem>) = dao.upsert(items.map { it.toEntity() })
}
