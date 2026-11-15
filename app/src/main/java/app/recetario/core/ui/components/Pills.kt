package app.recetario.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.theme.LocalRecetarioColors

/**
 * Etiqueta redondeada. Mide 32 dp de alto como mínimo; si es pulsable ([onClick]),
 * su zona táctil llega a 48 dp.
 */
@Composable
fun TagPill(
    text: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalRecetarioColors.current
    val pill: @Composable () -> Unit = {
        Surface(
            shape = CircleShape,
            color = if (accent) c.soft else c.surface,
            contentColor = if (accent) c.accent else c.ink,
            border = if (accent) null else BorderStroke(1.dp, c.line),
        ) {
            Box(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(text, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (onClick == null) {
        Box(modifier) { pill() }
    } else {
        Box(
            modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { pill() }
    }
}
