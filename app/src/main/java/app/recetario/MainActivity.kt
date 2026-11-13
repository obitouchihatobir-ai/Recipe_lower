package app.recetario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
import app.recetario.core.ui.theme.resolveTheme
import app.recetario.navigation.AppNavHost
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RecetarioApp).container
        setContent {
            // Hasta que la Tarea 12 guarde los ajustes: Automático con Cálido de día (§6.1).
            val now = remember { LocalTime.now(container.clock) }
            val theme = resolveTheme(ThemeSettings(), now, isSystemInDarkTheme())
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = theme != ThemeChoice.NOCTURNO
                    isAppearanceLightNavigationBars = theme != ThemeChoice.NOCTURNO
                }
            }
            RecetarioTheme(theme) { AppNavHost(container) }
        }
    }
}
