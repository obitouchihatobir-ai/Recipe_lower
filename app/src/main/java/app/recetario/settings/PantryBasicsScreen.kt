package app.recetario.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.pantry.samePantryName
import kotlinx.coroutines.launch

/** Ajustes › Despensa › Básicos: lo que siempre hay en casa y no cuenta en la despensa (§7.2). */
@Composable
fun PantryBasicsRoute(container: AppContainer, onBack: () -> Unit) {
    val repo = container.settingsRepository
    val basics by repo.pantryBasics.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var draft by rememberSaveable { mutableStateOf("") }

    fun save(list: List<String>) {
        scope.launch { snackbar.reportFailure("No se pudieron guardar los básicos.") { repo.setPantryBasics(list) } }
    }

    PantryBasicsContent(
        basics = basics,
        draft = draft,
        onDraft = { draft = it },
        onAdd = {
            val current = basics ?: return@PantryBasicsContent
            val name = draft.trim()
            if (name.isEmpty()) return@PantryBasicsContent
            if (current.any { samePantryName(it, name) }) {
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Ya está en los básicos")
                }
            } else {
                draft = ""
                save(current + name)
            }
        },
        onRemove = { name -> basics?.let { save(it - name) } },
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
fun PantryBasicsContent(
    basics: List<String>?,
    draft: String,
    onDraft: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
) {
    val c = LocalRecetarioColors.current
    val canAdd = draft.isNotBlank()
    SettingsPage("Básicos de la despensa", onBack, tag = "pantry-basics-screen", modifier = modifier, snackbar = snackbar) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            MutedText(
                "Lo que siempre tienes en casa. No cuenta en «¿Qué puedo hacer?» ni en el filtro «Con mi despensa».",
                Modifier.padding(top = 4.dp, bottom = 8.dp),
                fontSizeSp = 14,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.fillMaxWidth().testTag("basics-field"),
                label = { Text("Añadir un básico") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd() }),
                trailingIcon = {
                    TextButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.heightIn(min = 48.dp).testTag("basics-add")) {
                        Text("Añadir", color = if (canAdd) c.accent else c.muted)
                    }
                },
                colors = appTextFieldColors(),
            )
            when {
                basics == null -> Unit
                basics.isEmpty() -> MutedText("No hay básicos: cuentan todos los ingredientes.", Modifier.padding(top = 16.dp), fontSizeSp = 14)
                else -> LazyColumn(Modifier.fillMaxSize().testTag("basics-list"), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                    items(basics, key = { it }) { name ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), color = c.ink, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { onRemove(name) }) {
                                Icon(AppIcons.Close, contentDescription = "Quitar «$name»", tint = c.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}
