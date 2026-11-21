package app.recetario.recipes.detail

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.FlavorProfile
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DetailScreenTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private var backs = 0
    private var edits = 0
    private var cookingServings: Int? = null

    @Before
    fun setUp() {
        container = installTestContainer()
    }

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
        description: String = "",
    ) = runBlocking {
        container.recipeRepository.save(
            sampleRecipe(
                "r1", "Crema de calabaza",
                ingredients = (1..count).map {
                    ingredient("i$it", if (it == 1) "Calabaza" else "Ingrediente $it", if (it == 1) 800.0 else 10.0 * it, MeasureUnit.GRAM)
                },
                steps = listOf(Step("s1", "Pela la calabaza.", emptyList(), null, emptySet())),
            ).copy(coverPhoto = cover, notes = notes, description = description),
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

    private val need = hasText("🛒 Lo que necesito → a la compra")

    /**
     * Desplaza hasta «Lo que necesito» y, si queda detrás de «▶ Modo cocina» (fijo abajo), un poco más: el relleno
     * inferior de la lista (alto real del botón + márgenes) deja siempre sitio para que quede por encima.
     */
    private fun scrollNeedAboveCookButton() {
        rule.onNodeWithTag("detail-list").performScrollToNode(need)
        val overlap = rule.onNode(need).getBoundsInRoot().bottom - rule.onNodeWithTag("detail-cook").getBoundsInRoot().top
        if (overlap > 0.dp) {
            val px = with(rule.density) { (overlap + 8.dp).toPx() }
            rule.onNodeWithTag("detail-list").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, px) }
        }
        rule.waitForIdle()
        assertThat(rule.onNode(need).getBoundsInRoot().bottom).isAtMost(rule.onNodeWithTag("detail-cook").getBoundsInRoot().top)
    }

    /** Un toque de verdad en el centro del botón (no solo su acción): no cae en «▶ Modo cocina». */
    private fun tapNeed() = rule.onNode(need).performClick()

    @Test
    fun need_addsUncheckedAndShowsMessage() {
        save()
        launch()
        rule.waitFor(hasText("Calabaza"))
        rule.onNodeWithText("Calabaza").performClick() // marcada: no se añade
        scrollNeedAboveCookButton()
        tapNeed()
        rule.waitFor(hasText("✓ 7 ingredientes añadidos a tu lista de compra"))
        assertThat(cookingServings).isNull() // el toque no cayó en «▶ Modo cocina»
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

    @Test
    fun description_isShownUnderTheTitle_onlyWhenNotBlank() {
        save(description = "Una crema suave de otoño")
        launch()
        rule.waitFor(hasText("Una crema suave de otoño"))
        rule.onNodeWithText("Una crema suave de otoño").assertIsDisplayed()
    }

    @Test
    fun noIngredients_hidesTheNeedButton() {
        save(count = 0)
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Pela la calabaza."))
        assertThat(rule.onAllNodes(hasText("🛒 Lo que necesito → a la compra")).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun need_secondCompletedTap_showsTheToastAgainAndRestartsItsTimer() {
        save()
        launch()
        rule.waitFor(hasText("Calabaza"))
        scrollNeedAboveCookButton()
        rule.mainClock.autoAdvance = false
        val message = hasText("✓ 8 ingredientes añadidos a tu lista de compra")
        fun toastShown() = rule.onAllNodes(message).fetchSemanticsNodes().isNotEmpty()
        fun pump(until: () -> Boolean) {
            repeat(500) {
                rule.mainClock.advanceTimeByFrame()
                if (until()) return
                Thread.sleep(10)
            }
            error("condición no cumplida")
        }
        fun calabazaGrams() = runBlocking { container.shoppingRepository.observe().first() }
            .values.flatten().firstOrNull { it.name == "Calabaza" }?.quantity

        tapNeed()
        pump(::toastShown)
        rule.mainClock.advanceTimeBy(2_000)
        tapNeed()
        pump { calabazaGrams() == 1600.0 }
        pump { true } // deja recomponer el nuevo aviso
        // 1 s después del segundo toque (3 s tras el primero) el aviso sigue: su temporizador se reinició.
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(toastShown()).isTrue()
    }

    @Test
    fun aShortConvertibleQuantity_stillHasA48dpTouchTarget() {
        runBlocking {
            container.recipeRepository.save(
                sampleRecipe("r1", "Crema de calabaza", ingredients = listOf(ingredient("i1", "Sal", 2.0, MeasureUnit.GRAM))),
            )
        }
        launch()
        rule.waitFor(hasText("2 g"))

        rule.onNodeWithText("2 g").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun flavor_isHidden_whenAllFiveTastesAreZero() {
        save()
        launch()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.waitFor(hasText("800 g"))

        assertThat(rule.onAllNodes(hasText("Sabor")).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun flavor_isShown_whenSomeTasteIsSet() {
        runBlocking {
            container.recipeRepository.save(sampleRecipe("r1", "Crema de calabaza", flavor = FlavorProfile(sweet = 2)))
        }
        launch()
        rule.waitFor(hasText("Crema de calabaza"))

        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Sabor"))
        rule.onNodeWithText("Sabor").assertIsDisplayed()
    }

    @Test
    fun servingsStepper_buttonsAreReadByTheirNameOnly() {
        save()
        launch()
        rule.waitFor(hasText("800 g"))

        // TalkBack lee «Quitar una porción», no «−».
        for (name in listOf("Quitar una porción", "Añadir una porción")) {
            rule.onNodeWithContentDescription(name).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        }
    }
}
