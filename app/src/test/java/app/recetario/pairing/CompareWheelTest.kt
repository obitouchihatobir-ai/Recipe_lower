package app.recetario.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** La rueda comparativa: dos ruedas que crecen una después de otra, sin animación con movimiento reducido (§8). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CompareWheelTest {
    @get:Rule
    val rule = createComposeRule()

    private val mole = PairingItem(
        kind = "RECIPE", id = "mole", name = "Mole poblano", role = PairRole.FOOD, course = null, alcoholic = null,
        flavor = FlavorProfile(sweet = 1, spicy = 2, intensity = Intensity.INTENSE, rich = true), serveTemp = ServeTemp.HOT,
    )
    private val jamaica = Catalog.load().first { it.id == "cat-agua-jamaica" }
    private val tinto = Catalog.load().first { it.role == PairRole.DRINK && it.id != "cat-agua-jamaica" }

    private fun progress(description: String): List<Float> =
        rule.onNode(hasContentDescription(description)).fetchSemanticsNode().config[CompareWheelProgress]

    @Test
    fun compareWheel_drawsBoth_reducedMotionInstant() {
        var taps = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            RecetarioTheme(ThemeChoice.CALIDO) {
                Column {
                    // Con animación, encima de algo que se puede tocar.
                    Box {
                        Box(Modifier.size(230.dp).testTag("under").clickable { taps++ })
                        CompositionLocalProvider(LocalReducedMotion provides false) {
                            CompareWheel(mole.flavor, jamaica.flavor, "animada")
                        }
                    }
                    CompositionLocalProvider(LocalReducedMotion provides true) {
                        CompareWheel(mole.flavor, tinto.flavor, "quieta")
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        // Sin animaciones: las dos ruedas enteras desde el primer fotograma.
        assertThat(progress("quieta")).containsExactly(1f, 1f).inOrder()
        val start = progress("animada")
        assertThat(start[0]).isLessThan(1f)
        assertThat(start[1]).isEqualTo(0f)

        // Primero crece una…
        rule.mainClock.advanceTimeBy(WHEEL_DRAW_MS / 2L)
        val half = progress("animada")
        assertThat(half[0]).isGreaterThan(0f)
        assertThat(half[0]).isLessThan(1f)
        assertThat(half[1]).isEqualTo(0f)

        // Mientras se dibuja, el toque pasa a lo de debajo.
        rule.onNode(hasContentDescription("animada")).performClick()
        assertThat(taps).isEqualTo(1)

        // …y después la otra.
        rule.mainClock.advanceTimeBy(WHEEL_DRAW_MS.toLong())
        val second = progress("animada")
        assertThat(second[0]).isEqualTo(1f)
        assertThat(second[1]).isGreaterThan(0f)

        rule.mainClock.advanceTimeBy(WHEEL_DRAW_MS * 2L)
        assertThat(progress("animada")).containsExactly(1f, 1f).inOrder()
    }

    @Test
    fun compareWheel_talkbackDescription() {
        val description = compareWheelDescription(mole, jamaica)
        val j = jamaica.flavor
        assertThat(description).contains("Mole poblano: dulce 1 de 3, salado 0 de 3, ácido 0 de 3, amargo 0 de 3, picante 2 de 3")
        assertThat(description).contains(
            "Agua de jamaica: dulce ${j.sweet} de 3, salado ${j.salty} de 3, ácido ${j.sour} de 3, amargo ${j.bitter} de 3, picante ${j.spicy} de 3",
        )

        val score = scorePair(mole, jamaica)
        var rated: Boolean? = null
        rule.setContent {
            RecetarioTheme(ThemeChoice.NOCTURNO) {
                CompareContent(
                    loaded = true, a = mole, b = jamaica, score = score, liked = null, ratable = true,
                    onRate = { rated = it }, onBack = {},
                )
            }
        }
        rule.onNodeWithTag("compare-wheel").assert(hasContentDescription(description))
        // La leyenda nombra a los dos y cómo es su línea; las frases dicen por qué.
        rule.onNode(hasText("Mole poblano")).assertExists()
        rule.onNode(hasText("Línea discontinua", substring = true)).assertExists()
        for (reason in score.reasons) rule.onNode(hasText(reason) and hasAnyAncestor(hasTestTag("compare-reasons"))).assertExists()
        rule.onNode(hasContentDescription(LIKE_LABEL)).performClick()
        assertThat(rated).isTrue()
    }

    @Test
    fun compareWheel_catalogPair_hasNoRating() {
        val other = Catalog.load().first { it.role == PairRole.FOOD }
        rule.setContent {
            RecetarioTheme(ThemeChoice.FRESCO) {
                CompareContent(
                    loaded = true, a = jamaica, b = other, score = scorePair(jamaica, other), liked = null,
                    ratable = ratableKey(jamaica, other) != null, onRate = {}, onBack = {},
                )
            }
        }
        rule.onNodeWithTag("compare-wheel").assertExists()
        rule.onAllNodes(hasContentDescription(LIKE_LABEL)).assertCountEquals(0)
    }

    @Test
    fun compareRoute_observesTheDataOnce() {
        val c = installTestContainer()
        try {
            seedPairingRecipes(c)
            val calls = AtomicInteger()
            val counting = object : PairingRepository by c.pairingRepository {
                override fun observeData(): Flow<PairingData> {
                    calls.incrementAndGet()
                    return c.pairingRepository.observeData()
                }
            }
            rule.setContent {
                RecetarioTheme(ThemeChoice.CALIDO) {
                    CompareRoute(c, "RECIPE" to "mole", "RECIPE" to "limonada", onBack = {}, repository = counting)
                }
            }
            rule.waitFor(hasTestTag("compare-wheel"))
            rule.waitForIdle()
            // Recomponer (al llegar los datos) no vuelve a crear el flujo ni a suscribirse.
            assertThat(calls.get()).isEqualTo(1)
        } finally {
            c.database.close()
        }
    }
}
