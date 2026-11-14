package app.recetario

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.testing.waitFor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** La regla lanza [MainActivity] con `ActivityScenario` (Robolectric, sdk 35). */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsHomeAndBottomBar() {
        // Los ajustes se leen del DataStore en otro hilo: hasta entonces no se pinta nada.
        composeRule.waitFor(hasText("¿Qué cocinamos hoy?"))
        composeRule.onNodeWithText("¿Qué cocinamos hoy?").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-recipes").assertIsDisplayed()
    }
}
