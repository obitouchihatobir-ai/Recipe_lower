package app.recetario.weather

import app.recetario.testing.FakeOpenMeteo
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.Locale

class OpenMeteoClientTest {
    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun client_buildsExactUrls() {
        // En español los decimales llevan coma: la URL no debe cambiar con el idioma del teléfono.
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
        val fake = FakeOpenMeteo()
        val client = OpenMeteoClient(fake::open)

        client.forecast(4.60971, -74.08175)
        client.geocode("Ciudad de México")

        assertThat(fake.requests.map { it.toString() }).containsExactly(
            "https://api.open-meteo.com/v1/forecast?latitude=4.6097&longitude=-74.0818" +
                "&current=apparent_temperature,precipitation,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                "&timezone=auto&forecast_days=7",
            "https://geocoding-api.open-meteo.com/v1/search?name=Ciudad+de+M%C3%A9xico&count=5&language=es&format=json",
        ).inOrder()
        for (c in fake.connections) {
            assertThat(c.headers["User-Agent"]).isEqualTo("Sazon")
            assertThat(c.connectTimeout).isEqualTo(10_000)
            assertThat(c.readTimeout).isEqualTo(10_000)
            assertThat(c.disconnected).isTrue()
        }
    }

    @Test
    fun client_returnsTheBody() {
        val fake = FakeOpenMeteo(geocoding = """{"results":[]}""")
        assertThat(OpenMeteoClient(fake::open).geocode("x")).isEqualTo("""{"results":[]}""")
    }

    @Test
    fun client_httpErrorOrOffline_throws() {
        val fake = FakeOpenMeteo(code = 500)
        assertThrows(OpenMeteoHttpException::class.java) { OpenMeteoClient(fake::open).forecast(1.0, 2.0) }
        assertThat(fake.connections.single().disconnected).isTrue()
        val offline = FakeOpenMeteo(offline = true)
        assertThrows(IOException::class.java) { OpenMeteoClient(offline::open).forecast(1.0, 2.0) }
    }
}
