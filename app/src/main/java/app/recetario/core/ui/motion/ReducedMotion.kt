package app.recetario.core.ui.motion

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * `true` si el usuario quitó las animaciones del sistema
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`). `RecetarioTheme` lo proporciona.
 */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Lee la opción del sistema de quitar animaciones (fuera de `RecetarioTheme`, que la publica en [LocalReducedMotion]). */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
