package app.recetario.recipes.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.components.AppTextField
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.FieldLabel
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.list.formatDurationSeconds
import app.recetario.recipes.list.unitLabel
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.quantity.MeasureUnit

// Partes de cada paso del editor: temporizador sugerido, ingredientes que usa y fotos (hasta 4).

private const val MAX_STEP_PHOTOS = 4

/** Sin temporizador, si el texto dice «20 min» se ofrece crearlo (no se crea solo). */
@Composable
internal fun StepTimer(step: StepDraft, onEvent: (EditorEvent) -> Unit) {
    val c = LocalRecetarioColors.current
    val seconds = step.timerSeconds
    if (seconds != null) {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = c.soft, contentColor = c.accent) {
                Text(
                    "⏱ ${formatDurationSeconds(seconds)}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            IconButton(onClick = { onEvent(EditorEvent.SetStepTimer(step.id, null)) }) {
                Icon(AppIcons.Close, contentDescription = "Quitar temporizador", tint = c.muted, modifier = Modifier.size(18.dp))
            }
        }
    } else {
        val detected = remember(step.text) { detectDurationSeconds(step.text) }
        if (detected != null) {
            ChoiceChip(
                "⏱ Añadir temporizador de ${formatDurationSeconds(detected)}",
                selected = false,
                role = Role.Button,
                onClick = { onEvent(EditorEvent.SetStepTimer(step.id, detected)) },
            )
        }
    }
}

private fun IngredientDraft.toIngredient() =
    Ingredient(id = id, section = null, name = name, quantity = null, unit = unit, customUnit = null, note = "")

/** Chips de los ingredientes que usa el paso: los sugeridos aparecen apagados y se encienden al tocarlos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StepIngredients(step: StepDraft, ingredients: List<IngredientDraft>, onEvent: (EditorEvent) -> Unit) {
    val named = ingredients.filter { it.name.isNotBlank() }
    if (named.isEmpty()) return
    val suggested = remember(step.text, named) { suggestStepIngredients(step.text, named.map { it.toIngredient() }) }
    val shown = named.filter { it.id in step.ingredientIds || it.id in suggested }
    val others = named.filterNot { it in shown }
    fun toggle(id: String) = onEvent(
        EditorEvent.SetStepIngredients(step.id, if (id in step.ingredientIds) step.ingredientIds - id else step.ingredientIds + id),
    )
    Column(Modifier.padding(top = 8.dp)) {
        FieldLabel("Ingredientes que usa")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (ing in shown) ChoiceChip(ing.name, ing.id in step.ingredientIds, onClick = { toggle(ing.id) })
            if (others.isNotEmpty()) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    ChoiceChip("Otro", selected = false, role = Role.Button, leading = "+", onClick = { open = true })
                    DropdownMenu(open, onDismissRequest = { open = false }, containerColor = LocalRecetarioColors.current.surface) {
                        for (ing in others) {
                            DropdownMenuItem(text = { Text(ing.name) }, onClick = {
                                toggle(ing.id)
                                open = false
                            })
                        }
                    }
                }
            }
        }
        if (shown.any { it.id !in step.ingredientIds }) {
            MutedText("Sugeridos por el texto: tócalos para marcarlos.", fontSizeSp = 12)
        }
    }
}

@Composable
internal fun StepPhotos(
    step: StepDraft,
    photoStore: PhotoStore,
    onEvent: (EditorEvent) -> Unit,
    onPickPhoto: (PhotoSource) -> Unit,
) {
    val c = LocalRecetarioColors.current
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        step.photos.forEachIndexed { i, name ->
            Box(Modifier.size(64.dp)) {
                PhotoOrPlaceholder(
                    name, photoStore, "📷",
                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    contentDescription = "Foto ${i + 1} del paso",
                )
                Surface(
                    onClick = { onEvent(EditorEvent.RemoveStepPhoto(step.id, name)) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp),
                    shape = CircleShape,
                    color = c.ink.copy(alpha = 0.7f),
                    contentColor = c.bg,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Close, contentDescription = "Quitar la foto ${i + 1}", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        if (step.photos.size < MAX_STEP_PHOTOS) {
            Box {
                var open by remember { mutableStateOf(false) }
                Surface(
                    onClick = { open = true },
                    modifier = Modifier.size(64.dp).semantics { contentDescription = "Añadir una foto al paso" },
                    shape = RoundedCornerShape(12.dp),
                    color = c.soft,
                    contentColor = c.accent,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Foto", style = MaterialTheme.typography.labelSmall)
                    }
                }
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = c.surface) {
                    DropdownMenuItem(text = { Text("Hacer una foto") }, onClick = {
                        open = false
                        onPickPhoto(PhotoSource.CAMERA)
                    })
                    DropdownMenuItem(text = { Text("Elegir de la galería") }, onClick = {
                        open = false
                        onPickPhoto(PhotoSource.GALLERY)
                    })
                }
            }
        }
    }
}
