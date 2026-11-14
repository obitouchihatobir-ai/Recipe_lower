package app.recetario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.recetario.core.ui.theme.LocalThemeRevealOrigin
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeRevealHost
import app.recetario.navigation.AppNavHost
import app.recetario.settings.rememberResolvedTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RecetarioApp).container
        setContent {
            // Primera vez: Automático con Cálido de día (§6.1). Hasta leer los ajustes no se pinta nada.
            val theme = rememberResolvedTheme(container) ?: return@setContent
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = theme != ThemeChoice.NOCTURNO
                    isAppearanceLightNavigationBars = theme != ThemeChoice.NOCTURNO
                }
            }
            // Desde dónde se tocó en Apariencia: el cambio de tema se abre en círculo desde ahí. Caduca si el
            // toque no llegó a cambiar el tema, para que un cambio automático posterior sea un fundido.
            var origin by remember { mutableStateOf<Offset?>(null) }
            LaunchedEffect(origin) {
                if (origin != null) {
                    delay(1_500)
                    origin = null
                }
            }
            CompositionLocalProvider(LocalThemeRevealOrigin provides remember { { o: Offset? -> origin = o } }) {
                ThemeRevealHost(theme, origin, onOriginConsumed = { origin = null }) { shown ->
                    RecetarioTheme(shown) { AppNavHost(container) }
                }
            }
        }
    }
}
