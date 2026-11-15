package app.recetario.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.theme.LocalRecetarioColors

/** Título grande de pantalla (25 sp, letra de títulos del tema). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = LocalRecetarioColors.current.ink,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 25.sp, lineHeight = 30.sp),
    )
}

/** Título de sección (17 sp, letra de títulos del tema). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = LocalRecetarioColors.current.ink,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Rótulo pequeño encima de un grupo de opciones del editor o de los filtros. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = LocalRecetarioColors.current.muted,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Texto secundario (gris del tema). */
@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, fontSizeSp: Int = 13) {
    Text(
        text,
        modifier = modifier,
        color = LocalRecetarioColors.current.muted,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSizeSp.sp),
    )
}
