package app.recetario.shopping

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.AnimatedCheck
import app.recetario.core.ui.components.AppTextField
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.rememberEntranceTracker
import app.recetario.core.ui.components.staggeredEntrance
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.parseQuantity
import kotlinx.coroutines.launch

@Composable
fun ShoppingRoute(container: AppContainer) {
    val vm: ShoppingViewModel = viewModel(factory = ShoppingViewModel.factory(container.shoppingRepository))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    /** Muestra el aviso con «Deshacer» y, si se pulsa, devuelve los artículos. */
    fun offerUndo(message: String, items: List<ShoppingItem>) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Deshacer", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.restore(items)
        }
    }

    ShoppingContent(
        state = state,
        snackbar = snackbar,
        onToggle = vm::toggle,
        onDelete = { item ->
            vm.delete(item.id)
            offerUndo("Se eliminó «${item.name}»", listOf(item))
        },
        onClearChecked = {
            scope.launch {
                val removed = vm.clearChecked()
                if (removed.isNotEmpty()) {
                    offerUndo(if (removed.size == 1) "Se quitó 1 artículo marcado" else "Se quitaron ${removed.size} artículos marcados", removed)
                }
            }
        },
        onShare = { shareText(context, state.shareText) },
        onAdd = vm::add,
    )
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Compartir la lista").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sin apps para compartir: no hay nada que hacer.
    }
}

/** Compra: artículos por sección del súper, con casilla, deslizar para borrar, compartir y alta a mano (§8.3). */
@Composable
fun ShoppingContent(
    state: ShoppingState,
    snackbar: SnackbarHostState,
    onToggle: (String) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onClearChecked: () -> Unit,
    onShare: () -> Unit,
    onAdd: (name: String, quantity: Double?, unit: MeasureUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val tracker = rememberEntranceTracker()
    var adding by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(c.bg).testTag("shopping-screen")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp).staggeredEntrance(0, "header", tracker),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenTitle("Compra", Modifier.weight(1f))
                if (state.checkedCount > 0) {
                    TextButton(onClick = onClearChecked, modifier = Modifier.heightIn(min = 48.dp).testTag("shopping-clear")) {
                        Text("Vaciar marcados", color = c.accent)
                    }
                }
                if (state.shareText.isNotEmpty()) {
                    IconButton(onClick = onShare, modifier = Modifier.testTag("shopping-share")) {
                        Icon(AppIcons.Share, contentDescription = "Compartir la lista", tint = c.ink)
                    }
                }
            }
            if (state.loaded && state.isEmpty) {
                EmptyShopping()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 96.dp),
                ) {
                    var index = 1
                    for ((section, list) in state.groups) {
                        val headerIndex = index++
                        item(key = "header-${section.name}") {
                            SectionTitle(
                                section.label,
                                Modifier
                                    .animateItem(fadeInSpec = null, placementSpec = placement(reduced), fadeOutSpec = null)
                                    .padding(top = 14.dp, bottom = 4.dp)
                                    .staggeredEntrance(headerIndex, "header-${section.name}", tracker),
                            )
                        }
                        val firstIndex = index
                        index += list.size
                        itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                            ShoppingRow(
                                item = item,
                                onToggle = { onToggle(item.id) },
                                onDelete = { onDelete(item) },
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = if (reduced) null else spring(stiffness = 600f),
                                        placementSpec = placement(reduced),
                                        fadeOutSpec = if (reduced) null else spring(stiffness = 600f),
                                    )
                                    .staggeredEntrance(firstIndex + i, item.id, tracker),
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 92.dp))
        if (!adding) {
            FloatingActionButton(
                onClick = { adding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp).testTag("shopping-add"),
                shape = RoundedCornerShape(LocalRecetarioShapes.current.radius),
                containerColor = c.accent,
                contentColor = c.onAccent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
            ) {
                Icon(AppIcons.Plus, contentDescription = "Añadir un artículo", modifier = Modifier.size(26.dp))
            }
        }
        if (adding) {
            AddItemPanel(
                onDismiss = { adding = false },
                onConfirm = { name, quantity, unit ->
                    onAdd(name, quantity, unit)
                    adding = false
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun placement(reduced: Boolean) =
    if (reduced) null else spring(stiffness = 600f, visibilityThreshold = IntOffset.VisibilityThreshold)

@Composable
private fun EmptyShopping() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp).testTag("shopping-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(AppIcons.Shopping, contentDescription = null, tint = LocalRecetarioColors.current.muted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.size(12.dp))
        Text(
            "Tu lista está vacía. Desde una receta, pulsa «Lo que necesito».",
            color = LocalRecetarioColors.current.muted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(c.soft, RoundedCornerShape(LocalRecetarioShapes.current.radius)).padding(end = 18.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Borrar", color = c.accent, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        val quantity = formatQuantity(item.quantity, item.unit, item.customUnit)
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.bg)
                .heightIn(min = 48.dp)
                .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .testTag("shopping-item-${item.id}")
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedCheck(item.checked)
            Text(
                item.name,
                modifier = Modifier.weight(1f),
                color = if (item.checked) c.muted else c.ink,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            if (quantity.isNotEmpty()) MutedText(quantity, fontSizeSp = 14)
        }
    }
}

/** Unidades que se ofrecen al añadir a mano. */
private val manualUnits = listOf(
    MeasureUnit.PIECE, MeasureUnit.GRAM, MeasureUnit.KILOGRAM, MeasureUnit.MILLILITER, MeasureUnit.LITER,
    MeasureUnit.CUP, MeasureUnit.TABLESPOON, MeasureUnit.TEASPOON, MeasureUnit.CLOVE, MeasureUnit.PINCH,
)

private fun unitLabel(unit: MeasureUnit) = if (unit == MeasureUnit.PIECE) "unidades" else unit.singular

/** Panel para añadir a mano: nombre, cantidad (admite 1,5 · 1/2 · 1 1/2 · ½) y unidad. */
@Composable
private fun AddItemPanel(
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, MeasureUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    var name by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("") }
    var unitName by rememberSaveable { mutableStateOf(MeasureUnit.PIECE.name) }
    val unit = MeasureUnit.valueOf(unitName)
    val quantity = parseQuantity(quantityText)
    val quantityInvalid = quantityText.isNotBlank() && quantity == null
    Column(
        modifier
            .fillMaxWidth()
            .imePadding()
            .background(c.surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("shopping-add-panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Añadir artículo")
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre",
            modifier = Modifier.fillMaxWidth().testTag("shopping-add-name"),
        )
        AppTextField(
            value = quantityText,
            onValueChange = { quantityText = it },
            label = "Cantidad (opcional)",
            modifier = Modifier.fillMaxWidth().testTag("shopping-add-quantity"),
            error = if (quantityInvalid) "No se entiende la cantidad" else null,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(manualUnits) { u ->
                ChoiceChip(unitLabel(u), selected = u == unit, onClick = { unitName = u.name }, role = Role.RadioButton)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = c.muted) }
            TextButton(
                onClick = { onConfirm(name, quantity, unit) },
                enabled = name.isNotBlank() && !quantityInvalid,
                modifier = Modifier.heightIn(min = 48.dp).testTag("shopping-add-confirm"),
            ) { Text("Añadir", color = if (name.isNotBlank() && !quantityInvalid) c.accent else c.muted) }
        }
    }
}
