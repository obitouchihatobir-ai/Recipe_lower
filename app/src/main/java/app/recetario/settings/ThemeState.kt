package app.recetario.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.recetario.AppContainer
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
import app.recetario.core.ui.theme.resolveTheme
import app.recetario.core.ui.theme.sunTimesFor
import app.recetario.weather.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import app.recetario.home.minuteTicks
import java.time.Clock
import java.time.LocalTime

/**
 * La hora del [clock], que se vuelve a leer cada minuto (así Automático cambia al llegar la noche) y al volver a la
 * app: la espera del minuto no avanza con el móvil dormido, y tras una noche con la pantalla apagada el tema
 * seguiría siendo el de ayer hasta un minuto después.
 */
@Composable
fun rememberNow(clock: Clock): LocalTime {
    var now by remember(clock) { mutableStateOf(LocalTime.now(clock)) }
    LaunchedEffect(clock) { minuteTicks(clock).collect { now = LocalTime.now(clock) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = LocalTime.now(clock) }
    return now
}

/**
 * Lo que decide el tema: los ajustes y, con «Noche según la salida y puesta del sol» ([bySun]), la última previsión
 * guardada ([forecast]; solo mientras haya ubicación).
 */
data class ThemeInputs(val settings: ThemeSettings, val bySun: Boolean, val forecast: WeatherSnapshot?) {
    /** Salida y puesta del sol de hoy, si toca usarlas: con Automático, el ajuste encendido y una previsión de menos de 3 días. */
    fun sun(clock: Clock): Pair<LocalTime, LocalTime>? =
        if (settings.auto && bySun) sunTimesFor(forecast, LocalDate.now(clock), clock.instant()) else null

    fun resolve(now: LocalTime, systemDark: Boolean, clock: Clock): ThemeChoice = resolveTheme(settings, now, systemDark, sun(clock))
}

/** Los ajustes del tema y, si hace falta, la previsión. `MainActivity` espera a que lleguen antes de pintar. */
fun AppContainer.themeInputs(): Flow<ThemeInputs> =
    combine(settingsRepository.themeSettings, settingsRepository.themeBySun, weatherRepository.forecast) { settings, bySun, forecast ->
        ThemeInputs(settings, bySun, forecast.takeIf { bySun })
    }.distinctUntilChanged()

/** El tema que toca ahora (§6.1 y diseño 2, §7.4), o null mientras se leen los ajustes. */
@Composable
fun rememberResolvedTheme(container: AppContainer): ThemeChoice? {
    val inputs by remember(container) { container.themeInputs() }.collectAsState<ThemeInputs, ThemeInputs?>(initial = null)
    val now = rememberNow(container.clock)
    val dark = isSystemInDarkTheme()
    return inputs?.resolve(now, dark, container.clock)
}
