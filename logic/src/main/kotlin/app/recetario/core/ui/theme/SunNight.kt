package app.recetario.core.ui.theme

import java.time.LocalTime

/** Noche: desde la puesta (incluida) hasta la salida del sol (excluida). */
fun isNightBySun(now: LocalTime, sunrise: LocalTime, sunset: LocalTime): Boolean =
    if (sunrise <= sunset) now < sunrise || now >= sunset else now >= sunset && now < sunrise

/** Con una previsión más vieja que esto, el tema vuelve a las horas fijas (diseño 2, §7.4). */
val SUN_FORECAST_MAX_AGE: java.time.Duration = java.time.Duration.ofDays(3)

/**
 * Salida y puesta del sol de [today] según [snapshot], o null si no hay previsión, tiene más de 3 días (a [now]) o
 * no trae ese día con las dos horas.
 */
fun sunTimesFor(
    snapshot: app.recetario.weather.WeatherSnapshot?,
    today: java.time.LocalDate,
    now: java.time.Instant,
): Pair<LocalTime, LocalTime>? {
    snapshot ?: return null
    if (java.time.Duration.between(snapshot.fetchedAt, now) > SUN_FORECAST_MAX_AGE) return null
    val day = snapshot.daily.firstOrNull { it.date == today } ?: return null
    return (day.sunrise ?: return null) to (day.sunset ?: return null)
}
