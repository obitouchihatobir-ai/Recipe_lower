package app.recetario

import android.content.Intent
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import app.recetario.cooking.TimerNotifications
import app.recetario.core.ui.motion.splashExitAnimator
import app.recetario.core.ui.motion.systemReducedMotion
import app.recetario.core.ui.theme.LocalThemeRevealOrigin
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeRevealHost
import app.recetario.navigation.AppNavHost
import app.recetario.settings.rememberResolvedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Ya se leyeron los ajustes y hay algo que pintar: la pantalla de arranque puede irse. */
    @Volatile
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // La pantalla de arranque se queda mientras se leen los ajustes (antes no se pinta nada). Al irse, el icono
        // sube y se desvanece; sin animaciones del sistema, se quita sin más.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !contentReady }
        if (!systemReducedMotion(this)) {
            splash.setOnExitAnimationListener { provider ->
                // En algunos teléfonos la pantalla de arranque no tiene icono (por ejemplo, al volver de Recientes).
                val icon = try {
                    provider.iconView
                } catch (_: RuntimeException) {
                    null
                }
                splashExitAnimator(icon, provider.view, onEnd = provider::remove).start()
            }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RecetarioApp).container
        // Abierta desde un aviso «⏱ ¡Tiempo!»: ese temporizador se apaga (solo la primera vez, no al girar; nunca
        // si Android repite el intent al reabrir desde Recientes, ni si ese paso vuelve a estar en marcha).
        if (savedInstanceState == null) TimerNotifications.dismissFromIntent(intent, container.timerManager)
        // Lista en cuanto se leen los ajustes (los mismos que espera el tema). Se mira el flujo, fuera del hilo
        // principal, y no la composición: mientras la pantalla de arranque cancela los dibujados, el hilo principal
        // no para (en las pruebas, además, es el que mueve el reloj de Compose).
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                container.settingsRepository.themeSettings.first()
            } finally {
                contentReady = true
            }
        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TimerNotifications.dismissFromIntent(intent, (application as RecetarioApp).container.timerManager)
    }
}
