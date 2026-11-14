package app.recetario.converter

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.recipes.detail.DetailRoute
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ConverterScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    private fun launchConverter() = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) { ConverterRoute(container, onBack = {}) }
    }

    private fun fill(value: String, ingredient: String = "") {
        rule.onNodeWithTag("converter-value").performTextReplacement(value)
        rule.onNodeWithTag("converter-ingredient").performTextReplacement(ingredient)
    }

    @Test
    fun converter_1cupFlour_shows125g() {
        launchConverter()
        fill("1", "harina")
        rule.onNodeWithTag("from-CUP").performClick()
        rule.onNodeWithTag("to-GRAM").performClick()
        rule.onNodeWithTag("converter-result").assertTextEquals("125 g")
    }

    @Test
    fun converter_unknownIngredient_showsDensityMessage() {
        launchConverter()
        fill("1", "pollo")
        rule.onNodeWithText("Para pasar de volumen a peso necesito saber el ingrediente (por ejemplo, harina).").assertIsDisplayed()
    }

    @Test
    fun converter_noIngredientVolumeToVolume_convertsWithoutIngredient() {
        launchConverter()
        fill("2")
        rule.onNodeWithTag("to-MILLILITER").performClick()
        rule.onNodeWithTag("converter-result").assertTextEquals("480 ml")
    }

    @Test
    fun converter_unparseableValue_showsError() {
        launchConverter()
        fill("abc")
        rule.onNodeWithText("Escribe un número, por ejemplo 1,5 o ½.").assertIsDisplayed()
    }

    @Test
    fun converter_swap_exchangesUnits() {
        launchConverter()
        fill("250", "harina")
        rule.onNodeWithTag("to-GRAM").performClick()
        rule.onNodeWithContentDescription("Intercambiar unidades").performClick()
        rule.onNodeWithTag("converter-result").assertTextEquals("2 tazas")
    }

    @Test
    fun converter_typingIngredient_offersSuggestions() {
        launchConverter()
        rule.onNodeWithTag("converter-ingredient").performTextReplacement("har")
        rule.onNodeWithTag("suggestion-harina").performClick()
        rule.onNodeWithTag("converter-result").assertTextEquals("125 g")
    }

    private fun saveFlourRecipe() = runBlocking {
        container.recipeRepository.save(
            sampleRecipe(
                "r1", "Bizcocho",
                ingredients = listOf(
                    ingredient("i1", "Harina", 1.5, MeasureUnit.CUP),
                    ingredient("i2", "Cebolla", 1.0, MeasureUnit.PIECE),
                ),
            ),
        )
    }

    private fun launchDetail() = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) {
            DetailRoute(container, "r1", onBack = {}, onEdit = {}, onCooking = {})
        }
    }

    @Test
    fun detail_tapQuantity_opensConversionsSheet() {
        saveFlourRecipe()
        launchDetail()
        rule.waitFor(hasText("1½ tazas"))
        rule.onNodeWithText("1½ tazas").performClick()
        rule.waitFor(hasTestTag("conversions-panel"))
        rule.onNodeWithTag("conversions-title").assertTextEquals("Harina · 1½ tazas")
        rule.onNodeWithText("187 g").assertIsDisplayed()
        rule.onNodeWithText("360 ml").assertIsDisplayed()
        rule.onNodeWithText("24 cucharadas").assertIsDisplayed()
        // Cerrar con el botón.
        rule.onNodeWithText("Cerrar").performClick()
        rule.waitFor(hasTestTag("detail-list"))
        rule.onAllNodes(hasTestTag("conversions-panel")).assertCountEquals(0)
    }

    @Test
    fun detail_countQuantity_isNotAButton() {
        saveFlourRecipe()
        launchDetail()
        rule.waitFor(hasText("1½ tazas"))
        rule.onAllNodes(hasText("1½ tazas") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(1)
        rule.onAllNodes(hasText("1") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
    }

    @Test
    fun recipesTopBar_converterAction_opensConverter() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasContentDescription("Conversor de medidas"))
        rule.onNodeWithContentDescription("Conversor de medidas").performClick()
        rule.waitFor(hasTestTag("converter-screen"))
    }
}
