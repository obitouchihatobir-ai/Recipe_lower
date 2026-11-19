package app.recetario.core.ui.motion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.components.AnimatedCheck
import app.recetario.core.ui.components.SERVINGS_ROLL_MS
import app.recetario.core.ui.components.ServingsStepper
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Animaciones compartidas (Task 18): porciones, casilla, aviso «Receta guardada» y rebote de la tarjeta. */
@RunWith(AndroidJUnit4::class)
class MotionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(reduced: Boolean, content: @Composable () -> Unit) = rule.setContent {
        RecetarioTheme(ThemeChoice.CALIDO) {
            CompositionLocalProvider(LocalReducedMotion provides reduced) { content() }
        }
    }

    private fun progressOf(matcher: SemanticsMatcher): Float =
        rule.onNode(matcher.and(SemanticsMatcher.keyIsDefined(AnimationProgress)), useUnmergedTree = true)
            .fetchSemanticsNode().config[AnimationProgress]

    // ------------------------------------------------------------------ porciones

    private fun servingsRoll(reduced: Boolean) {
        var servings by mutableIntStateOf(4)
        show(reduced) { ServingsStepper(servings, onChange = { servings = it }) }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Añadir una porción").performClick()
        // A mitad del giro hay dos números compuestos, pero TalkBack solo tiene el texto final.
        rule.mainClock.advanceTimeBy(SERVINGS_ROLL_MS / 2L)
        val texts = rule.onAllNodesWithText("porci", substring = true).fetchSemanticsNodes()
        assertThat(texts).hasSize(1)
        rule.onNode(hasText("5 porciones")).assertExists()
        rule.onNode(hasText("4 porciones")).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(SERVINGS_ROLL_MS.toLong())
        rule.onNode(hasText("5 porciones")).assertExists()
    }

    @Test
    fun servings_rollLikeACounter_butAnnounceOnlyTheFinalText() = servingsRoll(reduced = false)

    @Test
    fun servings_withReducedMotion_changeAtOnce_andAnnounceTheFinalText() = servingsRoll(reduced = true)

    // ------------------------------------------------------------------ casilla

    @Test
    fun check_fillsFromTheCentre() {
        var checked by mutableStateOf(false)
        show(reduced = false) { AnimatedCheck(checked, Modifier.testTag("check")) }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { checked = true }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(progressOf(hasTestTag("check"))).isLessThan(1f)
        rule.mainClock.advanceTimeBy(600)
        assertThat(progressOf(hasTestTag("check"))).isEqualTo(1f)
    }

    @Test
    fun check_withReducedMotion_isFilledAtOnce() {
        var checked by mutableStateOf(false)
        show(reduced = true) { AnimatedCheck(checked, Modifier.testTag("check")) }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { checked = true }
        rule.mainClock.advanceTimeByFrame()
        assertThat(progressOf(hasTestTag("check"))).isEqualTo(1f)
        rule.runOnUiThread { checked = false }
        rule.mainClock.advanceTimeByFrame()
        assertThat(progressOf(hasTestTag("check"))).isEqualTo(0f)
    }

    // ------------------------------------------------------------------ aviso «Receta guardada»

    @Test
    fun savedToast_drawsItsCheck_andLetsTouchesThrough() {
        val celebration = SaveCelebration()
        var taps = 0
        show(reduced = false) {
            Box(Modifier.fillMaxSize().testTag("below").clickable { taps++ }) {
                SavedToast(celebration, Modifier.align(Alignment.Center))
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { celebration.recipeSaved("r1") }
        rule.mainClock.advanceTimeBy(SAVED_CHECK_MS / 2L)
        rule.onNodeWithTag("saved-toast").assertExists()
        assertThat(progressOf(hasAnyAncestor(hasTestTag("saved-toast")))).isLessThan(1f)
        rule.mainClock.advanceTimeBy(SAVED_CHECK_MS.toLong())
        assertThat(progressOf(hasAnyAncestor(hasTestTag("saved-toast")))).isEqualTo(1f)
        // Un toque justo encima del aviso llega a lo que hay debajo.
        val toast = rule.onNodeWithTag("saved-toast").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { click(toast.center) }
        rule.mainClock.advanceTimeByFrame()
        assertThat(taps).isEqualTo(1)
        rule.mainClock.advanceTimeBy(SAVED_TOAST_MS + 500)
        rule.onNodeWithTag("saved-toast").assertDoesNotExist()
    }

    @Test
    fun savedToast_withReducedMotion_showsTheCheckAlreadyDrawn() {
        val celebration = SaveCelebration()
        show(reduced = true) { SavedToast(celebration) }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { celebration.recipeSaved("r1") }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(progressOf(hasAnyAncestor(hasTestTag("saved-toast")))).isEqualTo(1f)
        rule.onNode(hasText("Receta guardada")).assertExists()
    }

    // ------------------------------------------------------------------ rebote de la tarjeta

    private fun cardWidth(): Float = rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.width

    @Test
    fun savedCard_bouncesOnlyOnce() {
        val celebration = SaveCelebration()
        var shown by mutableStateOf(true)
        show(reduced = false) {
            CompositionLocalProvider(LocalSaveCelebration provides celebration) {
                if (shown) Box(Modifier.savedBounce("r1").size(100.dp).testTag("card"))
            }
        }
        val full = cardWidth()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { celebration.recipeSaved("r1") }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(cardWidth()).isLessThan(full)
        assertThat(cardWidth()).isAtLeast(full * SAVED_BOUNCE_FROM - 1f)
        rule.mainClock.advanceTimeBy(1_500)
        assertThat(cardWidth()).isWithin(0.5f).of(full)
        assertThat(celebration.bounceId).isNull()
        // Vuelve a componerse (por ejemplo, al volver a la pantalla): ya no rebota.
        rule.runOnUiThread { shown = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { shown = true }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(cardWidth()).isWithin(0.5f).of(full)
    }

    @Test
    fun savedCard_otherRecipes_doNotBounce_andReducedMotionNeverMoves() {
        val celebration = SaveCelebration()
        show(reduced = true) {
            CompositionLocalProvider(LocalSaveCelebration provides celebration) {
                Box(Modifier.savedBounce("r1").size(100.dp).testTag("card"))
                Box(Modifier.savedBounce("r2").size(100.dp).testTag("other"))
            }
        }
        val full = cardWidth()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { celebration.recipeSaved("r1") }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(cardWidth()).isEqualTo(full)
        // La tarjeta la reclamó (aunque sin moverse): no rebotará más tarde.
        assertThat(celebration.bounceId).isNull()
    }

    @Test
    fun savedCard_thatNeverShowedUp_doesNotBounceAfterTheToast() {
        val celebration = SaveCelebration()
        show(reduced = false) { SavedToast(celebration) }
        rule.runOnUiThread { celebration.recipeSaved("r9") }
        rule.mainClock.advanceTimeBy(SAVED_TOAST_MS + 500)
        assertThat(celebration.bounceId).isNull()
    }
}
