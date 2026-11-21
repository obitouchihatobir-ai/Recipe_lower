package app.recetario.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelected
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.model.Step
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.forecastJson
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import app.recetario.testing.waitFor
import app.recetario.weather.Place
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Un dueño del ciclo de vida que la prueba mueve a mano (empieza en RESUMED). */
private class TestLifecycleOwner : LifecycleOwner {
    val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

/**
 * La tarjeta Hoy de Inicio (diseño 2, §7.3). El reloj de las pruebas es el viernes 13 de noviembre de 2026 a las
 * 21:40 (cena, entre semana); sin ubicación, la estación es la de secas. Nunca hay red: el tiempo es de
 * [FakeOpenMeteo] y las ciudades son capitales conocidas.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class TodayCardTest {
    @get:Rule
    val rule = createComposeRule()

    private var container: AppContainer? = null

    @After
    fun tearDown() {
        container?.database?.close()
    }

    private fun install(clock: Clock = testClock, openMeteo: FakeOpenMeteo? = null) =
        installTestContainer(clock = clock, openMeteo = openMeteo).also { container = it }

    private fun AppContainer.save(vararg recipes: Recipe) = runBlocking { recipes.forEach { recipeRepository.save(it) } }

    private fun launch(c: AppContainer) {
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(c) } }
        rule.waitFor(hasTestTag("home-diary"))
    }

    /** El texto del nodo [tag]; null mientras no hay uno solo (durante el fundido entre dos menús hay dos). */
    private fun textOf(tag: String): String? =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
            ?.config?.get(SemanticsProperties.Text)?.joinToString { it.text }

    private fun titleOf(tag: String): String {
        rule.waitUntil(5_000) { textOf(tag) != null }
        return textOf(tag)!!
    }

    private val soup = sampleRecipe("sopa", "Crema de calabaza", serveTemp = ServeTemp.HOT, minutes = 30)
    private val chocolate = sampleRecipe("choco", "Chocolate caliente", DefaultCategories.DRINK_ID, serveTemp = ServeTemp.HOT, minutes = 10)
    private val flan = sampleRecipe("flan", "Flan casero", DefaultCategories.DESSERT_ID, minutes = 50)

