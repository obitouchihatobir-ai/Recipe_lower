package app.recetario.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors

internal fun servingsText(n: Int): String = if (n == 1) "1 porción" else "$n porciones"

/** Lo que tarda el número en rodar al cambiar las porciones. */
internal const val SERVINGS_ROLL_MS = 250

@Composable
fun ServingsStepper(value: Int, onChange: (Int) -> Unit, range: IntRange = 1..99, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Surface(modifier, shape = CircleShape, color = c.surface, border = BorderStroke(1.dp, c.line)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onChange((value - 1).coerceIn(range)) },
                enabled = value > range.first,
                modifier = Modifier.semantics { contentDescription = "Quitar una porción" },
            ) { Text("−", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {}) }
            ServingsLabel(value, Modifier.widthIn(min = 72.dp))
            TextButton(
                onClick = { onChange((value + 1).coerceIn(range)) },
                enabled = value < range.last,
                modifier = Modifier.semantics { contentDescription = "Añadir una porción" },
            ) { Text("+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {}) }
        }
    }
}

/**
 * «4 porciones» con el número rodando como un contador: sube al aumentar y baja al disminuir (250 ms). TalkBack
 * solo lee el texto final (también a mitad del giro, cuando hay dos números compuestos). Sin animaciones del
 * sistema, el número cambia sin más.
 */
@Composable
private fun ServingsLabel(value: Int, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val style = MaterialTheme.typography.labelLarge
    Row(
        modifier.clearAndSetSemantics { text = AnnotatedString(servingsText(value)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (reduced) {
            Text("$value", color = c.ink, style = style)
        } else {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(SERVINGS_ROLL_MS)) { h -> if (up) h else -h } + fadeIn(tween(SERVINGS_ROLL_MS)))
                        .togetherWith(slideOutVertically(tween(SERVINGS_ROLL_MS)) { h -> if (up) -h else h } + fadeOut(tween(SERVINGS_ROLL_MS)))
                        .using(SizeTransform(clip = true))
                },
                label = "servingsRoll",
            ) { n -> Text("$n", color = c.ink, style = style, textAlign = TextAlign.Center) }
        }
        Text(if (value == 1) " porción" else " porciones", color = c.ink, style = style)
    }
}
