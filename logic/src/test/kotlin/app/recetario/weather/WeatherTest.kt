package app.recetario.weather

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertThrows
import org.junit.Test

class WeatherTest {
    private val now = Instant.parse("2026-07-15T20:00:00Z")

    private fun res(name: String) =
        requireNotNull(javaClass.getResource("/openmeteo/$name")) { "Falta $name" }.readText()

    @Test
    fun parseForecast_fullSample() {
        val s = parseForecast(res("forecast-full.json"), now)
        assertThat(s.fetchedAt).isEqualTo(now)
        assertThat(s.current).isEqualTo(CurrentWeather(27.3, 0.4, 61, true))
        assertThat(s.daily).hasSize(3)
        assertThat(s.daily[0]).isEqualTo(
            DailyForecast(LocalDate.of(2026, 7, 15), 61, 26.1, 13.2, 80, LocalTime.of(6, 58), LocalTime.of(20, 2)),
        )
        assertThat(s.daily[1].rainProbability).isNull()
        assertThat(s.daily[2].maxC).isEqualTo(28.0)
    }

    @Test
    fun parseForecast_missingCurrent_returnsNullCurrent() {
        val s = parseForecast(res("forecast-no-current.json"), now)
        assertThat(s.current).isNull()
        assertThat(s.daily).hasSize(1)
        assertThat(s.daily[0].weatherCode).isEqualTo(2)
    }

    @Test
    fun parseForecast_garbage_throws() {
        assertThrows(WeatherParseException::class.java) { parseForecast("no es json", now) }
        assertThrows(WeatherParseException::class.java) { parseForecast("{}", now) }
        assertThrows(WeatherParseException::class.java) { parseForecast("""{"current":{"time":"x"}}""", now) }
        assertThrows(WeatherParseException::class.java) { parseForecast("[1,2]", now) }
    }

    @Test
    fun parseForecast_skipsIncompleteDays() {
        // Un día lejano sin código, máxima o mínima no tira la previsión entera: se salta.
        val json = """{"daily":{"time":["2026-07-15","2026-07-16","2026-07-17","2026-07-18"],
            "weather_code":[1,null,3,2],"temperature_2m_max":[20.0,21.0,null,23.0],"temperature_2m_min":[10.0,11.0,12.0,null]}}"""
        val days = parseForecast(json, now).daily
        assertThat(days.map { it.date }).containsExactly(LocalDate.of(2026, 7, 15))
        // Si no queda ninguno, sí lanza.
        val none = """{"daily":{"time":["2026-07-15"],"weather_code":[null],"temperature_2m_max":[20.0],"temperature_2m_min":[10.0]}}"""
        assertThrows(WeatherParseException::class.java) { parseForecast(none, now) }
    }

    @Test
    fun parseForecast_missingSun_isNull() {
        val json = """{"daily":{"time":["2026-07-15"],"weather_code":[1],"temperature_2m_max":[20.0],"temperature_2m_min":[10.0]}}"""
        val d = parseForecast(json, now).daily.single()
        assertThat(d.sunrise).isNull()
        assertThat(d.sunset).isNull()
        assertThat(d.rainProbability).isNull()
    }

    @Test
    fun parseGeocoding_fiveResults() {
        val p = parseGeocoding(res("geocoding-five.json"))
        assertThat(p).hasSize(5)
        assertThat(p[0]).isEqualTo(Place("Villa Ejemplo", "Estado Uno", "México", 19.43, -99.13))
        assertThat(p[2].admin).isNull()
        assertThat(p[3].country).isNull()
    }

    @Test
    fun parseGeocoding_noResults_empty() {
        assertThat(parseGeocoding(res("geocoding-empty.json"))).isEmpty()
        assertThat(parseGeocoding("basura")).isEmpty()
    }

    @Test
    fun weatherKind_codes() {
        assertThat(weatherKind(0)).isEqualTo(WeatherKind.CLEAR)
        assertThat(weatherKind(1)).isEqualTo(WeatherKind.CLEAR)
        assertThat(weatherKind(3)).isEqualTo(WeatherKind.CLOUDY)
        assertThat(weatherKind(45)).isEqualTo(WeatherKind.FOG)
        assertThat(weatherKind(53)).isEqualTo(WeatherKind.RAIN)
        assertThat(weatherKind(61)).isEqualTo(WeatherKind.RAIN)
        assertThat(weatherKind(82)).isEqualTo(WeatherKind.RAIN)
        assertThat(weatherKind(95)).isEqualTo(WeatherKind.STORM)
        assertThat(weatherKind(71)).isEqualTo(WeatherKind.SNOW)
        assertThat(weatherKind(86)).isEqualTo(WeatherKind.SNOW)
        assertThat(weatherKind(999)).isEqualTo(WeatherKind.CLOUDY)
    }

    @Test
    fun weatherLabel_texts() {
        assertThat(weatherLabel(0)).isEqualTo("Soleado")
        assertThat(weatherLabel(2)).isEqualTo("Parcialmente nublado")
        assertThat(weatherLabel(3)).isEqualTo("Nublado")
        assertThat(weatherLabel(45)).isEqualTo("Niebla")
        assertThat(weatherLabel(61)).isEqualTo("Lluvia")
        assertThat(weatherLabel(95)).isEqualTo("Tormenta")
        assertThat(weatherLabel(71)).isEqualTo("Nieve")
    }
}
