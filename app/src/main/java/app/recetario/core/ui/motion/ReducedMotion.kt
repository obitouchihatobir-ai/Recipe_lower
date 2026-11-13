package app.recetario.core.ui.motion

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * `true` si el usuario quitó las animaciones del sistema
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`). `RecetarioTheme` lo proporciona.
 */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
