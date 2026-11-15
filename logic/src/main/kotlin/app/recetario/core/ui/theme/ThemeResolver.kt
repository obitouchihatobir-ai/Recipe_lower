package app.recetario.core.ui.theme

import java.time.LocalTime

enum class ThemeChoice { CALIDO, FRESCO, NOCTURNO }

data class ThemeSettings(
    val auto: Boolean = true,
    val choice: ThemeChoice = ThemeChoice.CALIDO,
    val dayTheme: ThemeChoice = ThemeChoice.CALIDO,
    val nightStart: LocalTime = LocalTime.of(20, 0),
    val nightEnd: LocalTime = LocalTime.of(7, 0),
)

/**
 * Manual: la elección. Automático: Nocturno si el sistema está en oscuro o si [now] cae en la
 * noche (de [ThemeSettings.nightStart] incluida a [ThemeSettings.nightEnd] excluida, que puede
 * cruzar la medianoche); si no, el tema claro de día.
 */
fun resolveTheme(settings: ThemeSettings, now: LocalTime, systemDark: Boolean): ThemeChoice {
    if (!settings.auto) return settings.choice
    if (systemDark) return ThemeChoice.NOCTURNO
    val start = settings.nightStart
    val end = settings.nightEnd
    val isNight = if (start <= end) now >= start && now < end else now >= start || now < end
    return if (isNight) ThemeChoice.NOCTURNO else settings.dayTheme
}
