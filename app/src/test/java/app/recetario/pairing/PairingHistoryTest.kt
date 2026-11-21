package app.recetario.pairing

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Historial: las parejas valoradas, con 👍/👎 que se cambian o se quitan (§7.5). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class PairingHistoryTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private var compared: RatedPairView? = null

    @Before
    fun setUp() {
        container = installTestContainer()
        seedPairingRecipes(container)
    }

    @Test
    fun history_changeRating() {
        val data = pairingData(container)
        val mole = data.find("RECIPE", "mole")!!
        val jamaica = data.find("CATALOG", "cat-agua-jamaica")!!
        val key = pairKey(mole, jamaica)
        runBlocking { container.pairingRepository.rate(mole, jamaica, false) }

        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                PairingHistoryRoute(container, onBack = {}, onCompare = { compared = it })
            }
        }
        val row = "history-$key"
        rule.waitFor(hasTestTag(row))
        rule.waitFor(hasText("Agua de jamaica + Mole poblano") and hasAnyAncestor(hasTestTag(row)))
        rule.onNode(hasContentDescription(DISLIKE_LABEL) and hasAnyAncestor(hasTestTag(row))).assertIsSelected()
        rule.onNode(hasContentDescription(LIKE_LABEL) and hasAnyAncestor(hasTestTag(row))).assertIsNotSelected()

        // 👎 → 👍
        rule.onNode(hasContentDescription(LIKE_LABEL) and hasAnyAncestor(hasTestTag(row))).performClick()
        rule.waitUntil(5_000) { ratings(container)[key] == true }
        rule.waitFor(hasText("Te gusta") and hasAnyAncestor(hasTestTag(row)))
        rule.onNode(hasContentDescription(LIKE_LABEL) and hasAnyAncestor(hasTestTag(row))).assertIsSelected()

        // Tocar la pareja abre la rueda comparativa.
        rule.onNode(hasText("Agua de jamaica + Mole poblano") and hasAnyAncestor(hasTestTag(row))).performClick()
        assertThat(compared?.pairKey).isEqualTo(key)

        // Quitar la valoración la borra del historial.
        rule.onNode(hasContentDescription("Quitar valoración") and hasAnyAncestor(hasTestTag(row))).performClick()
        rule.waitUntil(5_000) { ratings(container).isEmpty() }
        rule.waitFor(hasText(HISTORY_EMPTY_TEXT))
    }
}
