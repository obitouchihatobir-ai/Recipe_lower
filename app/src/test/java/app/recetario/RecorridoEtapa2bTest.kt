package app.recetario

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.backup.FakeDocuments
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.pairing.DISLIKE_LABEL
import app.recetario.pairing.LIKE_LABEL
import app.recetario.pairing.pairingData
import app.recetario.pairing.ratings
import app.recetario.pairing.scrollUntil
import app.recetario.pairing.suggestPairings
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.forecastJson
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import app.recetario.weather.Place
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recorrido de cierre de la entrega 2B sobre la app de verdad ([MainActivity]), una vez por tema:
 * 1. tres recetas: un principal caliente (caldo), una bebida (agua de jamaica) y un postre (arroz con leche);
 * 2. con un tiempo de mentira de 9 °C y lluvia, Hoy propone el caldo: «Hace 9 °C y llueve…»;
 * 3. «Otra idea» (con un solo principal, sigue el caldo);
 * 4. el detalle del caldo con su sección Maridaje;
 * 5. 👎 a la pareja con el agua de jamaica, que desaparece;
 * 6. la pestaña Maridaje partiendo de la bebida: el caldo no sale;
 * 7. la rueda comparativa de la bebida con el postre;
 * 8. el menú completo (la bebida ya no es el agua de jamaica);
 * 9. el historial, donde la 👎 pasa a 👍.
 *
 * Nunca hay red: el tiempo es de [FakeOpenMeteo] y la ciudad es inventada. Espera siempre a un estado (nunca un tiempo
 * fijo) y guarda una captura de cada paso en `app/build/capturas/recorrido-2b/`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class RecorridoEtapa2bTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    /** Sábado 21 de noviembre de 2026, 20:30 en Madrid: cena. */
    private val clock = Clock.fixed(Instant.parse("2026-11-21T19:30:00Z"), ZoneId.of("Europe/Madrid"))
    private val weather = FakeOpenMeteo(
        forecast = forecastJson(LocalDate.of(2026, 11, 21), apparent = 9.2, precipitation = 1.4, weatherCode = 61),
    )
    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var themeName: String

    @After
    fun tearDown() {
        // Primero se cierra la actividad (y su composición) y después la base de datos.
        scenario?.close()
        if (::container.isInitialized) container.database.close()
    }

    @Test
    fun calido() = walk(ThemeChoice.CALIDO)

    @Test
    fun fresco() = walk(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = walk(ThemeChoice.NOCTURNO)

    private fun walk(theme: ThemeChoice) {
        themeName = theme.name.lowercase()
        container = installTestContainer(clock = clock, documents = FakeDocuments(), openMeteo = weather)
        runBlocking {
            with(container.settingsRepository) {
                setTheme(theme)
                setAuto(false)
                // Una ciudad inventada (nunca la ubicación del usuario).
                setCity(Place(CITY, "Provincia de Prueba", "Tierra de Prueba", 40.0, -3.5))
            }
        }
        threeRecipes()

        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))

        today()
        detailPairing()
        pairingTabFromTheDrink()
        compareWheel()
        fullMenu()
        history()
    }

    // ---------------------------------------------------------------- 1. tres recetas

    private fun threeRecipes() = runBlocking {
        val repo = container.recipeRepository
        repo.save(
            sampleRecipe(
                CALDO, "Caldo de pollo", minutes = 45,
                flavor = FlavorProfile(salty = 2, intensity = Intensity.MEDIUM),
                serveTemp = ServeTemp.HOT,
                ingredients = listOf(
                    ingredient("c1", "Pollo", 500.0, MeasureUnit.GRAM),
                    ingredient("c2", "Zanahoria", 2.0, MeasureUnit.PIECE),
                    ingredient("c3", "Cilantro", 1.0, MeasureUnit.PIECE),
                ),
            ).copy(course = Course.MAIN),
        )
        repo.save(
            sampleRecipe(
                JAMAICA, "Agua de jamaica de la casa", DefaultCategories.DRINK_ID, minutes = 15,
                flavor = FlavorProfile(sweet = 2, sour = 3, intensity = Intensity.LIGHT),
                serveTemp = ServeTemp.COLD,
            ).copy(alcoholic = false),
        )
        repo.save(
            sampleRecipe(
                ARROZ, "Arroz con leche", DefaultCategories.DESSERT_ID, minutes = 60,
                flavor = FlavorProfile(sweet = 3, rich = true),
                serveTemp = ServeTemp.COLD,
            ),
        )
    }

    // ---------------------------------------------------------------- 2 y 3. Hoy con lluvia y «Otra idea»

    /** El texto del nodo [tag]; null mientras no hay uno solo (durante el fundido entre dos menús hay dos). */
    private fun textOf(tag: String): String? =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
            ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }

    private fun today() {
        rule.waitFor(hasTestTag("today-card"))
        // El tiempo llega en segundo plano: el porqué cambia cuando llega.
        rule.waitFor(hasTestTag("today-weather"))
        rule.onNodeWithTag("today-weather").assert(hasContentDescription("El tiempo ahora: lluvia, 9 °C en $CITY"))
        rule.waitUntil(5_000) { textOf("today-reason") == "Hace 9 °C y llueve: algo caliente y reconfortante" }
        assertThat(textOf("today-main-title")).isEqualTo("Caldo de pollo")
        assertThat(textOf("today-eyebrow")).isEqualTo("Cena de hoy · Otoño")
        assertThat(weather.forecastRequests).isNotEmpty()
        save("paso2-hoy-lluvia")

        // «Otra idea»: con un solo principal, sigue el caldo (y la tarjeta no se rompe).
        rule.onNodeWithTag("today-another").performClick()
        rule.waitUntil(5_000) { textOf("today-main-title") == "Caldo de pollo" }
        assertThat(textOf("today-reason")).startsWith("Hace 9 °C y llueve")
        save("paso3-otra-idea")
    }

    // ---------------------------------------------------------------- 4 y 5. el detalle y una 👎

    private fun detailPairing() {
        rule.onNodeWithTag("today-main").performClick()
        rule.waitFor(hasTestTag("detail-cook"))

        val data = pairingData(container)
        val caldo = data.find("RECIPE", CALDO)!!
        val expected = suggestPairings(caldo, data.all, data.ratings, 3, data.weights)
        // Primero las recetas propias: el agua de jamaica.
        assertThat(expected.first().item.id).isEqualTo(JAMAICA)

        rule.scrollUntil("detail-list", hasTestTag("detail-pairing"))
        rule.onNodeWithTag("detail-pairing", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Maridaje")))
        val row = "pairing-row-RECIPE:$JAMAICA"
        rule.scrollUntil("detail-list", hasTestTag(row))
        rule.onNodeWithTag(row, useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(expected.first().reasons.first(), substring = true)))
        save("paso4-detalle-maridaje")

        // 👎: la pareja desaparece del detalle y queda guardada.
        rule.onNode(hasContentDescription(DISLIKE_LABEL) and hasAnyAncestor(hasTestTag(row))).performClick()
        rule.waitForGone(hasTestTag(row))
        assertThat(ratings(container)).containsExactly(PAIR, false)
        rule.scrollUntil("detail-list", hasTestTag("detail-pairing"))
        save("paso5-sin-la-pareja")

        // «Volver» está en la cabecera: primero se sube al principio de la lista.
        rule.onNodeWithTag("detail-list").performScrollToIndex(0)
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.waitForGone(hasTestTag("detail-cook"))
    }

    // ---------------------------------------------------------------- 6. la pestaña desde la bebida

    private fun pairingTabFromTheDrink() {
        rule.onNodeWithTag("tab-pairing").performClick()
        rule.waitFor(hasTestTag("pairing-search"))
        rule.onNodeWithTag("pairing-search").performTextInput("jamaica")
        val match = "pairing-match-RECIPE:$JAMAICA"
        rule.waitFor(hasTestTag(match))
        rule.onNodeWithTag("pairing-list").performScrollToNode(hasTestTag(match))
        rule.onNodeWithTag(match).performClick()
        rule.waitFor(hasTestTag("pairing-subject"))

        // El postre sí combina; el caldo, con su 👎, no sale en toda la lista.
        rule.scrollUntil("pairing-list", hasTestTag("pairing-row-RECIPE:$ARROZ"))
        assertThrows(AssertionError::class.java) {
            rule.onNodeWithTag("pairing-list").performScrollToNode(hasTestTag("pairing-row-RECIPE:$CALDO"))
        }
        rule.onNodeWithTag("pairing-list").performScrollToIndex(0)
        save("paso6-pestana-bebida")
    }

    // ---------------------------------------------------------------- 7. la rueda comparativa

    private fun compareWheel() {
        val row = "pairing-row-RECIPE:$ARROZ"
        rule.scrollUntil("pairing-list", hasTestTag(row))
        rule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(row)) and hasText("Arroz con leche", substring = true))
            .performClick()
        rule.waitFor(hasTestTag("compare-wheel"))
        rule.waitFor(hasText("Agua de jamaica de la casa + Arroz con leche"))
        rule.onNodeWithTag("compare-wheel").assert(hasContentDescription("Arroz con leche", substring = true))
        save("paso7-rueda")

        rule.onNodeWithTag("settings-back").performClick()
        rule.waitFor(hasTestTag("pairing-subject"))
    }

    // ---------------------------------------------------------------- 8. el menú completo

    private fun fullMenu() {
        rule.onNodeWithTag("pairing-list").performScrollToIndex(0)
        rule.onNodeWithTag("pairing-full-menu").performClick()
        rule.waitFor(hasTestTag("full-menu-reason"))
        rule.onNodeWithTag("full-menu-Principal", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Caldo de pollo")))
        // Con la 👎, la bebida ya no es el agua de jamaica.
        rule.onNode(hasTestTag("full-menu-Bebida") and hasAnyDescendant(hasText("Agua de jamaica de la casa")), useUnmergedTree = true)
            .assertDoesNotExist()
        save("paso8-menu-completo")

        rule.onNodeWithTag("settings-back").performClick()
        rule.waitFor(hasTestTag("pairing-full-menu"))
    }

    // ---------------------------------------------------------------- 9. el historial

    private fun history() {
        rule.onNodeWithTag("pairing-history").performClick()
        val row = "history-$PAIR"
        rule.waitFor(hasTestTag(row))
        rule.onNode(hasContentDescription(DISLIKE_LABEL) and hasAnyAncestor(hasTestTag(row))).assertIsSelected()
        save("paso9-historial-antes")

        rule.onNode(hasContentDescription(LIKE_LABEL) and hasAnyAncestor(hasTestTag(row))).performClick()
        rule.waitUntil(5_000) { ratings(container)[PAIR] == true }
        rule.waitFor(hasContentDescription(LIKE_LABEL) and hasAnyAncestor(hasTestTag(row)) and isSelected())
        save("paso9-historial-cambiado")
    }

    private fun save(name: String, settleMillis: Long = 1_500) {
        // La entrada escalonada y la rueda esperan con delay(): se adelanta el reloj para capturar el estado final.
        rule.mainClock.advanceTimeBy(settleMillis)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("user.dir"), "build/capturas/recorrido-2b").apply { mkdirs() }
        File(dir, "$name-$themeName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val CITY = "Villa Sazón"
        const val CALDO = "caldo"
        const val JAMAICA = "jamaica"
        const val ARROZ = "arroz-leche"
        const val PAIR = "RECIPE:caldo|RECIPE:jamaica"
    }
}
