package app.recetario.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.text.normalize
import app.recetario.core.ui.components.AppTextField
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.theme.LocalRecetarioColors
import kotlinx.coroutines.launch

private sealed interface TagPanel {
    data class Rename(val tag: String) : TagPanel
    data class Delete(val tag: String) : TagPanel
}

@Composable
fun TagsRoute(container: AppContainer, onBack: () -> Unit) {
    val repo = container.recipeRepository
    val tags by repo.observeTags().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    TagsContent(
        tags = tags,
        onBack = onBack,
        onRename = { old, new -> scope.launch { repo.renameTag(old, new) } },
        onDelete = { tag -> scope.launch { repo.deleteTag(tag) } },
    )
}

@Composable
fun TagsContent(
    tags: List<String>?,
    onBack: () -> Unit,
    onRename: (old: String, new: String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    var panel by remember { mutableStateOf<TagPanel?>(null) }
    SettingsPage(
        title = "Etiquetas",
        onBack = onBack,
        tag = "tags-screen",
        modifier = modifier,
        overlay = {
            when (val p = panel) {
                is TagPanel.Rename -> RenameTagPanel(
                    tag = p.tag,
                    existing = tags.orEmpty(),
                    onDismiss = { panel = null },
                    onConfirm = { new -> onRename(p.tag, new); panel = null },
                )
                is TagPanel.Delete -> DeleteTagPanel(p.tag, onDismiss = { panel = null }, onConfirm = { onDelete(p.tag); panel = null })
                null -> Unit
            }
        },
    ) {
        when {
            tags == null -> Unit
            tags.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 32.dp).testTag("tags-empty"), contentAlignment = Alignment.Center) {
                MutedText("Aún no hay etiquetas. Aparecen cuando las pones en una receta.", fontSizeSp = 14)
            }
            else -> LazyColumn(Modifier.fillMaxSize().testTag("tags-list"), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                items(tags, key = { it }) { tag ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 18.dp, end = 6.dp).testTag("tag-row-$tag"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SectionTitle(tag, Modifier.weight(1f))
                        TextButton(onClick = { panel = TagPanel.Rename(tag) }, modifier = Modifier.testTag("tag-rename-$tag")) {
                            Text("Renombrar", color = c.accent)
                        }
                        TextButton(onClick = { panel = TagPanel.Delete(tag) }, modifier = Modifier.testTag("tag-delete-$tag")) {
                            Text("Borrar", color = c.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.RenameTagPanel(tag: String, existing: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = LocalRecetarioColors.current
    var name by remember(tag) { mutableStateOf(tag) }
    val key = normalize(name.trim())
    val merges = key.isNotEmpty() && key != normalize(tag) && existing.any { normalize(it) == key }
    val canSave = key.isNotEmpty()
    BottomPanel(onDismiss, "tag-rename-panel") {
        SectionTitle("Renombrar «$tag»")
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre",
            modifier = Modifier.fillMaxWidth().testTag("tag-rename-field"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (merges) MutedText("Ya existe una etiqueta con ese nombre: se juntarán en una sola.", Modifier.testTag("tag-merge-hint"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = c.muted) }
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = canSave,
                modifier = Modifier.heightIn(min = 48.dp).testTag("tag-rename-confirm"),
            ) { Text(if (merges) "Juntar" else "Guardar", color = if (canSave) c.accent else c.muted) }
        }
    }
}

@Composable
private fun BoxScope.DeleteTagPanel(tag: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalRecetarioColors.current
    BottomPanel(onDismiss, "tag-delete-panel") {
        SectionTitle("¿Borrar «$tag»?")
        MutedText("Se quitará de todas las recetas que la tengan. Las recetas no se borran.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = c.muted) }
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp).testTag("tag-delete-confirm")) {
                Text("Borrar", color = c.accent)
            }
        }
    }
}
