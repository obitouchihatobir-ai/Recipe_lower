package app.recetario.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.theme.LocalRecetarioColors

internal fun servingsText(n: Int): String = if (n == 1) "1 porción" else "$n porciones"

@Composable
fun ServingsStepper(value: Int, onChange: (Int) -> Unit, range: IntRange = 1..24, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Surface(modifier, shape = CircleShape, color = c.surface, border = BorderStroke(1.dp, c.line)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onChange((value - 1).coerceIn(range)) },
                enabled = value > range.first,
                modifier = Modifier.semantics { contentDescription = "Quitar una porción" },
            ) { Text("−", style = MaterialTheme.typography.titleMedium) }
            Text(
                servingsText(value),
                modifier = Modifier.widthIn(min = 72.dp),
                textAlign = TextAlign.Center,
                color = c.ink,
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(
                onClick = { onChange((value + 1).coerceIn(range)) },
                enabled = value < range.last,
                modifier = Modifier.semantics { contentDescription = "Añadir una porción" },
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
