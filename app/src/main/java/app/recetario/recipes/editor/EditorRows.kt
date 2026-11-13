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

@Composable
private fun RowCard(dragging: Boolean, content: @Composable () -> Unit) {
    val c = LocalRecetarioColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalRecetarioShapes.current.radius),
        color = c.surface,
        border = BorderStroke(1.dp, if (dragging) c.accent else c.line),
        shadowElevation = if (dragging) 6.dp else 0.dp,
    ) { content() }
}

/** Asa para arrastrar; con TalkBack ofrece «Subir» y «Bajar». */
@Composable
private fun DragHandle(handle: Modifier, label: String, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit) {
    Box(
        handle
            .size(width = 40.dp, height = 48.dp)
            .semantics {
                contentDescription = "Mover $label"
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction("Subir") { onMove(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction("Bajar") { onMove(1); true })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.DragHandle, contentDescription = null, tint = LocalRecetarioColors.current.muted)
    }
}

@Composable
internal fun IngredientRow(
    draft: IngredientDraft,
    sectionTitle: String?,
    nameError: String?,
    quantityError: String?,
    dragging: Boolean,
    handle: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onChange: (IngredientDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    Column(Modifier.padding(vertical = 4.dp)) {
        if (sectionTitle != null) {
            Text(
                sectionTitle,
                color = c.accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        RowCard(dragging) {
            Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DragHandle(handle, "el ingrediente ${draft.name}".trimEnd(), canMoveUp, canMoveDown, onMove)
                    AppTextField(
                        value = draft.name,
                        onValueChange = { onChange(draft.copy(name = it)) },
                        label = "Ingrediente",
                        error = nameError,
                        modifier = Modifier.weight(1f).testTag("ingredient-name"),
                    )
                    IconButton(onClick = onRemove) {
                        Icon(AppIcons.Close, contentDescription = "Quitar el ingrediente", tint = c.muted)
                    }
                }
                var extras by rememberSaveable(draft.id) { mutableStateOf(draft.note.isNotEmpty() || draft.section.isNotEmpty()) }
                Row(
                    Modifier.padding(start = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    AppTextField(
                        value = draft.quantityText,
                        onValueChange = { onChange(draft.copy(quantityText = it)) },
                        label = "Cantidad",
                        placeholder = "1,5 o ½",
                        error = quantityError,
                        showErrorText = false,
                        modifier = Modifier.weight(0.85f).testTag("ingredient-quantity"),
                    )
                    UnitPicker(
                        unit = draft.unit,
                        onChange = { unit ->
                            // La unidad propia solo se guarda con «Otra…»: al cambiar de unidad se borra.
                            onChange(draft.copy(unit = unit, customUnit = if (unit == MeasureUnit.CUSTOM) draft.customUnit else ""))
                        },
                        modifier = Modifier.weight(1.15f),
                    )
                    // Alineado con el botón de quitar de la fila del nombre.
                    Box(Modifier.padding(top = 8.dp).size(48.dp), contentAlignment = Alignment.Center) {
                        if (!extras) {
                            IconButton(onClick = { extras = true }) {
                                Icon(AppIcons.Note, contentDescription = "Añadir nota o sección", tint = c.accent)
                            }
                        }
                    }
                }
                if (quantityError != null) ErrorText(quantityError, Modifier.padding(start = 40.dp, end = 48.dp))
                if (draft.unit == MeasureUnit.CUSTOM) {
                    AppTextField(
                        value = draft.customUnit,
                        onValueChange = { onChange(draft.copy(customUnit = it)) },
                        label = "Unidad propia",
                        placeholder = "Por ejemplo, vaso",
                        modifier = Modifier.padding(start = 40.dp, end = 48.dp).fillMaxWidth(),
                    )
                }
                if (extras) {
                    Column(Modifier.padding(start = 40.dp, end = 48.dp)) {
                        AppTextField(
                            value = draft.note,
                            onValueChange = { onChange(draft.copy(note = it)) },
                            label = "Nota",
                            placeholder = "Por ejemplo, picada",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = draft.section,
                            onValueChange = { onChange(draft.copy(section = it)) },
                            label = "Sección (opcional)",
                            placeholder = "Por ejemplo, Para la salsa",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPicker(unit: MeasureUnit, onChange: (MeasureUnit) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "Unidad: ${unitLabel(unit)}" },
            shape = RoundedCornerShape(4.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.line),
        ) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text("Unidad", color = c.muted, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall)
                    Text(
                        unitLabel(unit),
                        color = c.ink,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(AppIcons.ChevronDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            for (option in MeasureUnit.entries) {
                DropdownMenuItem(
                    text = { Text(unitLabel(option), color = if (option == unit) c.accent else c.ink) },
                    onClick = {
                        onChange(option)
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StepRow(
    step: StepDraft,
    number: Int,
    ingredients: List<IngredientDraft>,
    error: String?,
    dragging: Boolean,
    handle: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    photoStore: PhotoStore,
    onMove: (Int) -> Unit,
    onEvent: (EditorEvent) -> Unit,
    onPickPhoto: (PhotoSource) -> Unit,
) {
    val c = LocalRecetarioColors.current
    Box(Modifier.padding(vertical = 4.dp)) {
        RowCard(dragging) {
            Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DragHandle(handle, "el paso $number", canMoveUp, canMoveDown, onMove)
                    Text(
                        "PASO $number",
                        color = c.muted,
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEvent(EditorEvent.RemoveStep(step.id)) }) {
                        Icon(AppIcons.Close, contentDescription = "Quitar el paso $number", tint = c.muted)
                    }
                }
                Column(Modifier.padding(start = 10.dp, end = 10.dp)) {
                    AppTextField(
                        value = step.text,
                        onValueChange = { onEvent(EditorEvent.SetStepText(step.id, it)) },
                        label = "Qué hay que hacer",
                        error = error,
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("step-text"),
                    )
                    StepTimer(step, onEvent)
                    StepIngredients(step, ingredients, onEvent)
                    StepPhotos(step, photoStore, onEvent, onPickPhoto)
                }
            }
        }
    }
}
