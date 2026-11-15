package app.recetario.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors

/**
 * Chip redondeado que se marca y desmarca (categorías, filtros, opciones del editor). Encendido usa el acento;
 * apagado, la superficie con borde. Mide 34 dp de alto y su zona táctil, 48 dp. Al pulsarlo se encoge un poco.
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Checkbox,
    leading: String? = null,
    stateDescription: String? = null,
    trailing: (@Composable (Color) -> Unit)? = null,
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val bg by animateColorAsState(if (selected) c.accent else c.surface, if (reduced) snap() else tween(200), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.muted, if (reduced) snap() else tween(200), label = "chipFg")
    val line by animateColorAsState(if (selected) c.accent else c.line, if (reduced) snap() else tween(200), label = "chipLine")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) 0.95f else 1f, tween(150), label = "chipScale")
    Box(
        modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = role, onClick = onClick)
            .semantics { if (stateDescription != null) this.stateDescription = stateDescription },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            shape = CircleShape,
            color = bg,
            contentColor = fg,
            border = BorderStroke(1.dp, line),
        ) {
            Row(
                Modifier.heightIn(min = 34.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (leading != null) Text(leading, fontSize = 14.sp)
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing?.invoke(fg)
            }
        }
    }
}

/** Colores del interruptor con los tokens del tema (apagado: borde y bolita en gris secundario). */
@Composable
fun appSwitchColors(): SwitchColors {
    val c = LocalRecetarioColors.current
    return SwitchDefaults.colors(
        checkedThumbColor = c.onAccent,
        checkedTrackColor = c.accent,
        checkedBorderColor = c.accent,
        uncheckedThumbColor = c.muted,
        uncheckedTrackColor = c.surface,
        uncheckedBorderColor = c.muted,
    )
}
