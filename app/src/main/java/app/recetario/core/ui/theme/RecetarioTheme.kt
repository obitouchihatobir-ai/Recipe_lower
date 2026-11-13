package app.recetario.core.ui.theme

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.recetario.core.ui.motion.LocalReducedMotion

@Composable
fun RecetarioTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val colors = paletteFor(choice)
    val shapes = shapesFor(choice)
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val colorScheme = if (choice == ThemeChoice.NOCTURNO) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.soft, onSurfaceVariant = colors.muted,
            outline = colors.muted, outlineVariant = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.soft, onSurfaceVariant = colors.muted,
            outline = colors.muted, outlineVariant = colors.line,
        )
    }
    val r = shapes.radius
    CompositionLocalProvider(
        LocalRecetarioColors provides colors,
        LocalRecetarioShapes provides shapes,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographyFor(choice),
            shapes = Shapes(
                small = RoundedCornerShape(r / 2),
                medium = RoundedCornerShape(r),
                large = RoundedCornerShape(r),
            ),
            content = content,
        )
    }
}