    @Test
    fun todayCard_showsMainDrinkDessert_andReason() {
        val c = install()
        c.save(soup, chocolate, flan)
        launch(c)

        rule.waitFor(hasTestTag("today-card"))
        rule.onNodeWithTag("today-eyebrow").assertExists()
        assertThat(titleOf("today-eyebrow")).isEqualTo("Cena de hoy · Temporada de secas")
        assertThat(titleOf("today-main-title")).isEqualTo("Crema de calabaza")
        assertThat(titleOf("today-drink-title")).isEqualTo("Chocolate caliente")
        assertThat(titleOf("today-dessert-title")).isEqualTo("Flan casero")
        // Sin clima, viernes por la noche y una receta de 30 min: el porqué de la hora.
        assertThat(titleOf("today-reason")).isEqualTo("Cena de entre semana: algo rápido")
        rule.onNodeWithText("Principal · 30 min", useUnmergedTree = true).assertExists()
        // Los emojis se leen por el nombre de su categoría.
        rule.onNode(hasContentDescription("Bebidas"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("today-start").assertExists()
        rule.onNodeWithTag("today-another").assertExists()
    }

    @Test
    fun todayCard_anotherIdea_changesMain() {
        val c = install()
        c.save(
            soup,
            sampleRecipe("tacos", "Tacos de canasta", minutes = 25),
            sampleRecipe("arroz", "Arroz a la mexicana", minutes = 35),
        )
        launch(c)
        rule.waitFor(hasTestTag("today-main-title"))

        val seen = mutableListOf(titleOf("today-main-title"))
        repeat(2) {
            rule.onNodeWithTag("today-another").performClick()
            rule.waitUntil(5_000) { textOf("today-main-title").let { it != null && it != seen.last() } }
            seen += titleOf("today-main-title")
        }
        // Tres principales: una vuelta entera sin repetir.
        assertThat(seen.toSet()).hasSize(3)
    }

    @Test
    fun todayCard_emptyStates() {
        // Sin recetas: la invitación a apuntar la primera.
        val c = install()
        launch(c)
        rule.waitFor(hasText(TODAY_EMPTY_TEXT))
        rule.onNodeWithTag("today-card").assertDoesNotExist()
        rule.onNodeWithText("+ Nueva receta").performClick()
        rule.waitFor(hasTestTag("editor-title"))
    }

    @Test
    fun todayCard_emptyStates_withoutDrink() {
        val c = install()
        c.save(soup)
        launch(c)

        rule.waitFor(hasTestTag("today-main-title"))
        rule.onNodeWithText("Aún no tienes bebidas", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Aún no tienes postres", useUnmergedTree = true).assertExists()
        // TalkBack lee el enlace con su contexto, no solo «+ Crear una».
        rule.onNodeWithTag("today-create-drink").assert(hasContentDescription("Crear una bebida"))
        rule.onNodeWithTag("today-create-dessert").assert(hasContentDescription("Crear un postre"))
        rule.onNodeWithTag("today-create-drink").performClick()
        // El editor se abre con Bebidas ya elegida.
        rule.waitFor(hasTestTag("editor-title"))
        rule.waitFor(hasText("Bebidas") and isSelected())
        rule.onNode(hasText("Comidas") and isSelected()).assertDoesNotExist()
        rule.waitForIdle()
    }

    @Test
    fun today_withoutWeather_showsSeasonReason() {
        // Miércoles 15 de julio a las 14:30, Bogotá elegida pero sin conexión: lluvias en el trópico norte.
        val july = Clock.fixed(Instant.parse("2026-07-15T19:30:00Z"), ZoneId.of("America/Bogota"))
        val offline = FakeOpenMeteo(offline = true)
        val c = install(july, offline)
        runBlocking { c.settingsRepository.setCity(Place("Bogotá", "Bogotá D.C.", "Colombia", 4.60971, -74.08175)) }
        c.save(soup.copy(totalMinutes = 90))
        launch(c)

        rule.waitFor(hasTestTag("today-reason"))
        assertThat(titleOf("today-reason")).isEqualTo("Es temporada de lluvias: algo calientito")
        assertThat(titleOf("today-eyebrow")).isEqualTo("Comida de hoy · Temporada de lluvias")
        rule.onNodeWithTag("today-weather").assertDoesNotExist()
        // Se intentó pedir el tiempo al entrar, sin bloquear nada.
        rule.waitUntil(5_000) { offline.requests.isNotEmpty() }
    }

    @Test
    fun weather_askedAgainWhenReturningToTheApp() {
        // Una capital conocida y Open-Meteo sin conexión: cada vez que se pide, queda apuntado.
        val offline = FakeOpenMeteo(offline = true)
        val c = install(openMeteo = offline)
        runBlocking { c.settingsRepository.setCity(Place("Madrid", "Comunidad de Madrid", "España", 40.4168, -3.7038)) }
        c.save(soup)
        val owner = TestLifecycleOwner()
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RecetarioTheme(ThemeChoice.CALIDO) { HomeRoute(c, onCategory = {}, onRecipe = {}, onAdd = {}) }
            }
        }
        rule.waitUntil(5_000) { offline.forecastRequests.size == 1 }

        // La app pasa a segundo plano y vuelve (ON_PAUSE y ON_RESUME): se pide otra vez.
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitUntil(5_000) { offline.forecastRequests.size == 2 }
    }

    @Test
    fun todayCard_tapOpensDetail() {
        val c = install()
        c.save(soup, chocolate)
        launch(c)

        rule.waitFor(hasTestTag("today-drink-title"))
        rule.onNodeWithTag("today-drink").performClick()
        rule.waitFor(hasTestTag("detail-cook"))
        rule.waitFor(hasText("Chocolate caliente"))
    }

    @Test
    fun todayCard_startOpensCooking() {
        val c = install()
        c.save(soup.copy(steps = listOf(Step("p1", "Pela la calabaza.", emptyList(), null, emptySet()))))
        launch(c)

        rule.waitFor(hasTestTag("today-start"))
        rule.onNodeWithTag("today-start").performClick()
        rule.waitFor(hasTestTag("cooking-list"))
        // Que la receta termine de cargar antes de cerrar la base de datos.
        rule.waitFor(hasText("Pela la calabaza."))
        rule.waitForIdle()
    }

    @Test
    fun todayCard_weatherChipOnlyWithData() {
        // Sin ubicación: sin clima.
        val c = install(openMeteo = FakeOpenMeteo())
        c.save(soup, chocolate, flan)
        launch(c)
        rule.waitFor(hasTestTag("today-card"))
        rule.onNodeWithTag("today-weather").assertDoesNotExist()
    }

    @Test
    fun todayCard_weatherChipOnlyWithData_madrid() {
        // Con una ciudad (una capital conocida) y Open-Meteo de mentira: el clima pequeño con su descripción.
        val fake = FakeOpenMeteo(forecast = forecastJson(LocalDate.of(2026, 11, 13), apparent = 8.6))
        val c = install(openMeteo = fake)
        runBlocking { c.settingsRepository.setCity(Place("Madrid", "Comunidad de Madrid", "España", 40.4168, -3.7038)) }
        c.save(soup, chocolate, flan)
        launch(c)

        rule.waitFor(hasTestTag("today-weather"))
        rule.onNodeWithTag("today-weather").assert(hasContentDescription("El tiempo ahora: mayormente soleado, 9 °C en Madrid"))
        // Solo la descripción: el texto visible no se lee otra vez.
        rule.onAllNodes(hasText("9 °C · Madrid", substring = true), useUnmergedTree = true).assertCountEquals(0)
        // Con clima, el porqué habla del tiempo: frío y algo caliente.
        rule.waitUntil(5_000) { textOf("today-reason") == "Hace 9 °C: algo caliente y reconfortante" }
        assertThat(runBlocking { c.weatherRepository.snapshot.first() }).isNotNull()
    }
}
