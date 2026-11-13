package app.recetario.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.model.FlavorProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FlavorWheelTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun flavorPolygon_firstAxisPointsUp() {
        val pts = flavorPolygon(listOf(3, 0, 0, 0, 0), Offset(60f, 60f), 46f)
        assertThat(pts[0].x).isWithin(0.01f).of(60f)
        assertThat(pts[0].y).isWithin(0.01f).of(14f)
    }

    @Test
    fun flavorPolygon_zeroIsSixPercentOfRadius() {
        val pts = flavorPolygon(listOf(0, 0, 0, 0, 0), Offset(60f, 60f), 100f)
        assertThat(pts[0].y).isWithin(0.01f).of(54f)
    }

    @Test
    fun flavorWheel_hasSpanishContentDescription() {
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                FlavorWheel(FlavorProfile(sweet = 3, salty = 3, sour = 1, bitter = 0, spicy = 1), animate = false)
            }
        }
        rule.onNodeWithContentDescription(
            "Perfil de sabor: dulce 3 de 3, salado 3 de 3, ácido 1 de 3, amargo 0 de 3, picante 1 de 3",
        ).assertExists()
    }

    @Test
    fun servingsStepper_singularPlural() {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { ServingsStepper(value = 1, onChange = {}) } }
        rule.onNodeWithText("1 porción").assertExists()
        rule.onNodeWithContentDescription("Quitar una porción").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Añadir una porción").assertIsEnabled()
    }

    @Test
    fun servingsStepper_plural_andPlusCallsOnChange() {
        var last = 0
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { ServingsStepper(value = 4, onChange = { last = it }) } }
        rule.onNodeWithText("4 porciones").assertExists()
        rule.onNodeWithContentDescription("Añadir una porción").performClick()
        assertThat(last).isEqualTo(5)
    }

    @Test
    fun photoOrPlaceholder_missingFile_showsEmoji() {
        val store = PhotoStore(File(System.getProperty("java.io.tmpdir"), "no-existe-recetario"))
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) { PhotoOrPlaceholder("nada.jpg", store, "🍲") }
        }
        rule.onNodeWithText("🍲").assertExists()
    }

    @Test
    fun tagPillAndCheck_render() {
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                TagPill("Rápida", accent = true)
                AnimatedCheck(checked = true)
            }
        }
        rule.onNodeWithText("Rápida").assertExists()
    }

    @Test
    fun labelOrigin_keepsEveryLabelInsideCanvas() {
        val canvas = Size(120f, 120f)
        val label = Size(28f, 10f)
        for (i in 0 until 5) {
            val angle = -Math.PI / 2 + i * 2 * Math.PI / 5
            val o = labelOrigin(Offset(60f, 60f), angle, 40.8f + 10f, label, canvas)
            assertThat(o.x).isAtLeast(0f)
            assertThat(o.y).isAtLeast(0f)
            assertThat(o.x + label.width).isAtMost(120f)
            assertThat(o.y + label.height).isAtMost(120f)
        }
        // Un radio exagerado también queda dentro.
        val far = labelOrigin(Offset(60f, 60f), -Math.PI / 2, 200f, label, canvas)
        assertThat(far.y).isAtLeast(0f)
    }

    @Test
    fun entrance_playedKeyIsInstant_andNewKeyAnimates() {
        val tracker = object : EntranceTracker() {
            override fun hasPlayed(key: Any) = key == "visto"
        }
        assertThat(entranceIsInstant(false, "visto", tracker)).isTrue()
        assertThat(entranceIsInstant(false, "nuevo", tracker)).isFalse()
        assertThat(entranceIsInstant(false, "nuevo", null)).isFalse()
        assertThat(entranceIsInstant(true, null, null)).isTrue()
    }

    @Test
    fun entranceTracker_remembersPlayedKeys() {
        val t = EntranceTracker()
        assertThat(t.hasPlayed("a")).isFalse()
        t.markPlayed("a")
        assertThat(t.hasPlayed("a")).isTrue()
    }
}
