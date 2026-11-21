package app.recetario.testing

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Una conexión que no sale a la red: responde [code] con [body] y apunta lo que se le pide. */
class FakeHttpConnection(url: URL, private val code: Int, private val body: String) : HttpURLConnection(url) {
    val headers = mutableMapOf<String, String>()
    var disconnected = false

    override fun setRequestProperty(key: String, value: String) {
        headers[key] = value
    }

    override fun connect() {}

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = code

    override fun getInputStream(): InputStream =
        if (code in 200..299) body.byteInputStream() else throw IOException("HTTP $code")
}

/**
 * Open-Meteo de mentira para las pruebas (nunca hay red): [forecast] y [geocoding] son las respuestas de cada
 * servicio; con [offline] cada petición falla como sin conexión. [requests] guarda las URL pedidas.
 */
class FakeOpenMeteo(
    var forecast: String = forecastJson(LocalDate.of(2026, 11, 13)),
    var geocoding: String = BOGOTA_GEOCODING,
    var offline: Boolean = false,
    var code: Int = 200,
) {
    val requests = mutableListOf<URL>()
    val connections = mutableListOf<FakeHttpConnection>()

    fun open(url: URL): HttpURLConnection {
        requests += url
        if (offline) throw IOException("Sin conexión (prueba)")
        val body = if (url.host == "geocoding-api.open-meteo.com") geocoding else forecast
        return FakeHttpConnection(url, code, body).also { connections += it }
    }

    val forecastRequests: List<URL> get() = requests.filter { it.host == "api.open-meteo.com" }

    companion object {
        /** Una capital conocida (nunca la ubicación del usuario). */
        const val BOGOTA_GEOCODING = """{"results":[
            {"name":"Bogotá","admin1":"Bogotá D.C.","country":"Colombia","latitude":4.60971,"longitude":-74.08175},
            {"name":"Bogotá","admin1":"Nueva Jersey","country":"Estados Unidos","latitude":40.87,"longitude":-74.03}
        ]}"""
    }
}

/** Previsión de 7 días desde [firstDay], con la temperatura aparente de ahora, la salida y la puesta del sol. */
fun forecastJson(firstDay: LocalDate, apparent: Double = 24.0, sunrise: String = "06:30", sunset: String = "18:10"): String {
    val days = (0L..6L).map { firstDay.plusDays(it) }
    fun list(f: (LocalDate) -> String) = days.joinToString(",") { f(it) }
    return """{"current":{"apparent_temperature":$apparent,"precipitation":0.0,"weather_code":1,"is_day":1},
        "daily":{"time":[${list { "\"$it\"" }}],"weather_code":[${list { "1" }}],
        "temperature_2m_max":[${list { "25.0" }}],"temperature_2m_min":[${list { "12.0" }}],
        "precipitation_probability_max":[${list { "10" }}],
        "sunrise":[${list { "\"${it}T$sunrise\"" }}],"sunset":[${list { "\"${it}T$sunset\"" }}]}}"""
}
