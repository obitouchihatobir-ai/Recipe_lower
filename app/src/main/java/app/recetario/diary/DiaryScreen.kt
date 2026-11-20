package app.recetario.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.rememberEntranceTracker
import app.recetario.core.ui.components.staggeredEntrance
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.PhotoStore
import app.recetario.settings.SettingsPage
import kotlinx.coroutines.launch

@Composable
fun DiaryRoute(container: AppContainer, onBack: () -> Unit, onRecipe: (String) -> Unit) {
    val vm: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.editor.draft.collectAsStateWithLifecycle()
    val actions = rememberLogFormActions(vm.editor)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    DiaryContent(
        state = state,
        photoStore = container.photoStore,
        snackbar = snackbar,
        onBack = onBack,
        onRecipe = onRecipe,
        onEdit = vm::edit,
        draft = draft,
        actions = actions,
        onDelete = { id ->
            scope.launch {
                val removed = vm.delete(id) ?: return@launch
                snackbar.currentSnackbarData?.dismiss()
                // «Deshacer» dura 4 s; después (o al salir de la pantalla) se borran sus fotos.
                val result = snackbar.showSnackbar("Se borró la entrada del diario", actionLabel = "Deshacer", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) vm.restore(removed) else vm.purge(removed)
            }
        },
    )
}

/** Diario sin ViewModel: cronología por meses (el más reciente arriba); tocar una entrada la edita o la borra. */
@Composable
fun DiaryContent(
    state: DiaryState,
    photoStore: PhotoStore,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRecipe: (String) -> Unit,
    onEdit: (CookLogEntry) -> Unit,
    draft: LogDraft?,
    actions: LogFormActions,
    onDelete: (String) -> Unit,
) {
    val tracker = rememberEntranceTracker()
    SettingsPage(
        title = "Diario",
        onBack = onBack,
        tag = "diary-screen",
        snackbar = snackbar,
        overlay = {
            if (draft != null) {
                val id = draft.entryId
                LogEntryPanel(
                    title = "Editar la entrada",
                    draft = draft,
                    today = state.today,
                    photoStore = photoStore,
                    actions = actions,
                    onDelete = id?.let { { onDelete(it) } },
                )
            }
        },
    ) {
        when {
            !state.loaded -> Unit
            state.months.isEmpty() -> EmptyDiary()
            else -> LazyColumn(
                Modifier.fillMaxSize().testTag("diary-list"),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                var position = 0
                for ((month, rows) in state.months) {
                    val monthKey = "month-$month"
                    // Entrada escalonada: solo cuentan las primeras (el retraso se corta en 6).
                    val monthIndex = position
                    position += 1 + rows.size
                    item(key = monthKey) {
                        Text(
                            monthTitle(month),
                            color = LocalRecetarioColors.current.ink,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .staggeredEntrance(monthIndex, monthKey, tracker)
                                .semantics { heading() }
                                .testTag(monthKey),
                        )
                    }
                    itemsIndexed(rows, key = { _, row -> "entry-${row.entry.id}" }) { i, row ->
                        DiaryEntryRow(
                            row, photoStore,
                            onEdit = { onEdit(row.entry) },
                            onRecipe = onRecipe,
                            modifier = Modifier.staggeredEntrance(monthIndex + 1 + i, "entry-${row.entry.id}", tracker),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryEntryRow(
    row: DiaryRow,
    photoStore: PhotoStore,
    onEdit: () -> Unit,
    onRecipe: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val entry = row.entry
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClickLabel = "Editar o borrar", onClick = onEdit)
            .padding(10.dp)
            .testTag("diary-entry-${entry.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PhotoOrPlaceholder(
            row.photo, photoStore, row.emoji,
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            emojiSize = 28.sp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.recipeTitle,
                color = c.ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("diary-title-${entry.id}"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MutedText(entryDateLabel(entry.date))
                if (entry.rating != null) {
                    Spacer(Modifier.width(8.dp))
                    RatingStars(entry.rating)
                }
            }
            if (row.orphan) MutedText("La receta ya no está en el recetario")
            if (entry.note.isNotBlank()) {
                Text(
                    entry.note,
                    color = c.ink,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val recipeId = entry.recipeId
        if (recipeId != null) {
            TextButton(
                onClick = { onRecipe(recipeId) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("diary-open-${entry.id}"),
            ) { Text("Ver receta", color = c.accent) }
        }
    }
}

@Composable
private fun EmptyDiary() {
    val c = LocalRecetarioColors.current
    Column(
        Modifier.fillMaxWidth().padding(top = 72.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📔", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Tu diario está vacío", color = c.ink, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        MutedText("Al terminar una receta en el modo cocina, o con «Lo hice» en su ficha, la apuntas aquí.", fontSizeSp = 14)
    }
}
