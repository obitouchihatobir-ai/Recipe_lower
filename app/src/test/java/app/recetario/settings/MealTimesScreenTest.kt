package app.recetario.settings

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import app.recetario.today.MealTimes
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class MealTimesScreenTest {
    @get:Rule(order = 0)
    val closeDatabase = object : ExternalResource() {
        override fun after() {
            container?.database?.close()
        }
    }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private var container: AppContainer? = null

    private fun launch(): AppContainer {
        val c = installTestContainer().also { container = it }
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { MealTimesRoute(c, onBack = {}) } }
        rule.waitFor(hasTestTag("meal-BREAKFAST-end"))
        return c
    }

    private fun AppContainer.times() = runBlocking { settingsRepository.mealTimes.first() }

    @Test
    fun mealTimes_overlap_rejected() {
        val c = launch()
        rule.waitFor(hasText("De 11:00 a 13:00 cuenta como comida."))
        // El desayuno acaba más tarde, media hora cada vez, hasta tocar la comida (13:00): se guarda.
        for (expected in listOf("11:30", "12:00", "12:30", "13:00")) {
            rule.onNodeWithTag("meal-BREAKFAST-end-plus").performClick()
            rule.waitUntil(5_000) { c.times().breakfast.end.toString() == expected }
            // La pantalla ya muestra la hora nueva: el siguiente toque parte de ella.
            rule.waitFor(hasText(expected) and hasAnyAncestor(hasTestTag("meal-BREAKFAST-end")))
        }
        rule.waitForGone(hasText("De 11:00 a 13:00 cuenta como comida."))
        // Media hora más y se solapan: se avisa y no se guarda.
        rule.onNodeWithTag("meal-BREAKFAST-end-plus").performClick()
        rule.waitFor(hasText("El desayuno y la comida se solapan."))
        assertThat(c.times().breakfast.end).isEqualTo(LocalTime.of(13, 0))
    }

    @Test
    fun mealTimes_crossingMidnight_rejected() {
        val c = launch()
        rule.onNodeWithTag("meal-DINNER-end-plus").performScrollTo().performClick()
        rule.waitUntil(5_000) { c.times().dinner.end == LocalTime.of(23, 30) }
        rule.waitFor(hasText("23:30") and hasAnyAncestor(hasTestTag("meal-DINNER-end")))
        rule.onNodeWithTag("meal-DINNER-end-plus").performClick()
        rule.waitFor(hasText("La cena no puede pasar de la medianoche."))
        assertThat(c.times().dinner.end).isEqualTo(LocalTime.of(23, 30))
        assertThat(c.times().breakfast).isEqualTo(MealTimes().breakfast)
    }
}
