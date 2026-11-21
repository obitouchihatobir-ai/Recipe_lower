package app.recetario.core.ui.theme

import java.time.LocalTime

/** Noche: desde la puesta (incluida) hasta la salida del sol (excluida). */
fun isNightBySun(now: LocalTime, sunrise: LocalTime, sunset: LocalTime): Boolean =
    if (sunrise <= sunset) now < sunrise || now >= sunset else now >= sunset && now < sunrise
