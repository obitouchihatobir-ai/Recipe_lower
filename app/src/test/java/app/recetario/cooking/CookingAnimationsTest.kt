package app.recetario.cooking

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.motion.AnimationProgress
import app.recetario.core.ui.motion.CONFETTI_MS
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.model.Step
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * Animaciones del modo cocina (Task 18): el tachado de «Hecho ✓», el confeti de «¡Listo!», el anillo del
 * temporizador y el latido de «✓ ¡Tiempo!». Con las animaciones quitadas, todo en su estado final al instante.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CookingAnimationsTest {
    @get:Rule
    val rule = createComposeRule()

    private val photoStore = PhotoStore(File(Files.createTempDirectory("fotos").toFile(), "photos"))
    private val recipe = sampleRecipe(
        "r1", "Crema de calabaza",
        steps = listOf(
            Step("s1", "Pela la calabaza y córtala en cubos.", emptyList(), null, emptySet()),
            Step("s2", "Sofríe la cebolla.", emptyList(), 300, emptySet()),
        ),
    )
    private var state by mutableStateOf(CookingUiState(loading = false, recipe = recipe))
    private var closed = 0

    private fun open(reduced: Boolean, now: Long = 0L) {
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    CookingContent(
                        state = state, photoStore = photoStore, now = { now }, onClose = { closed++ },
                        onToggleIngredient = {}, onGoTo = {}, onDone = {}, onStartTimer = {}, onToggleTimer = {},
                        onAddMinute = {}, onDismissTimer = {},
                    )
                }
            }
        }
        if (rule.mainClock.autoAdvance) rule.waitForIdle() else rule.mainClock.advanceTimeByFrame()
    }

    /** Cambia el estado desde la prueba y avisa a Compose (con el reloj parado no lo haría solo). */
    private fun update(block: () -> Unit) = rule.runOnUiThread {
        block()
        Snapshot.sendApplyNotifications()
    }

    private fun progressOf(matcher: SemanticsMatcher): Float =
        rule.onNode(matcher.and(SemanticsMatcher.keyIsDefined(AnimationProgress)), useUnmergedTree = true)
            .fetchSemanticsNode().config[AnimationProgress]

    private val step1Text = hasText("Pela la calabaza y córtala en cubos.")

    @Test
    fun hecho_strikesTheStepFromLeftToRight() {
        open(reduced = false)
        rule.mainClock.autoAdvance = false
        update { state = state.copy(currentIndex = 1, doneSteps = setOf(0)) }
        rule.mainClock.advanceTimeBy(STRIKE_MS / 2L)
        val half = progressOf(step1Text)
        assertThat(half).isGreaterThan(0f)
        assertThat(half).isLessThan(1f)
        rule.mainClock.advanceTimeBy(STRIKE_MS.toLong())
        assertThat(progressOf(step1Text)).isEqualTo(1f)
    }

    @Test
    fun hecho_withReducedMotion_isStruckAtOnce() {
        open(reduced = true)
        rule.mainClock.autoAdvance = false
        update { state = state.copy(currentIndex = 1, doneSteps = setOf(0)) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(progressOf(step1Text)).isEqualTo(1f)
    }

    @Test
    fun aStepAlreadyDone_whenTheScreenOpens_isStruckWithoutAnimating() {
        state = state.copy(currentIndex = 1, doneSteps = setOf(0))
        rule.mainClock.autoAdvance = false
        open(reduced = false)
        assertThat(progressOf(step1Text)).isEqualTo(1f)
    }

    @Test
    fun listo_rainsConfettiBehind_withoutBlockingTouches_once() {
        open(reduced = false)
        rule.mainClock.autoAdvance = false
        update { state = state.copy(currentIndex = 2, doneSteps = setOf(0, 1)) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("confetti").assertExists()
        // El botón de debajo del confeti responde mientras cae.
        rule.onNodeWithText("Volver a la receta").performClick()
        rule.mainClock.advanceTimeByFrame()
        assertThat(closed).isEqualTo(1)
        rule.mainClock.advanceTimeBy(CONFETTI_MS + 200L)
        rule.onNodeWithTag("confetti").assertDoesNotExist()
        // Sigue terminado (por ejemplo, al recomponer): no vuelve a caer.
        update { state = state.copy(checkedIngredients = setOf("x")) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("confetti").assertDoesNotExist()
    }

    @Test
    fun listo_withReducedMotion_hasNoConfetti() {
        open(reduced = true)
        rule.mainClock.autoAdvance = false
        update { state = state.copy(currentIndex = 2, doneSteps = setOf(0, 1)) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("¡Listo!").assertExists()
        rule.onNodeWithTag("confetti").assertDoesNotExist()
    }

    private fun timer(endsAt: Long?, paused: Long? = null, finished: Boolean = false) =
        CookingTimer("s2", "Paso 2: Sofríe la cebolla.", 300, endsAt, paused, finished)

    @Test
    fun timerRing_showsWhatIsLeft_withTheFixedClock() {
        // 5 min en total y quedan 2:30 en el reloj fijo: el anillo está a la mitad.
        state = state.copy(currentIndex = 1, doneSteps = setOf(0), timers = mapOf("s2" to timer(endsAt = 1_150_000)))
        open(reduced = false, now = 1_000_000)
        rule.onNodeWithTag("cooking-list").performScrollToNode(hasContentDescription("Pausar el temporizador del paso 2", substring = true))
        assertThat(progressOf(hasTestTag("timer-ring-2"))).isEqualTo(0.5f)
    }

    @Test
    fun timerRing_pausedOrReduced_isStillThere_andStatic() {
        state = state.copy(currentIndex = 1, doneSteps = setOf(0), timers = mapOf("s2" to timer(endsAt = null, paused = 75_000)))
        open(reduced = true)
        rule.onNodeWithTag("cooking-list").performScrollToNode(hasContentDescription("Reanudar el temporizador del paso 2", substring = true))
        assertThat(progressOf(hasTestTag("timer-ring-2"))).isEqualTo(0.25f)
    }

    private val finishedButton = hasContentDescription("¡Tiempo! Apagar el aviso del paso 2")

    private fun beating(): Boolean =
        rule.onNode(finishedButton).fetchSemanticsNode().config[TimerBeating]

    @Test
    fun finishedTimer_beatsUntilItIsDismissed() {
        state = state.copy(currentIndex = 1, doneSteps = setOf(0), timers = mapOf("s2" to timer(endsAt = null, finished = true)))
        rule.mainClock.autoAdvance = false
        open(reduced = false)
        rule.mainClock.advanceTimeBy(100)
        assertThat(beating()).isTrue()
        // A mitad del latido el botón es más grande (escala 1,08 en su punto máximo).
        val rest = rule.onNode(finishedButton).fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.advanceTimeBy(TIMER_BEAT_MS - 100L)
        val peak = rule.onNode(finishedButton).fetchSemanticsNode().boundsInRoot.width
        assertThat(peak).isGreaterThan(rest)
        assertThat(rule.onAllNodes(hasTestTag("timer-ring-2"), useUnmergedTree = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun finishedTimer_withReducedMotion_doesNotBeat() {
        state = state.copy(currentIndex = 1, doneSteps = setOf(0), timers = mapOf("s2" to timer(endsAt = null, finished = true)))
        rule.mainClock.autoAdvance = false
        open(reduced = true)
        rule.mainClock.advanceTimeBy(100)
        assertThat(beating()).isFalse()
        val before = rule.onNode(finishedButton).fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.advanceTimeBy(TIMER_BEAT_MS.toLong())
        assertThat(rule.onNode(finishedButton).fetchSemanticsNode().boundsInRoot.width).isEqualTo(before)
    }
}
