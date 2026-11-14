package app.recetario.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import app.recetario.AppContainer
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
import app.recetario.core.ui.theme.resolveTheme
import app.recetario.home.minuteTicks
import java.time.Clock
import java.time.LocalTime

/** La hora del [clock], que se vuelve a leer cada minuto (así Automático cambia al llegar la noche). */
@Composable
fun rememberNow(clock: Clock): LocalTime {
    var now by remember(clock) { mutableStateOf(LocalTime.now(clock)) }
    LaunchedEffect(clock) { minuteTicks(clock).collect { now = LocalTime.now(clock) } }
    return now
}

/** El tema que toca ahora (§6.1), o null mientras se leen los ajustes. */
@Composable
fun rememberResolvedTheme(container: AppContainer): ThemeChoice? {
    val settings by container.settingsRepository.themeSettings.collectAsState<ThemeSettings?>(initial = null)
    val now = rememberNow(container.clock)
    val dark = isSystemInDarkTheme()
    return settings?.let { resolveTheme(it, now, dark) }
}
