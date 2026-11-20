package app.recetario.diary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.data.PhotoStore
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** El formulario del diario en un teléfono estrecho: «Añadir foto» no se aplasta con 3 fotos. */
@RunWith(AndroidJUnit4::class)
// Con gráficos nativos el texto se mide de verdad (en el modo antiguo, «Añadir foto» mide casi nada).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
class LogEntryFormLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun threePhotos_addPhotoKeepsItsWidthOnANarrowScreen() {
        val today = LocalDate.of(2026, 11, 19)
        val draft = LogDraft(null, "r1", "Crema de calabaza", today, photos = listOf("a.jpg", "b.jpg", "c.jpg"))
        val actions = LogFormActions({}, {}, {}, {}, {}, {}, {})
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                Box(Modifier.fillMaxSize()) {
                    LogEntryPanel("Apuntar en el diario", draft, today, PhotoStore(folder.root), actions)
                }
            }
        }
        val button = rule.onNodeWithTag("diary-add-photo").getBoundsInRoot()
        // «📷 Añadir foto» mide unos 120 dp; en una Row con 3 fotos de 64 dp (320 − 36 de márgenes − 216) le
        // quedaban 68 como mucho.
        assertThat(button.width).isAtLeast(100.dp)
        assertThat(button.height).isAtLeast(48.dp)
    }
}
