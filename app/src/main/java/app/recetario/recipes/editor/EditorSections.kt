package app.recetario.recipes.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.text.normalize
import app.recetario.core.ui.components.AppTextField
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.FieldLabel
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.ServingsStepper
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp

@Composable
internal fun SectionHeader(
    section: EditorSection,
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(if (reduced) 0 else 250), label = "chevron")
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                heading()
                stateDescription = if (expanded) "Desplegada" else "Plegada"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            section.title,
            color = c.ink,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp),
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            MutedText("$count")
        }
        Spacer(Modifier.weight(1f))
        Icon(
            AppIcons.ChevronDown,
            contentDescription = null,
            tint = c.muted,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
internal fun CoverPicker(
    coverPhoto: String?,
    emoji: String,
    photoStore: PhotoStore,
    onPick: (PhotoSource) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(LocalRecetarioShapes.current.radius)),
    ) {
        PhotoOrPlaceholder(coverPhoto, photoStore, emoji, Modifier.fillMaxSize(), contentDescription = "Foto de portada")
        Row(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OverlayButton("Cámara", AppIcons.Camera, "Hacer la foto de portada") { onPick(PhotoSource.CAMERA) }
            OverlayButton("Galería", AppIcons.Gallery, "Elegir la foto de portada de la galería") { onPick(PhotoSource.GALLERY) }
            if (coverPhoto != null) OverlayButton("Quitar", AppIcons.Close, "Quitar la foto de portada", onRemove)
        }
    }
}

@Composable
private fun OverlayButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        shape = CircleShape,
        color = c.surface.copy(alpha = 0.94f),
        contentColor = c.ink,
    ) {
        Row(
            Modifier.heightIn(min = 36.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipGroup(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(top = 10.dp)) {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
internal fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(start = 4.dp, top = 2.dp),
    )
}

@Composable
internal fun BasicsSection(
    state: EditorState,
    categories: List<Category>,
    allTags: List<String>,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = categories.firstOrNull { it.id == state.categoryId }?.baseType
    Column(modifier.fillMaxWidth()) {
        AppTextField(
            value = state.title,
            onValueChange = { onEvent(EditorEvent.SetTitle(it)) },
            label = "Nombre de la receta",
            error = state.errors[Field.TITLE],
            modifier = Modifier.fillMaxWidth().testTag("editor-title"),
        )
        ChipGroup("Categoría") {
            for (cat in categories) {
                ChoiceChip(
                    cat.name,
                    selected = cat.id == state.categoryId,
                    role = Role.RadioButton,
                    leading = cat.emoji,
                    onClick = { onEvent(EditorEvent.SetCategory(cat.id, cat.baseType)) },
                )
            }
        }
        state.errors[Field.CATEGORY]?.let { ErrorText(it) }
        Spacer(Modifier.height(10.dp))
        AppTextField(
            value = state.description,
            onValueChange = { onEvent(EditorEvent.SetDescription(it)) },
            label = "Descripción breve (opcional)",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = state.cookingMinutesText,
            onValueChange = { onEvent(EditorEvent.SetCookingMinutes(it)) },
            label = "Tiempo total",
            placeholder = "Por ejemplo, 35 o 1 h 30 min",
            error = state.errors[Field.MINUTES],
            modifier = Modifier.fillMaxWidth().testTag("editor-minutes"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Porciones", Modifier.weight(1f))
            ServingsStepper(state.servings, { onEvent(EditorEvent.SetServings(it)) }, range = 1..99)
        }
        ChipGroup("Dificultad") {
            for (d in Difficulty.entries) {
                ChoiceChip(d.label, state.difficulty == d, role = Role.RadioButton, onClick = { onEvent(EditorEvent.SetDifficulty(d)) })
            }
        }
        ChipGroup("Se sirve") {
            for (t in ServeTemp.entries) {
                ChoiceChip(t.label, state.serveTemp == t, role = Role.RadioButton, onClick = {
                    onEvent(EditorEvent.SetServeTemp(if (state.serveTemp == t) null else t))
                })
            }
        }
        ChipGroup("Momentos del día") {
            for (m in Moment.entries) {
                ChoiceChip(m.label, m in state.moments, onClick = { onEvent(EditorEvent.ToggleMoment(m)) })
            }
        }
        if (state.defaultsSuggested) {
            MutedText("Sugerido según la categoría: tócalo para cambiarlo.", Modifier.padding(top = 2.dp), fontSizeSp = 12)
        }
        if (base == BaseType.DRINK) {
            ChipGroup("Alcohol") {
                for ((value, label) in listOf(true to "Con alcohol", false to "Sin alcohol")) {
                    ChoiceChip(label, state.alcoholic == value, role = Role.RadioButton, onClick = {
                        onEvent(EditorEvent.SetAlcoholic(if (state.alcoholic == value) null else value))
                    })
                }
            }
        }
        if (base == BaseType.FOOD) {
            ChipGroup("Tipo de plato") {
                for (course in Course.entries) {
                    ChoiceChip(course.label, state.course == course, role = Role.RadioButton, onClick = {
                        onEvent(EditorEvent.SetCourse(if (state.course == course) null else course))
                    })
                }
            }
        }
        TagsEditor(state.tags, allTags) { onEvent(EditorEvent.SetTags(it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(tags: List<String>, allTags: List<String>, onChange: (List<String>) -> Unit) {
    val c = LocalRecetarioColors.current
    var text by rememberSaveable { mutableStateOf("") }
    fun has(tag: String) = tags.any { normalize(it) == normalize(tag) }
    fun add() {
        val fresh = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        var next = tags
        for (t in fresh) if (next.none { normalize(it) == normalize(t) }) next = next + t
        if (next != tags) onChange(next)
        text = ""
    }
    Column(Modifier.padding(top = 10.dp)) {
        FieldLabel("Etiquetas")
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tag in tags) {
                    ChoiceChip(
                        tag,
                        selected = true,
                        role = Role.Button,
                        modifier = Modifier.semantics { contentDescription = "Quitar la etiqueta $tag" },
                        onClick = { onChange(tags - tag) },
                        trailing = { tint -> Icon(AppIcons.Close, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Añadir etiqueta") },
            placeholder = { Text("Por ejemplo, Vegetariana") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = if (text.isBlank()) null else ({
                IconButton(onClick = { add() }) { Icon(AppIcons.Plus, contentDescription = "Añadir etiqueta", tint = c.accent) }
            }),
            colors = appTextFieldColors(),
        )
        val suggestions = allTags.filterNot(::has).take(8)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in suggestions) ChoiceChip(s, selected = false, role = Role.Button, leading = "+", onClick = { onChange(tags + s) })
            }
        }
    }
}

@Composable
internal fun NotesSection(state: EditorState, onEvent: (EditorEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AppTextField(
            value = state.notes,
            onValueChange = { onEvent(EditorEvent.SetNotes(it)) },
            label = "Notas",
            placeholder = "Trucos, cambios para la próxima vez…",
            singleLine = false,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = state.sourceUrl,
            onValueChange = { onEvent(EditorEvent.SetSourceUrl(it)) },
            label = "Enlace de origen (opcional)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AddRowButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalRecetarioColors.current
    TextButton(onClick = onClick, modifier = modifier.padding(top = 4.dp)) {
        Icon(AppIcons.Plus, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = c.accent, style = MaterialTheme.typography.labelLarge)
    }
}
