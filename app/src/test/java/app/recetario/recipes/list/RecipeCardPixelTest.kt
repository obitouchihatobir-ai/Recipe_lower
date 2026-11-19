package app.recetario.recipes.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.data.PhotoStore
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

/**
 * La línea de datos de una tarjeta de la cuadrícula («Postre», «Comida · 40 min») se pinta entera: la esquina
 * redondeada de la tarjeta no puede comerse la primera letra (se vio en las capturas del recorrido de la etapa 1).
 * Se compara, píxel a píxel, con el mismo texto pintado suelto al lado.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class RecipeCardPixelTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun calido() = metaLineIsNotClipped(ThemeChoice.CALIDO)

    @Test
    fun fresco() = metaLineIsNotClipped(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = metaLineIsNotClipped(ThemeChoice.NOCTURNO)

    private fun metaLineIsNotClipped(theme: ThemeChoice) {
        val card = RecipeCardData(
            id = "r1", title = "Bizcocho", coverPhoto = null, emoji = "🍮", meta = META, taste = null,
        )
        val photoStore = PhotoStore(tmp.newFolder("photos"))
        rule.setContent {
            RecetarioTheme(theme) {
                val c = LocalRecetarioColors.current
                Row(Modifier.background(c.bg).padding(16.dp)) {
                    Box(Modifier.width(150.dp)) { RecipeGridCard(card, photoStore, onClick = {}) }
                    Spacer(Modifier.width(16.dp))
                    // El mismo texto, con el mismo estilo que la línea de datos de la tarjeta, sin tarjeta alrededor.
                    Box(Modifier.width(150.dp)) {
                        Text(
                            META,
                            color = c.muted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        val nodes = rule.onAllNodesWithText(META, useUnmergedTree = true)
        val inCard = nodes[0].captureToImage().toPixelMap()
        val alone = nodes[1].captureToImage().toPixelMap()
        assertThat(inCard.width).isEqualTo(alone.width)
        assertThat(inCard.height).isEqualTo(alone.height)

        var different = 0
        for (x in 0 until alone.width) for (y in 0 until alone.height) {
            val a = inCard[x, y]
            val b = alone[x, y]
            val diff = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))
            // Solo cuentan los píxeles muy distintos (texto contra fondo), no los matices del suavizado.
            if (diff > 96f / 255f) different++
        }
        assertWithMessage("$theme: píxeles de «$META» que la tarjeta no pinta como el texto suelto")
            .that(different).isEqualTo(0)
    }

    private companion object {
        const val META = "Postre"
    }
}
