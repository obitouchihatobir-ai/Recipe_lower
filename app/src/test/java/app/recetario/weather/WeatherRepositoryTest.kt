package app.recetario.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.settings.SettingsRepository
import app.recetario.testing.FakeOpenMeteo
import app.recetario.testing.forecastJson
import app.recetario.testing.testSettingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Un reloj que se puede mover a mano. */
private class MovableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("America/Bogota")
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

@RunWith(AndroidJUnit4::class)
class WeatherRepositoryTest {
    private val t0 = Instant.parse("2026-11-13T17:00:00Z")
    private val clock = MovableClock(t0)
    private val fake = FakeOpenMeteo(forecast = forecastJson(LocalDate.of(2026, 11, 13)))
    private val settings = SettingsRepository(testSettingsDataStore(), testSettingsDataStore())
    private var device: Coordinates? = null
    private val repo = DefaultWeatherRepository(settings, OpenMeteoClient(fake::open), { device }, clock)

    private val bogota = Place("Bogotá", "Bogotá D.C.", "Colombia", 4.60971, -74.08175)

    private fun later(d: Duration) {
        clock.now = t0.plus(d)
    }

    @Test
    fun weather_cacheFreshUnder1h_noRequest() = runBlocking {
        settings.setCity(bogota)
        assertThat(repo.refresh()).isInstanceOf(WeatherResult.Ok::class.java)
        assertThat(fake.forecastRequests).hasSize(1)

        later(Duration.ofMinutes(59))
        val cached = repo.refresh()
        assertThat(fake.forecastRequests).hasSize(1)
        assertThat((cached as WeatherResult.Ok).snapshot.fetchedAt).isEqualTo(t0)

        // Pedido a mano (forzado): sí.
        repo.refresh(force = true)
        assertThat(fake.forecastRequests).hasSize(2)

        later(Duration.ofMinutes(59 + 61))
        repo.refresh()
        assertThat(fake.forecastRequests).hasSize(3)
    }

    @Test
    fun weather_networkFails_usesCacheOrNone() = runBlocking {
        settings.setCity(bogota)
        repo.refresh()
        fake.offline = true

        later(Duration.ofHours(5))
        val five = repo.refresh()
        assertThat(five).isInstanceOf(WeatherResult.Offline::class.java)
        assertThat((five as WeatherResult.Offline).cached?.fetchedAt).isEqualTo(t0)
        assertThat(repo.snapshot.first()?.fetchedAt).isEqualTo(t0)
        assertThat(settings.weatherFailedAt.first()).isEqualTo(t0.plus(Duration.ofHours(5)))

        later(Duration.ofHours(7))
        val seven = repo.refresh()
        assertThat((seven as WeatherResult.Offline).cached).isNull()
        assertThat(repo.snapshot.first()).isNull()

        // Vuelve la red: se borra el fallo.
        fake.offline = false
        assertThat(repo.refresh()).isInstanceOf(WeatherResult.Ok::class.java)
        assertThat(settings.weatherFailedAt.first()).isNull()
        assertThat(repo.snapshot.first()?.fetchedAt).isEqualTo(t0.plus(Duration.ofHours(7)))
    }

    @Test
    fun weather_noLocation_returnsNoLocation() = runBlocking {
        assertThat(repo.refresh()).isEqualTo(WeatherResult.NoLocation)
        assertThat(repo.location().first()).isNull()
        // Aproximada, pero el teléfono aún no sabe dónde está y nunca lo supo.
        settings.setLocationMode(LocationMode.APPROXIMATE)
        assertThat(repo.refresh(force = true)).isEqualTo(WeatherResult.NoLocation)
        assertThat(fake.requests).isEmpty()
        assertThat(repo.snapshot.first()).isNull()
    }

    @Test
    fun weather_approximate_usesTheDeviceAndRemembersIt() = runBlocking {
        settings.setLocationMode(LocationMode.APPROXIMATE)
        device = Coordinates(-12.04637, -77.04279)
        assertThat(repo.refresh()).isInstanceOf(WeatherResult.Ok::class.java)
        assertThat(fake.forecastRequests.single().query).contains("latitude=-12.0464&longitude=-77.0428")
        assertThat(repo.location().first()?.latitude).isWithin(0.01).of(-12.04)

        // Sin dato del teléfono (o sin permiso): se usa la última aproximada.
        device = null
        assertThat(repo.refresh(force = true)).isInstanceOf(WeatherResult.Ok::class.java)
        assertThat(fake.forecastRequests).hasSize(2)
        assertThat(fake.forecastRequests[1].query).contains("latitude=-12.0464")
    }

    @Test
    fun weather_otherCity_doesNotUseTheOldCache() = runBlocking {
        settings.setCity(bogota)
        repo.refresh()
        assertThat(repo.snapshot.first()).isNotNull()
        settings.setCity(Place("Lima", "Lima", "Perú", -12.04637, -77.04279))
        assertThat(repo.snapshot.first()).isNull()
        repo.refresh()
        assertThat(fake.forecastRequests).hasSize(2)
    }

    @Test
    fun forecast_otherCityWithFailedRefresh_doesNotUseTheOldCitysSun() = runBlocking {
        settings.setCity(bogota)
        repo.refresh()
        assertThat(repo.forecast.first()).isNotNull()
        // Otra ciudad y sin red: la previsión de Bogotá (su salida y puesta del sol) no vale para Lima.
        fake.offline = true
        settings.setCity(Place("Lima", "Lima", "Perú", -12.04637, -77.04279))
        repo.refresh()
        assertThat(repo.forecast.first()).isNull()
    }

    @Test
    fun weather_badAnswers_areErrors_neverExceptions() = runBlocking {
        settings.setCity(bogota)
        fake.forecast = "esto no es json"
        assertThat(repo.refresh()).isInstanceOf(WeatherResult.Error::class.java)
        fake.forecast = forecastJson(LocalDate.of(2026, 11, 13))
        fake.code = 503
        assertThat(repo.refresh(force = true)).isInstanceOf(WeatherResult.Error::class.java)
        assertThat(repo.snapshot.first()).isNull()
    }

    @Test
    fun clearLocation_forgetsCityAndWeather() = runBlocking {
        settings.setCity(bogota)
        repo.refresh()
        settings.clearLocation()
        assertThat(settings.locationMode.first()).isEqualTo(LocationMode.NONE)
        assertThat(repo.location().first()).isNull()
        assertThat(settings.weatherCache.first()).isNull()
        assertThat(repo.forecast.first()).isNull()
    }

    @Test
    fun searchCity_fiveResultsOrOffline() = runBlocking {
        val found = repo.searchCity("Bogotá") as CitySearchResult.Ok
        assertThat(found.places.map { it.label() }).containsExactly("Bogotá, Bogotá D.C., Colombia", "Bogotá, Nueva Jersey, Estados Unidos")
        assertThat(repo.searchCity("   ")).isEqualTo(CitySearchResult.Ok(emptyList()))
        fake.offline = true
        assertThat(repo.searchCity("Bogotá")).isEqualTo(CitySearchResult.Offline)
    }
}
