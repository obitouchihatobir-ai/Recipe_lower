package app.recetario.settings

import app.recetario.testing.CloseDatabaseRule
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.installTestContainer
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import app.recetario.today.ThermalThresholds
import app.recetario.weather.LocationMode
import app.recetario.weather.Place
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class LocationWeatherScreenTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container?.database?.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private var container: AppContainer? = null
    private val fake = FakeOpenMeteo()

    /** El diálogo del permiso: contesta [granted] y cuenta cuántas veces se pidió. */
    private class FakePermission(private val granted: Boolean) : ActivityResultRegistryOwner {
        val asked = mutableListOf<Any?>()
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                asked += input
                dispatchResult(requestCode, granted)
            }
        }
    }

    private var shown by mutableStateOf(true)

    private fun launch(permission: FakePermission = FakePermission(granted = false)): AppContainer {
        val c = container ?: installTestContainer(openMeteo = fake).also { container = it }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides permission) {
                RecetarioTheme(ThemeChoice.CALIDO) { if (shown) LocationWeatherRoute(c, onBack = {}) }
            }
        }
        rule.waitFor(hasTestTag("location-weather-screen"))
        return c
    }

    @Test
    fun location_denied_offersCity_andDoesNotAskAgain() {
        val permission = FakePermission(granted = false)
        val c = launch(permission)

        rule.onNodeWithTag("loc-approximate").performClick()
        rule.waitFor(hasText("Sin permiso de ubicación. Puedes elegir una ciudad."))
        assertThat(permission.asked).containsExactly(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        rule.onNodeWithTag("loc-city").assertExists()

        // Otro toque, y al volver a la pantalla: no se vuelve a pedir.
        rule.onNodeWithTag("loc-approximate").performClick()
        rule.runOnIdle { shown = false }
        rule.runOnIdle { shown = true }
        rule.waitFor(hasTestTag("loc-approximate"))
        rule.onNodeWithTag("loc-approximate").performClick()
        rule.waitFor(hasText("Sin permiso de ubicación. Puedes elegir una ciudad."))
        rule.runOnIdle { assertThat(permission.asked).hasSize(1) }
        runBlocking {
            assertThat(c.settingsRepository.locationMode.first()).isEqualTo(LocationMode.NONE)
            assertThat(c.settingsRepository.locationPermissionDenied.first()).isTrue()
        }
    }

    @Test
    fun city_search_selectsAndSaves() {
        val c = launch()
        rule.onNodeWithTag("loc-city").performClick()
        rule.waitFor(hasTestTag("city-panel"))
        rule.onNodeWithTag("city-field").performTextInput("Bogotá")
        rule.onNodeWithTag("city-search").performClick()
        rule.waitFor(hasText("Bogotá, Bogotá D.C., Colombia"))
        rule.onNodeWithText("Bogotá, Nueva Jersey, Estados Unidos").assertExists()

        rule.onNodeWithText("Bogotá, Bogotá D.C., Colombia").performClick()

        rule.waitForGone(hasTestTag("city-panel"))
        rule.waitFor(hasText("Bogotá, Bogotá D.C., Colombia"))
        runBlocking {
            assertThat(c.settingsRepository.locationMode.first()).isEqualTo(LocationMode.CITY)
            assertThat(c.settingsRepository.city.first()).isEqualTo(Place("Bogotá", "Bogotá D.C.", "Colombia", 4.60971, -74.08175))
        }
        // Con la ciudad nueva se pide el tiempo y se dice cuándo.
        rule.waitFor(hasText("Actualizado a las 21:40"))
        assertThat(fake.forecastRequests.last().query).contains("latitude=4.6097&longitude=-74.0818")
    }

    @Test
    fun weatherFails_saysSo_andRemoveLocationClearsIt() {
        fake.offline = true
        val c = installTestContainer(openMeteo = fake).also { container = it }
        runBlocking { c.settingsRepository.setCity(Place("Bogotá", "Bogotá D.C.", "Colombia", 4.6, -74.08)) }
        launch()
        rule.waitFor(hasText("No se pudo obtener el tiempo"))

        rule.onNodeWithTag("loc-remove").performScrollTo().performClick()

        rule.waitFor(hasText("Sin ubicación"))
        rule.waitForGone(hasTestTag("weather-status"))
        runBlocking {
            assertThat(c.settingsRepository.locationMode.first()).isEqualTo(LocationMode.NONE)
            assertThat(c.settingsRepository.city.first()).isNull()
        }
    }

    @Test
    fun thresholds_stepByOneDegree_andCannotCross() {
        val c = launch()
        rule.onNodeWithTag("cold-plus").performScrollTo().performClick()
        rule.waitFor(hasText("16 °C"))
        runBlocking { assertThat(c.settingsRepository.thermalThresholds.first()).isEqualTo(ThermalThresholds(16, 24)) }
        rule.onNodeWithTag("hot-minus").performClick()
        rule.waitFor(hasText("23 °C"))
        runBlocking { c.settingsRepository.setThermalThresholds(ThermalThresholds(22, 23)) }
        rule.waitFor(hasText("22 °C"))
        // Frío por debajo de 23 y calor por encima de 23 no tiene sentido: los botones no lo dejan.
        rule.onNodeWithTag("cold-plus").assertIsNotEnabled()
        rule.onNodeWithTag("hot-minus").assertIsNotEnabled()
    }
}
