package app.recetario.weather

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

private fun JsonElement?.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray

/** `is_day` llega como 0/1 (o booleano). */
private fun JsonPrimitive.dayFlag(): Boolean? = intOrNull?.let { it != 0 } ?: booleanOrNull

/**
 * Lee la respuesta de `api.open-meteo.com/v1/forecast`. El clima actual es opcional (queda null);
 * la previsión diaria es lo esencial: sin ella lanza [WeatherParseException].
 */
fun parseForecast(json: String, fetchedAt: Instant): WeatherSnapshot {
    try {
        val root = Json.parseToJsonElement(json).obj() ?: throw WeatherParseException("Respuesta inesperada")
        val dailyObj = root["daily"].obj() ?: throw WeatherParseException("Falta la previsión diaria")
        val times = dailyObj["time"].arr() ?: throw WeatherParseException("Falta la lista de días")
        val codes = dailyObj["weather_code"].arr()
        val maxes = dailyObj["temperature_2m_max"].arr()
        val mins = dailyObj["temperature_2m_min"].arr()
        val rain = dailyObj["precipitation_probability_max"].arr()
        val rises = dailyObj["sunrise"].arr()
        val sets = dailyObj["sunset"].arr()
        // Un día incompleto (sin código, máxima o mínima) se salta; sin ningún día completo, no hay previsión.
        val daily = times.indices.mapNotNull { i ->
            val date = times[i].prim()?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            DailyForecast(
                date = date,
                weatherCode = codes?.getOrNull(i).prim()?.intOrNull ?: return@mapNotNull null,
                maxC = maxes?.getOrNull(i).prim()?.doubleOrNull ?: return@mapNotNull null,
                minC = mins?.getOrNull(i).prim()?.doubleOrNull ?: return@mapNotNull null,
                rainProbability = rain?.getOrNull(i).prim()?.intOrNull,
                sunrise = rises?.getOrNull(i).prim()?.content?.let(::timeOfDay),
                sunset = sets?.getOrNull(i).prim()?.content?.let(::timeOfDay),
            )
        }
        if (daily.isEmpty()) throw WeatherParseException("La previsión no tiene ningún día completo")
        return WeatherSnapshot(parseCurrent(root["current"].obj()), daily, fetchedAt)
    } catch (e: WeatherParseException) {
        throw e
    } catch (e: Exception) {
        throw WeatherParseException("No se pudo leer la previsión", e)
    }
}

private fun timeOfDay(iso: String): LocalTime? =
    runCatching { LocalDateTime.parse(iso).toLocalTime() }.getOrNull()

private fun parseCurrent(c: JsonObject?): CurrentWeather? {
    c ?: return null
    return CurrentWeather(
        apparentC = c["apparent_temperature"].prim()?.doubleOrNull ?: return null,
        precipitationMm = c["precipitation"].prim()?.doubleOrNull ?: 0.0,
        weatherCode = c["weather_code"].prim()?.intOrNull ?: return null,
        isDay = c["is_day"].prim()?.dayFlag() ?: true,
    )
}

/** Lee `geocoding-api.open-meteo.com/v1/search`; sin resultados (o con basura) devuelve una lista vacía. */
fun parseGeocoding(json: String): List<Place> {
    val results = runCatching { Json.parseToJsonElement(json).obj()?.get("results").arr() }.getOrNull()
        ?: return emptyList()
    return results.mapNotNull { e ->
        val o = e.obj() ?: return@mapNotNull null
        val name = o["name"].prim()?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Place(
            name = name,
            admin = o["admin1"].prim()?.content?.takeIf { it.isNotBlank() },
            country = o["country"].prim()?.content?.takeIf { it.isNotBlank() },
            latitude = o["latitude"].prim()?.doubleOrNull ?: return@mapNotNull null,
            longitude = o["longitude"].prim()?.doubleOrNull ?: return@mapNotNull null,
        )
    }
}
