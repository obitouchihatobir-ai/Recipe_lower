package app.recetario.home

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.MainActivity
import app.recetario.recipes.model.ServeTemp
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.weather.Place
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Inicio pide el tiempo al entrar y también cada vez que se vuelve a la app (ON_RESUME), con la app de verdad
 * ([MainActivity]). Nunca hay red: Open-Meteo sin conexión apunta cada petición; la ciudad es una capital conocida.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class TodayWeatherResumeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        if (::container.isInitialized) container.database.close()
    }

    @Test
    fun weather_askedAgainWhenReturningToTheApp() {
        val offline = FakeOpenMeteo(offline = true)
        container = installTestContainer(openMeteo = offline)
        runBlocking {
            container.settingsRepository.setAuto(false)
            container.settingsRepository.setCity(Place("Madrid", "Comunidad de Madrid", "España", 40.4168, -3.7038))
            container.recipeRepository.save(sampleRecipe("sopa", "Crema de calabaza", serveTemp = ServeTemp.HOT))
        }
        val s = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        rule.waitFor(hasTestTag("today-card"))
        rule.waitUntil(5_000) { offline.forecastRequests.isNotEmpty() }
        rule.waitForIdle()
        val before = offline.forecastRequests.size

        // La app pasa a segundo plano y vuelve (ON_PAUSE y ON_RESUME): se pide otra vez.
        println("DIAG before=$before")
        s.moveToState(Lifecycle.State.STARTED)
        println("DIAG state=${s.state}")
        s.moveToState(Lifecycle.State.RESUMED)
        println("DIAG state=${s.state}")
        try {
            rule.waitUntil(5_000) { offline.forecastRequests.size > before }
        } finally {
            println("DIAG after=${offline.forecastRequests.size}")
        }
    }
}
