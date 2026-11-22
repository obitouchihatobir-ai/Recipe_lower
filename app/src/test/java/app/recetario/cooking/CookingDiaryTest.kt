package app.recetario.cooking

import app.recetario.testing.CloseDatabaseRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.Cooking
import app.recetario.recipes.model.Step
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
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
import java.time.LocalDate

/** La tarjeta «Guardar en el diario» bajo «¡Listo!» (diseño §7.1). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CookingDiaryTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        container = installTestContainer()
        runBlocking {
            container.recipeRepository.save(
                sampleRecipe(
                    "r1", "Crema de calabaza",
                    steps = listOf(
                        Step("s1", "Pela la calabaza.", emptyList(), null, emptySet()),
                        Step("s2", "Cuece y tritura.", emptyList(), null, emptySet()),
                    ),
                ),
            )
        }
    }

    private fun scrollTo(matcher: SemanticsMatcher) = rule.onNodeWithTag("cooking-list").performScrollToNode(matcher)

    /** Abre el modo cocina y hace los dos pasos hasta «¡Listo!». */
    private fun finish() {
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav) }
        }
        rule.runOnIdle { nav.navigate(Cooking("r1", 4)) }
        rule.waitFor(hasTestTag("cooking-list"))
        for (step in 1..2) {
            val button = hasContentDescription("Marcar el paso $step como hecho")
            rule.waitFor(button)
            scrollTo(button)
            rule.onNode(button).performClick()
        }
        rule.waitFor(hasText("¡Listo!"))
    }

    private fun entries() = runBlocking { container.diaryRepository.observeEntries().first() }

    @Test
    fun listo_showsDiaryCard_saveCreatesEntry() {
        finish()
        rule.waitFor(hasTestTag("diary-card"))
        scrollTo(hasTestTag("diary-card"))
        rule.onNodeWithText("Guardar en el diario").assertExists()
        // Ya rellena: hoy, sin valoración y sin nota.
        rule.onNodeWithTag("rating-description", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Sin valoración", useUnmergedTree = true).assertExists()

        scrollTo(hasContentDescription("4 estrellas"))
        rule.onNodeWithContentDescription("4 estrellas").performClick()
        rule.waitFor(hasText("4 de 5 estrellas"))
        scrollTo(hasTestTag("diary-note"))
        rule.onNodeWithTag("diary-note").performTextInput("Con un poco más de nata")
        scrollTo(hasTestTag("diary-save"))
        rule.onNodeWithTag("diary-save").performClick()

        rule.waitFor(hasText("✓ Guardado en el diario"))
        rule.waitUntil(5_000) { entries().isNotEmpty() }
        val entry = entries().single()
        assertThat(entry.recipeId).isEqualTo("r1")
        assertThat(entry.recipeTitle).isEqualTo("Crema de calabaza")
        assertThat(entry.rating).isEqualTo(4)
        assertThat(entry.note).isEqualTo("Con un poco más de nata")
        assertThat(entry.date).isEqualTo(LocalDate.now(testClock))

        // Guardar no tapa la salida del modo cocina.
        scrollTo(hasText("Volver a la receta"))
        rule.onNodeWithText("Volver a la receta").performClick()
        rule.waitForGone(hasTestTag("cooking-list"))
        rule.runOnIdle { assertThat(nav.currentDestination?.hasRoute(Cooking::class)).isFalse() }
    }

    @Test
    fun listo_ahoraNo_hidesCard() {
        finish()
        rule.waitFor(hasTestTag("diary-card"))
        scrollTo(hasText("Ahora no"))
        rule.onNodeWithText("Ahora no").performClick()

        rule.waitForGone(hasTestTag("diary-card"))
        rule.onNodeWithText("✓ Guardado en el diario").assertDoesNotExist()
        assertThat(entries()).isEmpty()
        scrollTo(hasText("Volver a la receta"))
        rule.onNodeWithText("Volver a la receta").performClick()
        rule.waitForGone(hasTestTag("cooking-list"))
    }
}
