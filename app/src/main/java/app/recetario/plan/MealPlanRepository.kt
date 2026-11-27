package app.recetario.plan

import androidx.room.withTransaction
import app.recetario.core.data.AppDatabase
import app.recetario.plan.data.MealPlanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * El menú de la semana (diseño de la etapa 2, §7.6), en `meal_plan`: un plato por `(fecha, hueco)`. Solo escribe los
 * huecos de [MealSlot] (sus `key` son los `MEAL_SLOTS` de las copias). Nunca usa `@Upsert`: con un id nuevo y el hueco
 * ocupado, la clave única `(date, slot)` lo haría fallar en silencio. Si se borra la receta, sus huecos se borran con
 * ella (cascada de la clave ajena).
 */
interface MealPlanRepository {
    /** Los platos de la semana que empieza el lunes [monday]. */
    fun observeWeek(monday: LocalDate): Flow<List<PlannedMeal>>

    /** Pone [recipeId] en el hueco; si ya estaba ocupado, lo sustituye conservando su id. Sin receta, no hace nada. */
    suspend fun set(date: LocalDate, slot: MealSlot, recipeId: String, servings: Int)

    /** Suma [delta] porciones al plato del hueco (entre 1 y 99); sin plato, no hace nada. */
    suspend fun changeServings(date: LocalDate, slot: MealSlot, delta: Int)

    /** Vacía el hueco y devuelve el plato que había (para «Deshacer»). */
    suspend fun clear(date: LocalDate, slot: MealSlot): PlannedMeal?

    /**
     * Añade los platos cuyo hueco sigue libre y cuya receta existe (nunca pisa nada) y devuelve los que añadió: son los
     * que «Deshacer» debe quitar.
     */
    suspend fun addAll(meals: List<PlannedMeal>): List<PlannedMeal>

    /**
     * Deshace «Rellenar con sugerencias»: quita esos platos, pero solo los que siguen con la receta que se añadió (un
     * hueco cambiado a mano desde entonces se queda con lo elegido).
     */
    suspend fun removeAdded(meals: Collection<PlannedMeal>)
}

class RoomMealPlanRepository(private val db: AppDatabase) : MealPlanRepository {
    private val dao = db.mealPlanDao()

    override fun observeWeek(monday: LocalDate): Flow<List<PlannedMeal>> =
        dao.observeRange(monday.toString(), monday.plusDays(6).toString()).map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun set(date: LocalDate, slot: MealSlot, recipeId: String, servings: Int) {
        val portions = servings.coerceIn(MIN_SERVINGS, MAX_SERVINGS)
        db.withTransaction<Unit> {
            if (!dao.recipeExists(recipeId)) return@withTransaction
            val current = dao.getSlot(date.toString(), slot.key)
            if (current != null) {
                dao.updateSlot(current.id, recipeId, portions)
            } else {
                dao.insertIfFree(MealPlanEntity(UUID.randomUUID().toString(), date.toString(), slot.key, recipeId, portions))
            }
        }
    }

    override suspend fun changeServings(date: LocalDate, slot: MealSlot, delta: Int) =
        dao.addServings(date.toString(), slot.key, delta, MIN_SERVINGS, MAX_SERVINGS)

    override suspend fun clear(date: LocalDate, slot: MealSlot): PlannedMeal? = db.withTransaction {
        val row = dao.getSlot(date.toString(), slot.key) ?: return@withTransaction null
        dao.delete(row.id)
        row.toModel()
    }

    override suspend fun addAll(meals: List<PlannedMeal>): List<PlannedMeal> = db.withTransaction {
        meals.filter { meal ->
            dao.recipeExists(meal.recipeId) && dao.insertIfFree(meal.toEntity()) != -1L
        }
    }

    override suspend fun removeAdded(meals: Collection<PlannedMeal>) {
        if (meals.isEmpty()) return
        db.withTransaction { meals.forEach { dao.deleteIfStill(it.id, it.recipeId) } }
    }

    companion object {
        const val MIN_SERVINGS = 1
        const val MAX_SERVINGS = 99
    }
}

private fun MealPlanEntity.toModel(): PlannedMeal? {
    val s = MealSlot.entries.firstOrNull { it.key == slot } ?: return null
    return PlannedMeal(id, LocalDate.parse(date), s, recipeId, servings)
}

private fun PlannedMeal.toEntity() = MealPlanEntity(id, date.toString(), slot.key, recipeId, servings.coerceIn(RoomMealPlanRepository.MIN_SERVINGS, RoomMealPlanRepository.MAX_SERVINGS))
