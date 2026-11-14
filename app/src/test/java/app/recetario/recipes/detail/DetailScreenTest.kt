package app.recetario.recipes.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private var backs = 0
    private var edits = 0
    private var cookingServings: Int? = null

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    private fun launch() = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) {
            DetailRoute(
                container, "r1",
                onBack = { backs++ },
                onEdit = { edits++ },
                onCooking = { cookingServings = it },
            )
        }
    }

    private fun save(
        count: Int = 8,
        cover: String? = null,
        notes: String = "",
    ) = runBlocking {
        container.recipeRepository.save(
            sampleRecipe(
                "r1", "Crema de calabaza",
                ingredients = (1..count).map {
                    ingredient("i$it", if (it == 1) "Calabaza" else "Ingrediente $it", if (it == 1) 800.0 else 10.0 * it, MeasureUnit.GRAM)
                },
                steps = listOf(Step("s1", "Pela la calabaza.", emptyList(), null, emptySet())),
            ).copy(coverPhoto = cover, notes = notes),
        )
    }

    @Test
    fun showsTitleIngredientsStepsAndNotes() {
        save(notes = "Menos caldo la próxima vez")
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithText("800 g").assertIsDisplayed()
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Pela la calabaza."))
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Menos caldo la próxima vez"))
        rule.onNodeWithText("Mis notas").assertIsDisplayed()
    }

    @Test
    fun blankNotes_hideTheNotesSection() {
        save()
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Pela la calabaza."))
        assertThat(rule.onAllNodes(hasText("Mis notas")).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun missingCoverFile_showsPlaceholder_noCrash() {
        save(cover = "no-existe.jpg")
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithText("🍲").assertIsDisplayed()
    }

    @Test
    fun detailWith60Ingredients_scrollsToLast() {
        save(count = 60)
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Ingrediente 60"))
        rule.onNodeWithText("Ingrediente 60").assertIsDisplayed()
    }

    @Test
    fun stepper_rescalesQuantities() {
        save()
        launch()
        rule.waitFor(hasText("800 g"))
        rule.onNodeWithContentDescription("Añadir una porción").performClick()
        rule.onNodeWithContentDescription("Añadir una porción").performClick()
        rule.waitFor(hasText("1,2 kg"))
    }

    @Test
    fun need_addsUncheckedAndShowsMessage() {
        save()
        launch()
        rule.waitFor(hasText("Calabaza"))
        rule.onNodeWithText("Calabaza").performClick() // marcada: no se añade
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("🛒 Lo que necesito → a la compra"))
        rule.onNodeWithText("🛒 Lo que necesito → a la compra").performClick()
        rule.waitFor(hasText("✓ 7 ingredientes añadidos a tu lista de compra"))
        val items = runBlocking { container.shoppingRepository.observe().first() }.values.flatten()
        assertThat(items).hasSize(7)
    }

    @Test
    fun cookingButton_passesTheChosenServings() {
        save()
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithContentDescription("Añadir una porción").performClick()
        rule.onNodeWithText("▶ Modo cocina").performClick()
        assertThat(cookingServings).isEqualTo(5)
    }

    @Test
    fun menu_editAndBack() {
        save()
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithContentDescription("Volver").performClick()
        assertThat(backs).isEqualTo(1)
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Editar").performClick()
        assertThat(edits).isEqualTo(1)
    }

    @Test
    fun delete_asksForConfirmation_thenRemovesAndGoesBack() {
        save()
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Borrar").performClick()
        rule.waitFor(hasText("¿Borrar esta receta?"))
        // Cancelar no borra.
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForGone(hasText("¿Borrar esta receta?"))
        assertThat(runBlocking { container.recipeRepository.observeRecipe("r1").first() }).isNotNull()
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Borrar").performClick()
        rule.waitFor(hasText("¿Borrar esta receta?"))
        rule.onNodeWithTag("confirm-delete").performClick()
        rule.waitUntil(5_000) { backs == 1 }
        assertThat(runBlocking { container.recipeRepository.observeRecipe("r1").first() }).isNull()
    }

    @Test
    fun favoriteHeart_togglesFavorite() {
        save()
        launch()
        rule.waitFor(hasContentDescription("Marcar como favorita"))
        rule.onNodeWithContentDescription("Marcar como favorita").performClick()
        rule.waitFor(hasContentDescription("Quitar de favoritas"))
    }
}
