package app.recetario.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.shopping.ShoppingTabPage
import kotlinx.coroutines.launch

/** Pestaña Despensa de Compra (§7.2): añadir con autocompletado, la lista alfabética y «¿Qué puedo hacer?». */
@Composable
fun PantryRoute(container: AppContainer, tabs: @Composable () -> Unit, onWhatCanIMake: () -> Unit) {
    val vm: PantryViewModel = viewModel(factory = PantryViewModel.factory(container.pantryRepository, container.recipeRepository))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }

    fun add(name: String) {
        scope.launch {
            if (vm.add(name)) {
                query = ""
            } else if (pantryKey(name).isNotEmpty()) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("Ya está en la despensa")
            }
        }
    }

    PantryContent(
        state = state,
        query = query,
        onQuery = { query = it },
        onAdd = ::add,
        onDelete = { id ->
            scope.launch {
                val removed = vm.delete(id) ?: return@launch
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar("Se quitó «${removed.name}»", actionLabel = "Deshacer", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) vm.restore(removed)
            }
        },
        onWhatCanIMake = onWhatCanIMake,
        tabs = tabs,
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryContent(
    state: PantryState,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onWhatCanIMake: () -> Unit,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
) {
    val c = LocalRecetarioColors.current
    val suggestions = remember(query, state.items, state.ingredientNames) {
        pantrySuggestions(query, state.ingredientNames, state.items.map { it.name })
    }
    // Una entrada sin letras ni números («!!!») no tiene nombre que guardar: se ignora (sin «Ya está en la despensa»).
    val canAdd = pantryKey(query).isNotEmpty()
    ShoppingTabPage(tabs, tag = "pantry-screen", modifier = modifier, snackbar = snackbar) {
        Column(
            Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().testTag("pantry-field"),
                label = { Text("Añadir a la despensa") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd(query) }),
                trailingIcon = {
                    TextButton(
                        onClick = { onAdd(query) },
                        enabled = canAdd,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("pantry-add"),
                    ) { Text("Añadir", color = if (canAdd) c.accent else c.muted) }
                },
                colors = appTextFieldColors(),
            )
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (name in suggestions) {
                        ChoiceChip(
                            name,
                            selected = false,
                            onClick = { onAdd(name) },
                            role = Role.Button,
                            leading = "+",
                            modifier = Modifier.testTag("pantry-suggestion-$name"),
                        )
                    }
                }
            }
            Button(
                onClick = onWhatCanIMake,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pantry-what-can-i-make"),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("¿Qué puedo hacer?") }
            when {
                !state.loaded -> Unit
                state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Tu despensa está vacía. Añade lo que tienes en casa.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("pantry-empty"),
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize().testTag("pantry-list"), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pantry-item-${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.name, Modifier.weight(1f), color = c.ink, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(AppIcons.Close, contentDescription = "Quitar «${item.name}»", tint = c.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}
