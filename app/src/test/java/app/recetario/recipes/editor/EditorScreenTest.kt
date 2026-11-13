package app.recetario.recipes.editor

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.testing.installTestContainer
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
class EditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    /** Abre el editor de una receta nueva desde la pestaña Recetas. */
    private fun openNewRecipe() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.onNodeWithContentDescription("Nueva receta").performClick()
        rule.waitFor(hasTestTag("editor-title"))
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag("editor-list").performScrollToNode(hasTestTag(tag))

    private fun scrollToText(text: String) = rule.onNodeWithTag("editor-list").performScrollToNode(hasText(text))

    @Test
    fun editor_createRecipe_appearsInList() {
        openNewRecipe()
        rule.onNodeWithTag("editor-title").performTextInput("Limonada de la casa")
        rule.onNodeWithText("Bebidas").performClick()
        scrollToText("Añadir ingrediente")
        rule.onNodeWithText("Añadir ingrediente").performClick()
        scrollTo("ingredient-name")
        rule.onNodeWithTag("ingredient-name").performTextInput("Limón")
        rule.onNodeWithTag("ingredient-quantity").performTextInput("1,5")

        rule.onNodeWithText("Guardar").performClick()

        // Vuelve a la lista, que ya muestra la receta.
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitFor(hasText("Limonada de la casa"))
        rule.onNodeWithText("Limonada de la casa").assertIsDisplayed()
        val summary = runBlocking { container.recipeRepository.observeSummaries().first() }.single()
        val saved = runBlocking { container.recipeRepository.observeRecipe(summary.id).first() }!!
        assertThat(saved.categoryId).isEqualTo(DefaultCategories.DRINK_ID)
        assertThat(saved.ingredients.single().name).isEqualTo("Limón")
        assertThat(saved.ingredients.single().quantity).isEqualTo(1.5)
    }

    @Test
    fun saveWithoutTitleOrCategory_showsErrorsUnderTheFields() {
        openNewRecipe()
        rule.onNodeWithText("Guardar").performClick()
        rule.waitFor(hasText("Ponle un nombre a la receta"))
        rule.onNodeWithText("Ponle un nombre a la receta", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Elige una categoría", useUnmergedTree = true).assertIsDisplayed()
        // Sigue en el editor.
        rule.onNodeWithText("Nueva receta").assertIsDisplayed()
    }

    @Test
    fun stepWithTime_offersTimerChip_andTapAddsTimer() {
        openNewRecipe()
        scrollToText("Añadir paso")
        rule.onNodeWithText("Añadir paso").performClick()
        scrollTo("step-text")
        rule.onNodeWithTag("step-text").performTextInput("Cuece 20 minutos a fuego medio")

        rule.waitFor(hasText("⏱ Añadir temporizador de 20 min"))
        rule.onNodeWithText("⏱ Añadir temporizador de 20 min").performClick()

        rule.waitFor(hasText("⏱ 20 min"))
        rule.onNodeWithText("⏱ Añadir temporizador de 20 min").assertDoesNotExist()
        rule.onNodeWithContentDescription("Quitar temporizador").assertIsDisplayed()
    }

    @Test
    fun pendingDraft_isOfferedModally_andRecovered() {
        DraftStore(container.draftsDir).save(
            DraftStore.NEW_KEY,
            EditorState(recipeId = "borrador", title = "Gazpacho a medias"),
        )
        openNewRecipe()
        rule.waitFor(hasText("¿Recuperar el borrador?"))
        rule.onNodeWithText("Descartar").assertIsDisplayed()

        rule.onNodeWithText("Recuperar").performClick()

        rule.waitForGone(hasText("¿Recuperar el borrador?"))
        rule.onNodeWithTag("editor-title").assert(hasText("Gazpacho a medias"))
    }
}
