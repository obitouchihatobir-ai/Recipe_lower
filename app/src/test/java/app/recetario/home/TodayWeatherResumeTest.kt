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
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
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
    private val offline = FakeOpenMeteo(offline = true)

    @After
    fun tearDown() {
        scenario?.close()
        if (::container.isInitialized) container.database.close()
    }

    @Test
    fun weather_askedAgainWhenReturningToTheApp() {
        container = installTestContainer(openMeteo = offline)
        runBlocking {
            container.settingsRepository.setAuto(false)
            container.settingsRepository.setCity(Place("Madrid", "Comunidad de Madrid", "España", 40.4168, -3.7038))
            container.recipeRepository.save(sampleRecipe("sopa", "Crema de calabaza", serveTemp = ServeTemp.HOT))
        }
        val s = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        rule.waitFor(hasTestTag("today-card"))
        waitForRequests(1)

        // La app pasa a segundo plano y vuelve (ON_PAUSE y ON_RESUME): cada vez se pide otra vez.
        for (round in 2..4) {
            s.moveToState(Lifecycle.State.STARTED)
            s.moveToState(Lifecycle.State.RESUMED)
            waitForRequests(round)
        }
    }

    /**
     * Espera a que se hayan pedido [n] previsiones y a que la última petición termine (sin conexión, anota el fallo):
     * así la siguiente no espera al candado de la anterior. Vacía la cola del hilo principal en cada vuelta.
     */
    private fun waitForRequests(n: Int) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            offline.forecastRequests.size >= n &&
                runBlocking { container.settingsRepository.weatherFailedAt.first() } != null
        }
        assertThat(offline.forecastRequests).hasSize(n)
        runBlocking { container.settingsRepository.setWeatherFailedAt(null) }
    }
}
