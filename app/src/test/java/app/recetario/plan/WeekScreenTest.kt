package app.recetario.plan

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.shopping.ShoppingItem
import app.recetario.shopping.ShoppingRoute
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** La pestaña Semana con el reloj de las pruebas: viernes 13 de noviembre de 2026 (semana del lunes 9). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class WeekScreenTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    private val monday = LocalDate.of(2026, 11, 9)
    private val wednesday = LocalDate.of(2026, 11, 11)
    private val friday = LocalDate.of(2026, 11, 13)
    private val saturday = LocalDate.of(2026, 11, 14)

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    private fun seedRecipes() = runBlocking {
        container.recipeRepository.save(
            sampleRecipe(
                "r1", "Arroz con tomate",
                ingredients = listOf(
                    ingredient("i1", "Arroz", 200.0, MeasureUnit.GRAM),
                    ingredient("i2", "Tomates", 4.0, MeasureUnit.PIECE),
                    ingredient("i3", "Sal", null, MeasureUnit.TO_TASTE),
                ),
            ),
        )
        container.recipeRepository.save(sampleRecipe("r2", "Caldo de verduras"))
        container.recipeRepository.save(sampleRecipe("r3", "Tortilla de patatas"))
    }

    private fun launch() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { ShoppingRoute(container) } }
        rule.waitFor(hasTestTag("shopping-tab-week"))
        rule.onNodeWithTag("shopping-tab-week").performClick()
        rule.waitFor(hasTestTag("week-screen"))
    }

    private fun meals(start: LocalDate = monday) = runBlocking { container.mealPlanRepository.observeWeek(start).first() }

    private fun shopping(): List<ShoppingItem> = runBlocking { container.shoppingRepository.observe().first().values.flatten() }

    private fun openSlot(date: LocalDate, slot: MealSlot) {
        rule.waitFor(hasTestTag(weekSlotTag(date, slot)))
        rule.onNodeWithTag(weekSlotTag(date, slot)).performScrollTo().performClick()
        rule.waitFor(hasTestTag("week-panel"))
    }

    @Test
    fun emptyState_noRecipes() {
        launch()
        rule.waitFor(hasText(WEEK_EMPTY_TEXT))
        rule.onNodeWithTag("week-fill").assertDoesNotExist()
    }

    @Test
    fun week_navigation_titles() {
        seedRecipes()
        launch()
        rule.waitFor(hasText("Esta semana"))
        rule.onNodeWithText("Lunes 9").assertExists()
        rule.onNodeWithTag("week-next").performClick()
        rule.waitFor(hasText("Semana que viene"))
        rule.waitFor(hasText("Lunes 16"))
        rule.onNodeWithTag("week-next").performClick()
        rule.waitFor(hasText("Semana del 23 de noviembre"))
        rule.onNodeWithTag("week-previous").performClick()
        rule.onNodeWithTag("week-previous").performClick()
        rule.onNodeWithTag("week-previous").performClick()
        rule.waitFor(hasText("Semana pasada"))
        rule.waitFor(hasText("Lunes 2"))
        rule.onNodeWithTag("week-previous").assert(hasContentDescription("Semana anterior"))
    }

    @Test
    fun tapSlot_chooseRecipe_saves() {
        seedRecipes()
        launch()
        openSlot(friday, MealSlot.LUNCH)
        rule.onNodeWithTag("week-search").performTextInput("tomate")
        rule.waitFor(hasTestTag("week-choice-r1"))
        rule.onNodeWithTag("week-choice-r2").assertDoesNotExist()
        rule.onNodeWithTag("week-choice-r1").performClick()
        rule.waitFor(hasContentDescription("Viernes 13, comida: Arroz con tomate, 4 porciones"))
        rule.onNodeWithTag("week-panel").assertDoesNotExist()
        assertThat(meals().map { Triple(it.date, it.slot, it.recipeId) }).containsExactly(Triple(friday, MealSlot.LUNCH, "r1"))

        // Cambiar por otra receta: el mismo hueco, sin duplicados.
        openSlot(friday, MealSlot.LUNCH)
        rule.onNodeWithTag("week-choice-r3").performClick()
        rule.waitFor(hasContentDescription("Viernes 13, comida: Tortilla de patatas, 4 porciones"))
        assertThat(meals().map { it.recipeId }).containsExactly("r3")
    }

    @Test
    fun changeServings() {
        seedRecipes()
        runBlocking { container.mealPlanRepository.set(friday, MealSlot.DINNER, "r2", 4) }
        launch()
        openSlot(friday, MealSlot.DINNER)
        rule.onNodeWithTag("week-servings-plus").assert(hasContentDescription("Añadir una porción")).performClick()
        rule.waitFor(hasContentDescription("Viernes 13, cena: Caldo de verduras, 5 porciones"))
        rule.onNodeWithTag("week-servings-minus").performClick()
        rule.onNodeWithTag("week-servings-minus").performClick()
        rule.waitFor(hasContentDescription("Viernes 13, cena: Caldo de verduras, 3 porciones"))
        assertThat(meals().single().servings).isEqualTo(3)
    }

    @Test
    fun remove_offersUndo() {
        seedRecipes()
        runBlocking { container.mealPlanRepository.set(friday, MealSlot.DINNER, "r2", 2) }
        val before = meals()
        launch()
        openSlot(friday, MealSlot.DINNER)
        rule.onNodeWithTag("week-remove").performClick()
        rule.waitFor(hasText("Se quitó «Caldo de verduras»"))
        rule.waitFor(hasContentDescription("Viernes 13, cena: vacío"))
        assertThat(meals()).isEmpty()
        rule.onNodeWithText("Deshacer").performClick()
        rule.waitFor(hasContentDescription("Viernes 13, cena: Caldo de verduras, 2 porciones"))
        assertThat(meals()).isEqualTo(before)
    }

    @Test
    fun fillWeek_addsOnlyEmpty_andUndo_restoresExactly() {
        seedRecipes()
        runBlocking {
            container.mealPlanRepository.set(friday, MealSlot.LUNCH, "r1", 6)
            container.mealPlanRepository.set(wednesday, MealSlot.DINNER, "r2", 2)
        }
        val before = meals()
        launch()
        rule.waitFor(hasTestTag("week-fill"))
        rule.onNodeWithTag("week-fill").performClick()
        // Viernes (desayuno y cena), sábado y domingo: 8 huecos; los días pasados y el hueco ocupado no se tocan.
        rule.waitFor(hasText("Se añadieron 8 comidas"))
        val filled = meals()
        assertThat(filled).hasSize(10)
        assertThat(filled).containsAtLeastElementsIn(before)
        assertThat(filled.filter { it.date < friday }).isEqualTo(before.filter { it.date < friday })
        assertThat(filled.map { it.date to it.slot }.toSet()).hasSize(10)
        // Cada plato nuevo con las porciones de su receta (4).
        val added = filled - before.toSet()
        assertThat(added.map { it.servings }.toSet()).containsExactly(4)
        rule.waitFor(hasContentDescription("Viernes 13, comida: Arroz con tomate, 6 porciones"))

        rule.onNodeWithText("Deshacer").performClick()
        rule.waitUntil(5_000) { meals() == before }
        assertThat(meals()).isEqualTo(before)
    }

    @Test
    fun generateList_addsMissing_message() {
        seedRecipes()
        runBlocking {
            // La receta es para 4: para 8, el doble. El arroz está en la despensa y la sal es un básico.
            container.mealPlanRepository.set(saturday, MealSlot.LUNCH, "r1", 8)
            container.mealPlanRepository.set(wednesday, MealSlot.LUNCH, "r1", 4)
            container.pantryRepository.add("Arroz")
        }
        launch()
        rule.waitFor(hasTestTag("week-generate"))
        rule.onNodeWithTag("week-generate").performClick()
        rule.waitFor(hasText("✓ 1 ingrediente añadido"))
        val first = shopping().single()
        assertThat(first.name).isEqualTo("Tomates")
        assertThat(first.quantity).isEqualTo(8.0)
        assertThat(first.sourceRecipeId).isEqualTo("r1")

        // Comprado: generar otra vez añade una línea nueva y nunca pisa la marcada.
        runBlocking { container.shoppingRepository.toggle(first.id) }
        rule.onNodeWithTag("week-generate").performClick()
        rule.waitUntil(5_000) { shopping().size == 2 }
        val items = shopping()
        assertThat(items.single { it.checked }).isEqualTo(first.copy(checked = true))
        val fresh = items.single { !it.checked }
        assertThat(fresh.id).isNotEqualTo(first.id)
        assertThat(fresh.quantity).isEqualTo(8.0)
        assertThat(fresh.createdAt).isEqualTo(container.clock.instant())

        // Sin marcar: la tercera vez se suma a la línea abierta.
        rule.onNodeWithTag("week-generate").performClick()
        rule.waitUntil(5_000) { shopping().single { !it.checked }.quantity == 16.0 }
        assertThat(shopping()).hasSize(2)
    }

    @Test
    fun generateList_nothingMissing() {
        seedRecipes()
        runBlocking {
            container.mealPlanRepository.set(saturday, MealSlot.LUNCH, "r1", 4)
            container.pantryRepository.add("Arroz")
            container.pantryRepository.add("Tomates")
        }
        launch()
        rule.waitFor(hasTestTag("week-generate"))
        rule.onNodeWithTag("week-generate").performClick()
        rule.waitFor(hasText("No falta nada para esta semana"))
        assertThat(shopping()).isEmpty()
    }

    /** En una semana pasada no hay nada que rellenar ni que comprar: los dos botones van apagados. */
    @Test
    fun pastWeek_fillAndGenerateAreDisabled() {
        seedRecipes()
        launch()
        rule.waitFor(hasText("Esta semana"))
        rule.onNodeWithTag("week-fill").assertIsEnabled()
        rule.onNodeWithTag("week-generate").assertIsEnabled()
        rule.onNodeWithTag("week-previous").performClick()
        rule.waitFor(hasText("Semana pasada"))
        rule.onNodeWithTag("week-fill").assertIsNotEnabled()
        rule.onNodeWithTag("week-generate").assertIsNotEnabled()
        rule.onNodeWithTag("week-next").performClick()
        rule.onNodeWithTag("week-next").performClick()
        rule.waitFor(hasText("Semana que viene"))
        rule.onNodeWithTag("week-fill").assertIsEnabled()
        rule.onNodeWithTag("week-generate").assertIsEnabled()
    }
}
