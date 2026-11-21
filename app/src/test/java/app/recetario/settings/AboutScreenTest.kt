package app.recetario.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun about_showsOpenMeteoAttribution() {
        val opened = mutableListOf<String>()
        val uris = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uris) {
                RecetarioTheme(ThemeChoice.CALIDO) { AboutContent(versionName = "0.3.0", onBack = {}) }
            }
        }
        rule.onNodeWithText("Datos del tiempo: Open-Meteo.com, CC BY 4.0").assertExists()
        rule.onNodeWithTag("about-open-meteo").performScrollTo().performClick()
        rule.runOnIdle { assertThat(opened).containsExactly("https://open-meteo.com/") }
    }
}
