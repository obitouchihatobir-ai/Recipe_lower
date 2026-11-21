package app.recetario.weather

enum class WeatherKind { CLEAR, CLOUDY, FOG, RAIN, STORM, SNOW }

/** Códigos WMO de Open-Meteo. Un código desconocido cuenta como nublado. */
fun weatherKind(code: Int): WeatherKind = when (code) {
    0, 1 -> WeatherKind.CLEAR
    2, 3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    in 51..67, in 80..82 -> WeatherKind.RAIN
    in 71..77, 85, 86 -> WeatherKind.SNOW
    95, 96, 99 -> WeatherKind.STORM
    else -> WeatherKind.CLOUDY
}

fun weatherLabel(code: Int): String = when (code) {
    0 -> "Soleado"
    1 -> "Mayormente soleado"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    51, 53, 55 -> "Llovizna"
    56, 57, 66, 67 -> "Lluvia helada"
    61, 63, 65, 80, 81, 82 -> "Lluvia"
    71, 73, 75, 77, 85, 86 -> "Nieve"
    95, 96, 99 -> "Tormenta"
    else -> "Nublado"
}
