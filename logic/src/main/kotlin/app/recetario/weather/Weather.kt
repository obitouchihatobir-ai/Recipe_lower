package app.recetario.weather

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class CurrentWeather(
    val apparentC: Double,
    val precipitationMm: Double,
    val weatherCode: Int,
    val isDay: Boolean,
)

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val maxC: Double,
    val minC: Double,
    val rainProbability: Int?,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
)

data class WeatherSnapshot(
    val current: CurrentWeather?,
    val daily: List<DailyForecast>,
    val fetchedAt: Instant,
)

data class Place(
    val name: String,
    val admin: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)

class WeatherParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
