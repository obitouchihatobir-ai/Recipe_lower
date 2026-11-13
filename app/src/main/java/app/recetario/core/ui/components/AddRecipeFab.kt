package app.recetario.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes

/** Botón «+» para crear una receta: al pulsarlo se encoge y gira 90° (200 ms). */
@Composable
fun AddRecipeFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(if (pressed && !reduced) 1f else 0f, tween(200), label = "fabPress")
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            val s = 1f - 0.08f * progress
            scaleX = s
            scaleY = s
            rotationZ = 90f * progress
        },
        shape = RoundedCornerShape(LocalRecetarioShapes.current.radius),
        containerColor = c.accent,
        contentColor = c.onAccent,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
        interactionSource = interaction,
    ) {
        Icon(AppIcons.Plus, contentDescription = "Nueva receta", modifier = Modifier.size(26.dp))
    }
}
