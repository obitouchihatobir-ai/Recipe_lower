package app.recetario.core.ui.theme

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
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
    val colorScheme = colorSchemeFor(choice, colors)
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

// Los roles de Material3 se enlazan a los tokens del tema para que NavigationBar, Card,
// AlertDialog y ModalBottomSheet no usen los colores por defecto.
private fun colorSchemeFor(choice: ThemeChoice, c: RecetarioColors): ColorScheme =
    if (choice == ThemeChoice.NOCTURNO) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            tertiary = c.accent, onTertiary = c.onAccent,
            secondaryContainer = c.soft, onSecondaryContainer = c.ink,
            background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.soft, onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.bg,
            surfaceContainerLow = c.surface,
            surfaceContainer = c.surface,
            surfaceContainerHigh = lerp(c.surface, c.soft, 0.5f),
            surfaceContainerHighest = c.soft,
            outline = c.line, outlineVariant = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            tertiary = c.accent, onTertiary = c.onAccent,
            secondaryContainer = c.soft, onSecondaryContainer = c.ink,
            background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.soft, onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.bg,
            surfaceContainerLow = c.surface,
            surfaceContainer = c.surface,
            surfaceContainerHigh = lerp(c.surface, c.soft, 0.5f),
            surfaceContainerHighest = c.soft,
            outline = c.line, outlineVariant = c.line,
        )
    }
