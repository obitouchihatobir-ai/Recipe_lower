package app.recetario.weather

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Open-Meteo contestó con un código de error (no es falta de conexión). */
class OpenMeteoHttpException(val code: Int) : Exception("Open-Meteo respondió $code")

/**
 * Las dos únicas llamadas de red de la app (diseño 2, §3): la previsión (`api.open-meteo.com`) y el buscador de
 * ciudades (`geocoding-api.open-meteo.com`). Con `HttpURLConnection`, 10 s de espera y `User-Agent: Sazon`. Son
 * bloqueantes: se llaman fuera del hilo principal. Lanzan [IOException] sin conexión y [OpenMeteoHttpException] si
 * el servicio responde con un error. [open] se cambia en las pruebas (nunca salen a la red).
 */
class OpenMeteoClient(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun forecastUrl(latitude: Double, longitude: Double): URL = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=${coordinate(latitude)}&longitude=${coordinate(longitude)}" +
            "&current=apparent_temperature,precipitation,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=7",
    )

    fun geocodeUrl(name: String): URL = URL(
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(name.trim(), "UTF-8")}" +
            "&count=5&language=es&format=json",
    )

    fun forecast(latitude: Double, longitude: Double): String = get(forecastUrl(latitude, longitude))

    fun geocode(name: String): String = get(geocodeUrl(name))

    private fun get(url: URL): String {
        val connection = open(url)
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Sazon")
            val code = connection.responseCode
            if (code !in 200..299) throw OpenMeteoHttpException(code)
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("Respuesta demasiado grande")
                }
                out.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000

        /** Una previsión de 7 días ocupa unos 2 KB: algo mucho mayor no es de Open-Meteo. */
        const val MAX_BYTES = 1024 * 1024

        /** Cuatro decimales (unos 10 m, más que de sobra) y siempre con punto, sea cual sea el idioma del teléfono. */
        fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.4f", value)
    }
}
