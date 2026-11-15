package app.recetario

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.settings.AboutContent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** La marca «Sazón»: nombre en el lanzador y en «Acerca de», e icono adaptativo con sus tres capas. */
@RunWith(AndroidJUnit4::class)
class BrandingTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun appName_isSazon_inTheLauncher() {
        assertThat(context.getString(R.string.app_name)).isEqualTo("Sazón")
        assertThat(context.applicationInfo.loadLabel(context.packageManager).toString()).isEqualTo("Sazón")
    }

    @Test
    fun about_showsTheAppName() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AboutContent(versionName = "0.1.0", onBack = {}) } }
        rule.onNodeWithText("Sazón").assertIsDisplayed()
        rule.onNodeWithText("Versión 0.1.0").assertIsDisplayed()
    }

    /** Capa → nombre del recurso al que apunta, leídos del XML compilado del icono. */
    private fun layers(id: Int): Map<String, String> {
        val parser = context.resources.getXml(id)
        val result = mutableMapOf<String, String>()
        var root: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (root == null) {
                root = parser.name
                continue
            }
            val drawable = parser.getAttributeResourceValue(ANDROID_NS, "drawable", 0)
            result[parser.name] = if (drawable == 0) "" else
                "${context.resources.getResourceTypeName(drawable)}/${context.resources.getResourceEntryName(drawable)}"
        }
        assertThat(root).isEqualTo("adaptive-icon")
        return result
    }

    @Test
    fun adaptiveIcon_hasBackgroundForegroundAndMonochrome() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            assertWithMessage(context.resources.getResourceEntryName(id)).that(layers(id)).containsExactly(
                "background", "mipmap/ic_launcher_background",
                "foreground", "mipmap/ic_launcher_foreground",
                "monochrome", "mipmap/ic_launcher_monochrome",
            )
        }
    }

    @Test
    fun adaptiveIcon_inflatesWithItsThreeLayers() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = context.getDrawable(id)
            assertThat(icon).isInstanceOf(AdaptiveIconDrawable::class.java)
            icon as AdaptiveIconDrawable
            assertThat(icon.background).isNotNull()
            assertThat(icon.foreground).isNotNull()
            assertThat(icon.monochrome).isNotNull()
        }
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
