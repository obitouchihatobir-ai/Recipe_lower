package app.recetario.cooking

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.Cooking
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CookingScreenTest {
    @get:Rule
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
                    ingredients = listOf(
                        ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
                        ingredient("i2", "Mantequilla", 20.0, MeasureUnit.GRAM),
                        ingredient("i3", "Caldo de verduras", 750.0, MeasureUnit.MILLILITER),
                    ),
                    steps = listOf(
                        Step("s1", "Pela la calabaza y córtala en cubos.", emptyList(), null, setOf("i1")),
                        Step("s2", "Sofríe la cebolla y el ajo picados hasta que estén transparentes.", emptyList(), 300, setOf("i2")),
                        Step("s3", "Añade el caldo, cuece y tritura.", emptyList(), 1200, setOf("i1", "i3")),
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() = container.database.close()

    private fun open(recipeId: String = "r1", servings: Int = 4) {
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav) }
        }
        rule.runOnIdle { nav.navigate(Cooking(recipeId, servings)) }
        rule.waitFor(hasTestTag("cooking-list"))
    }

    private fun scrollTo(matcher: SemanticsMatcher) = rule.onNodeWithTag("cooking-list").performScrollToNode(matcher)

    private fun done(step: Int) {
        val button = hasContentDescription("Marcar el paso $step como hecho")
        rule.waitFor(button)
        scrollTo(button)
        rule.onNode(button).performClick()
    }

    @Test
    fun tapHecho_expandsNextStep() {
        open()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.onNodeWithText("Paso 1 de 3").assertIsDisplayed()

        done(1)

        rule.waitFor(hasContentDescription("Marcar el paso 2 como hecho"))
        rule.onNodeWithContentDescription("Marcar el paso 1 como hecho").assertDoesNotExist()
        rule.onNodeWithText("Paso 2 de 3").assertIsDisplayed()
        // El paso 2 expandido muestra sus ingredientes y su temporizador.
        scrollTo(hasText("⏱ Iniciar 5:00"))
        rule.onNodeWithText("⏱ Iniciar 5:00").assertIsDisplayed()
        rule.onNodeWithText("Mantequilla 20 g").assertIsDisplayed()
    }

    @Test
    fun quantitiesUseServingsArg() {
        open(servings = 6)
        rule.waitFor(hasText("1,2 kg"))
        rule.onNodeWithText("1,2 kg").assertIsDisplayed()
        // También en los chips del paso actual.
        scrollTo(hasText("Calabaza 1,2 kg"))
        rule.onNodeWithText("Calabaza 1,2 kg").assertIsDisplayed()
    }

    @Test
    fun tapADoneStep_goesBackToIt() {
        open()
        done(1)
        rule.waitFor(hasContentDescription("Marcar el paso 2 como hecho"))
        scrollTo(hasText("Pela la calabaza y córtala en cubos."))
        rule.onNodeWithText("Pela la calabaza y córtala en cubos.").performClick()
        rule.waitFor(hasContentDescription("Marcar el paso 1 como hecho"))
        rule.onNodeWithText("Paso 1 de 3").assertIsDisplayed()
    }

    @Test
    fun lastStep_showsListo_andBackToTheRecipe() {
        open()
        done(1)
        done(2)
        done(3)
        rule.waitFor(hasText("¡Listo!"))
        scrollTo(hasText("Volver a la receta"))
        rule.onNodeWithText("Volver a la receta").performClick()
        rule.waitForGone(hasTestTag("cooking-list"))
        rule.runOnIdle { assertThat(nav.currentDestination?.hasRoute(Cooking::class)).isFalse() }
    }

    @Test
    fun closeButton_leavesCookingMode() {
        open()
        rule.waitFor(hasContentDescription("Cerrar modo cocina"))
        rule.onNodeWithContentDescription("Cerrar modo cocina").performClick()
        rule.waitForGone(hasTestTag("cooking-list"))
    }

    @Test
    fun ingredients_collapseAndExpand() {
        open()
        rule.waitFor(hasText("Caldo de verduras"))
        rule.onNodeWithText("Ingredientes").performClick()
        rule.waitForGone(hasText("Caldo de verduras"))
        rule.onNodeWithText("Ingredientes").performClick()
        rule.waitFor(hasText("Caldo de verduras"))
    }

    @Test
    fun timer_startPauseAddMinuteFinishAndDismiss() {
        open()
        done(1)
        val idle = hasText("⏱ Iniciar 5:00")
        rule.waitFor(idle)
        scrollTo(idle)
        rule.onNode(idle).performClick()

        rule.waitFor(hasText("⏸ ", substring = true))
        assertThat(container.timerManager.timers.value.single().isRunning).isTrue()
        assertThat(container.timerManager.timers.value.single().label).isEqualTo("Paso 2: Sofríe la cebolla y el ajo…")
        // El primer temporizador arranca el servicio en primer plano.
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedService
        assertThat(started?.component?.className).isEqualTo(TimerService::class.java.name)

        rule.onNodeWithContentDescription("Sumar un minuto al temporizador del paso 2").performClick()
        rule.runOnIdle { assertThat(container.timerManager.timers.value.single().totalSeconds).isEqualTo(360) }

        rule.onNode(hasText("⏸ ", substring = true)).performClick()
        rule.waitFor(hasText("▶ ", substring = true))
        assertThat(container.timerManager.timers.value.single().isPaused).isTrue()

        rule.onNode(hasText("▶ ", substring = true)).performClick()
        rule.waitFor(hasText("⏸ ", substring = true))
        rule.runOnIdle { container.timerManager.tick(container.timerManager.now() + 3_600_000) }
        rule.waitFor(hasText("✓ ¡Tiempo!"))

        rule.onNodeWithText("✓ ¡Tiempo!").performClick()
        rule.waitFor(idle)
        assertThat(container.timerManager.timers.value).isEmpty()
    }

    @Test
    fun runningTimer_staysVisibleOnItsStepAfterMovingOn() {
        open()
        done(1)
        val idle = hasText("⏱ Iniciar 5:00")
        rule.waitFor(idle)
        scrollTo(idle)
        rule.onNode(idle).performClick()
        rule.waitFor(hasText("⏸ ", substring = true))
        done(2)
        rule.waitFor(hasContentDescription("Marcar el paso 3 como hecho"))
        // El paso 2 ya no está expandido, pero su temporizador sigue a la vista.
        rule.onNodeWithContentDescription("Pausar el temporizador del paso 2", substring = true).assertExists()
    }

    @Test
    fun keepsTheScreenOn_whileCooking() {
        open()
        rule.waitFor(hasText("Crema de calabaza"))
        rule.runOnIdle {
            val flags = rule.activity.window.attributes.flags
            assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isNotEqualTo(0)
        }
        rule.runOnIdle { nav.popBackStack() }
        rule.waitForGone(hasTestTag("cooking-list"))
        rule.runOnIdle {
            val flags = rule.activity.window.attributes.flags
            assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isEqualTo(0)
        }
    }

    @Test
    fun missingRecipe_saysSo() {
        open(recipeId = "no-existe")
        rule.waitFor(hasText("Esta receta ya no existe."))
    }
}
