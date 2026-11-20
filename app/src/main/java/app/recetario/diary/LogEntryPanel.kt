package app.recetario.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.components.AppTextField
import app.recetario.core.ui.components.DrawnStar
import app.recetario.core.ui.components.DrawnStars
import app.recetario.core.ui.components.FieldLabel
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.editor.PhotoSource
import app.recetario.recipes.editor.PhotoTarget
import app.recetario.recipes.editor.rememberPhotoRequester
import app.recetario.settings.BottomPanel
import java.time.LocalDate

/** Lo que se puede hacer en el formulario de una entrada del diario. */
class LogFormActions(
    val onRate: (Int) -> Unit,
    val onNote: (String) -> Unit,
    val onShiftDate: (Long) -> Unit,
    val onRemovePhoto: (String) -> Unit,
    val onPickPhoto: (PhotoSource) -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * Acciones del formulario conectadas a [editor]; las fotos llegan de la cámara o de la galería como en el editor de
 * recetas (el resultado se entrega aunque Android recree la pantalla).
 */
@Composable
fun rememberLogFormActions(editor: LogEntryEditor): LogFormActions {
    val photos = rememberPhotoRequester(
        editorReady = true,
        draftPending = false,
        onPicked = { stream, _ -> editor.importPhoto(stream) },
        onError = editor::showError,
    )
    return remember(editor, photos) {
        LogFormActions(
            onRate = editor::rate,
            onNote = editor::setNote,
            onShiftDate = editor::shiftDate,
            onRemovePhoto = editor::removePhoto,
            // El destino «portada» del editor aquí significa «una foto más de la entrada».
            onPickPhoto = { source -> photos.request(PhotoTarget.Cover, source) },
            onSave = { editor.save() },
            onCancel = editor::cancel,
        )
    }
}

/**
 * Formulario de una entrada: fecha (si [dateEditable]; nunca después de [today]), estrellas opcionales, nota y hasta 4
 * fotos, con «Guardar» y [cancelLabel]. [onDelete] añade «Borrar» (al editar desde el Diario).
 */
@Composable
fun LogEntryForm(
    draft: LogDraft,
    today: LocalDate,
    photoStore: PhotoStore,
    actions: LogFormActions,
    modifier: Modifier = Modifier,
    dateEditable: Boolean = true,
    cancelLabel: String = "Cancelar",
    onDelete: (() -> Unit)? = null,
) {
    val c = LocalRecetarioColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dateEditable) {
            DateStepper(draft.date, today, actions.onShiftDate)
        }
        StarRating(draft.rating, actions.onRate)
        AppTextField(
            value = draft.note,
            onValueChange = actions.onNote,
            label = "Nota (opcional)",
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("diary-note"),
        )
        LogPhotos(draft, photoStore, actions)
        draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp).testTag("log-delete")) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag("diary-cancel")) {
                Text(cancelLabel, color = c.accent)
            }
            Button(
                onClick = actions.onSave,
                modifier = Modifier.heightIn(min = 48.dp).testTag("diary-save"),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Guardar", fontWeight = FontWeight.W700) }
        }
    }
}

/** El formulario en un panel inferior (el detalle y el Diario); se desplaza si no cabe. */
@Composable
fun BoxScope.LogEntryPanel(
    title: String,
    draft: LogDraft,
    today: LocalDate,
    photoStore: PhotoStore,
    actions: LogFormActions,
    onDelete: (() -> Unit)? = null,
) {
    BottomPanel(onDismiss = actions.onCancel, tag = "log-panel") {
        // El panel no pasa del alto de la pantalla: si no cabe, se desplaza.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle(title)
            MutedText(draft.recipeTitle, Modifier.padding(top = 2.dp, bottom = 8.dp))
            LogEntryForm(draft, today, photoStore, actions, onDelete = onDelete)
        }
    }
}

/** «‹ Hoy ›»: un día antes o después; no se puede pasar de hoy. */
@Composable
private fun DateStepper(date: LocalDate, today: LocalDate, onShift: (Long) -> Unit) {
    val c = LocalRecetarioColors.current
    Column {
        FieldLabel("Fecha")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }, modifier = Modifier.testTag("log-date-previous")) {
                Text("‹", color = c.ink, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics { contentDescription = "Día anterior" })
            }
            Text(
                logDateLabel(date, today),
                color = c.ink,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).testTag("log-date"),
            )
            IconButton(
                onClick = { onShift(1) },
                enabled = date < today,
                modifier = Modifier.testTag("log-date-next"),
            ) {
                Text(
                    "›",
                    color = if (date < today) c.ink else c.line,
                    fontSize = 24.sp,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Día siguiente" },
                )
            }
        }
    }
}

/**
 * Cinco estrellas de 48 dp; tocar la elegida otra vez quita la valoración. TalkBack lee cada una («4 estrellas»,
 * marcada o sin marcar) y, junto al rótulo, la valoración en palabras («4 de 5 estrellas»).
 */
@Composable
fun StarRating(rating: Int?, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Valoración (opcional)")
            Spacer(Modifier.width(8.dp))
            MutedText(ratingDescription(rating), Modifier.testTag("rating-description"))
        }
        Row {
            for (n in 1..5) {
                val on = rating != null && n <= rating
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button) { onRate(n) }
                        .semantics {
                            contentDescription = if (n == 1) "1 estrella" else "$n estrellas"
                            stateDescription = if (on) "Marcada" else "Sin marcar"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DrawnStar(if (on) 1f else 0f, if (on) c.accent else c.muted, Modifier.size(28.dp).clearAndSetSemantics {})
                }
            }
        }
    }
}

/** Estrellas dibujadas de una entrada en una lista, leídas como «4 de 5 estrellas»; nada sin valoración. */
@Composable
fun RatingStars(rating: Int?, modifier: Modifier = Modifier) {
    if (rating == null) return
    DrawnStars(
        ratingFills(rating),
        LocalRecetarioColors.current.accent,
        modifier.semantics(mergeDescendants = true) { contentDescription = ratingDescription(rating) },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LogPhotos(draft: LogDraft, photoStore: PhotoStore, actions: LogFormActions) {
    val c = LocalRecetarioColors.current
    // FlowRow: con 3 fotos en 360 dp, «Añadir foto» pasa a la línea siguiente en vez de aplastarse.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        draft.photos.forEachIndexed { i, name ->
            Box(Modifier.size(64.dp)) {
                PhotoOrPlaceholder(
                    name, photoStore, "📷",
                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    contentDescription = "Foto ${i + 1}",
                )
                Surface(
                    onClick = { actions.onRemovePhoto(name) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp),
                    shape = CircleShape,
                    color = c.ink,
                    contentColor = c.bg,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Close, contentDescription = "Quitar la foto ${i + 1}", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        if (draft.canAddPhoto) {
            Box {
                var open by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .background(c.soft)
                        .clickable(role = Role.Button) { open = true }
                        .padding(horizontal = 14.dp)
                        .testTag("diary-add-photo"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Camera, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir foto", color = c.accent, style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = c.surface) {
                    DropdownMenuItem(text = { Text("Hacer una foto", color = c.ink) }, onClick = {
                        open = false
                        actions.onPickPhoto(PhotoSource.CAMERA)
                    })
                    DropdownMenuItem(text = { Text("Elegir de la galería", color = c.ink) }, onClick = {
                        open = false
                        actions.onPickPhoto(PhotoSource.GALLERY)
                    })
                }
            }
        }
    }
}
