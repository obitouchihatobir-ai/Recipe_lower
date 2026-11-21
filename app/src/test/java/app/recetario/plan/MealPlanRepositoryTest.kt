package app.recetario.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.backup.MEAL_SLOTS
import app.recetario.backup.PreviewResult
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MealPlanRepositoryTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    private lateinit var container: AppContainer
    private val repo get() = container.mealPlanRepository

    private val monday = LocalDate.of(2026, 11, 9)
    private val friday = LocalDate.of(2026, 11, 13)

    @Before
    fun setUp() {
        container = installTestContainer()
        runBlocking {
            container.recipeRepository.save(sampleRecipe("r1", "Arroz con tomate"))
            container.recipeRepository.save(sampleRecipe("r2", "Caldo"))
        }
    }

    private fun week(start: LocalDate = monday) = runBlocking { repo.observeWeek(start).first() }

    @Test
    fun set_replacesOccupiedSlot_noUpsertTrap() = runBlocking<Unit> {
        repo.set(friday, MealSlot.LUNCH, "r1", 4)
        val first = week().single()
        repo.set(friday, MealSlot.LUNCH, "r2", 2)
        val meals = week()
        // Un solo plato en el hueco, con la receta nueva y el mismo id (nada falla en silencio).
        assertThat(meals).containsExactly(PlannedMeal(first.id, friday, MealSlot.LUNCH, "r2", 2))
    }

    @Test
    fun set_unknownRecipe_doesNothing_andServingsStayInRange() = runBlocking<Unit> {
        repo.set(friday, MealSlot.LUNCH, "no-existe", 4)
        assertThat(week()).isEmpty()
        repo.set(friday, MealSlot.DINNER, "r1", 0)
        assertThat(week().single().servings).isEqualTo(1)
    }

    @Test
    fun clear_returnsForUndo() = runBlocking<Unit> {
        repo.set(friday, MealSlot.DINNER, "r1", 3)
        val before = week()
        val removed = repo.clear(friday, MealSlot.DINNER)
        assertThat(removed).isEqualTo(before.single())
        assertThat(week()).isEmpty()
        assertThat(repo.clear(friday, MealSlot.DINNER)).isNull()
        assertThat(repo.addAll(listOf(removed!!))).containsExactly(removed)
        assertThat(week()).isEqualTo(before)
    }

    @Test
    fun addAll_neverOverwritesAnOccupiedSlot_andReturnsOnlyTheAdded() = runBlocking<Unit> {
        repo.set(friday, MealSlot.LUNCH, "r1", 4)
        val kept = week().single()
        val clash = PlannedMeal("nuevo-1", friday, MealSlot.LUNCH, "r2", 2)
        val free = PlannedMeal("nuevo-2", friday, MealSlot.DINNER, "r2", 2)
        val orphan = PlannedMeal("nuevo-3", friday, MealSlot.BREAKFAST, "no-existe", 2)
        assertThat(repo.addAll(listOf(clash, free, orphan))).containsExactly(free)
        assertThat(week()).containsExactly(kept, free)
        repo.removeAll(listOf("nuevo-1", "nuevo-2", "nuevo-3"))
        assertThat(week()).containsExactly(kept)
    }

    @Test
    fun observeWeek_onlyThatWeek() = runBlocking<Unit> {
        repo.set(monday.minusDays(1), MealSlot.LUNCH, "r1", 4)
        repo.set(monday, MealSlot.LUNCH, "r1", 4)
        repo.set(monday.plusDays(6), MealSlot.DINNER, "r2", 4)
        repo.set(monday.plusDays(7), MealSlot.LUNCH, "r2", 4)
        assertThat(week().map { it.date }).containsExactly(monday, monday.plusDays(6))
    }

    @Test
    fun deleteRecipe_removesItsSlots() = runBlocking<Unit> {
        repo.set(friday, MealSlot.LUNCH, "r1", 4)
        repo.set(friday.plusDays(1), MealSlot.DINNER, "r1", 4)
        repo.set(friday, MealSlot.DINNER, "r2", 4)
        container.recipeRepository.delete("r1")
        assertThat(week().map { it.recipeId }).containsExactly("r2")
    }

    @Test
    fun slotsWritten_alwaysInMealSlots() = runBlocking<Unit> {
        for (slot in MealSlot.entries) repo.set(friday, slot, "r1", 2)
        repo.addAll(MealSlot.entries.map { PlannedMeal("p-${it.key}", friday.plusDays(1), it, "r2", 2) })
        val rows = container.database.mealPlanDao().getAll()
        assertThat(rows).hasSize(6)
        assertThat(rows.map { it.slot }.toSet()).isEqualTo(MEAL_SLOTS.toSet())
        // Lo escrito pasa la validación de las copias: exportar y comprobar la copia entera.
        val out = ByteArrayOutputStream()
        container.backupManager.export(out)
        assertThat(container.backupManager.preview(ByteArrayInputStream(out.toByteArray()))).isInstanceOf(PreviewResult.Ok::class.java)
    }
}
