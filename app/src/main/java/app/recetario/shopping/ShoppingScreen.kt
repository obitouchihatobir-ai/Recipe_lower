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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import app.recetario.pantry.PantryRoute
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.parseQuantity
import kotlinx.coroutines.launch

/** Pestañas internas de Compra (diseño general §6.3); se abre siempre en Lista. */
enum class ShoppingTab(val label: String, val tag: String) {
    LIST("Lista", "shopping-tab-list"),
    PANTRY("Despensa", "shopping-tab-pantry"),
    WEEK("Semana", "shopping-tab-week"),
}

/** Compra: Lista · Despensa · Semana. [onWhatCanIMake] abre «¿Qué puedo hacer?» desde la Despensa. */
@Composable
fun ShoppingRoute(container: AppContainer, onWhatCanIMake: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(ShoppingTab.LIST) }
    val tabs: @Composable () -> Unit = { ShoppingTabs(tab, onSelect = { tab = it }) }
    when (tab) {
        ShoppingTab.LIST -> ShoppingListRoute(container, tabs)
        ShoppingTab.PANTRY -> PantryRoute(container, tabs, onWhatCanIMake)
        ShoppingTab.WEEK -> WeekPlaceholder(tabs)
    }
}

/** Las pestañas internas: la elegida, en el acento y subrayada (sin transparencias). */
@Composable
fun ShoppingTabs(selected: ShoppingTab, onSelect: (ShoppingTab) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp).selectableGroup()) {
        for (tab in ShoppingTab.entries) {
            val on = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                    .testTag(tab.tag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    tab.label,
                    color = if (on) c.accent else c.muted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) c.accent else c.line))
            }
        }
    }
}

/** Página de una pestaña de Compra que no es la Lista: título, pestañas, contenido y avisos. */
@Composable
fun ShoppingTabPage(
    tabs: @Composable () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalRecetarioColors.current
    Box(modifier.fillMaxSize().background(c.bg).testTag(tag)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.heightIn(min = 48.dp).padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Compra", Modifier.weight(1f))
            }
            tabs()
            content()
        }
        if (snackbar != null) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

@Composable
private fun WeekPlaceholder(tabs: @Composable () -> Unit) {
    ShoppingTabPage(tabs, tag = "shopping-week") {
        Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Llega pronto: el menú de la semana",
                color = LocalRecetarioColors.current.muted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ShoppingListRoute(container: AppContainer, tabs: @Composable () -> Unit) {
    val vm: ShoppingViewModel = viewModel(factory = ShoppingViewModel.factory(container.shoppingRepository, container.pantryRepository))
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

    /** Al marcar como comprado: aviso de 4 s con «A la despensa». */
    fun offerPantry(name: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("«$name» comprado", actionLabel = PANTRY_ACTION, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && !vm.addToPantry(name)) snackbar.showSnackbar("Ya está en la despensa")
        }
    }

    ShoppingContent(
        state = state,
        snackbar = snackbar,
        tabs = tabs,
        onToggle = { id ->
            val item = state.groups.firstNotNullOfOrNull { (_, list) -> list.firstOrNull { it.id == id } }
            vm.toggle(id)
            when {
                item == null -> Unit
                !item.checked -> offerPantry(item.name)
                // Al desmarcar, el aviso de «A la despensa» ya no viene a cuento.
                else -> snackbar.currentSnackbarData?.takeIf { it.visuals.actionLabel == PANTRY_ACTION }?.dismiss()
            }
        },
        onDelete = { id ->
            scope.launch {
                vm.delete(id)?.let { removed -> offerUndo("Se eliminó «${removed.name}»", listOf(removed)) }
            }
        },
        onSetSection = vm::setSection,
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
    onDelete: (String) -> Unit,
    onSetSection: (String, ShopSection) -> Unit,
    onClearChecked: () -> Unit,
    onShare: () -> Unit,
    onAdd: (name: String, quantity: Double?, unit: MeasureUnit) -> Unit,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit = {},
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val tracker = rememberEntranceTracker()
    var adding by rememberSaveable { mutableStateOf(false) }
    // Lo escrito vive aquí, no en el panel: Atrás cierra el panel y se conserva; solo «Añadir» lo borra.
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftQuantity by rememberSaveable { mutableStateOf("") }
    var draftUnit by rememberSaveable { mutableStateOf(MeasureUnit.PIECE.name) }
    Box(modifier.fillMaxSize().background(c.bg).testTag("shopping-screen")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.heightIn(min = 48.dp).padding(start = 18.dp, end = 6.dp, top = 6.dp).staggeredEntrance(0, "header", tracker),
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
            tabs()
            if (state.loaded && state.isEmpty) {
                EmptyShopping()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("shopping-list"),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 96.dp),
                ) {
                    var index = 1
                    for ((section, list) in shoppingGroups(state.groups)) {
                        val headerIndex = index++
                        val headerKey = "header-${section?.name ?: BOUGHT_KEY}"
                        item(key = headerKey) {
                            SectionTitle(
                                section?.label ?: BOUGHT_LABEL,
                                Modifier
                                    .animateItem(fadeInSpec = null, placementSpec = placement(reduced), fadeOutSpec = null)
                                    .padding(top = 14.dp, bottom = 4.dp)
                                    .staggeredEntrance(headerIndex, headerKey, tracker),
                            )
                        }
                        val firstIndex = index
                        index += list.size
                        itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                            ShoppingRow(
                                item = item,
                                inPantry = item.id in state.inPantry,
                                onToggle = { onToggle(item.id) },
                                onDelete = { onDelete(item.id) },
                                onSetSection = { onSetSection(item.id, it) },
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
                name = draftName,
                onName = { draftName = it },
                quantityText = draftQuantity,
                onQuantityText = { draftQuantity = it },
                unitName = draftUnit,
                onUnitName = { draftUnit = it },
                onDismiss = { adding = false },
                onConfirm = { name, quantity, unit ->
                    onAdd(name, quantity, unit)
                    draftName = ""
                    draftQuantity = ""
                    draftUnit = MeasureUnit.PIECE.name
                    adding = false
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private const val PANTRY_ACTION = "A la despensa"
private const val BOUGHT_KEY = "BOUGHT"
private const val BOUGHT_LABEL = "Comprado"

/**
 * Lo que falta, por sección del súper, y al final «Comprado» (sección null) con todo lo marcado. Al marcar un
 * artículo, su fila (misma clave) se desliza hasta «Comprado» con `animateItem`; al desmarcarlo, vuelve.
 */
internal fun shoppingGroups(groups: List<Pair<ShopSection, List<ShoppingItem>>>): List<Pair<ShopSection?, List<ShoppingItem>>> {
    val pending = groups.mapNotNull { (section, list) -> list.filter { !it.checked }.takeIf { it.isNotEmpty() }?.let { section to it } }
    val bought = groups.flatMap { (_, list) -> list.filter { it.checked } }
    return pending + if (bought.isEmpty()) emptyList() else listOf(null to bought)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    inPantry: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSetSection: (ShopSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    // El estado del deslizar conserva su primera lambda: siempre se llama a la última.
    val latestDelete by rememberUpdatedState(onDelete)
    var menu by remember { mutableStateOf(false) }
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                latestDelete()
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
        Box(Modifier.background(c.bg)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .combinedClickable(
                        role = Role.Checkbox,
                        onClickLabel = if (item.checked) "Desmarcar" else "Marcar",
                        onLongClickLabel = "Cambiar de sección",
                        onClick = onToggle,
                        onLongClick = { menu = true },
                    )
                    .semantics {
                        toggleableState = ToggleableState(item.checked)
                        customActions = listOf(
                            CustomAccessibilityAction("Borrar") { latestDelete(); true },
                            CustomAccessibilityAction("Cambiar de sección") { menu = true; true },
                        )
                    }
                    .testTag("shopping-item-${item.id}")
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedCheck(item.checked)
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        color = if (item.checked) c.muted else c.ink,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                    )
                    // Ya en la despensa: en `muted`, opaco (contraste ≥ 4,5:1).
                    if (inPantry) MutedText("ya lo tienes", Modifier.testTag("in-pantry-${item.id}"))
                }
                if (quantity.isNotEmpty()) MutedText(quantity, fontSizeSp = 14)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                for (section in ShopSection.entries) {
                    DropdownMenuItem(
                        text = {
                            Text(section.label, color = if (section == item.section) c.accent else c.ink)
                        },
                        onClick = {
                            menu = false
                            if (section != item.section) onSetSection(section)
                        },
                        modifier = Modifier.testTag("section-option-${section.name}"),
                    )
                }
            }
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
    name: String,
    onName: (String) -> Unit,
    quantityText: String,
    onQuantityText: (String) -> Unit,
    unitName: String,
    onUnitName: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, MeasureUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val unit = MeasureUnit.valueOf(unitName)
    val nameFocus = remember { FocusRequester() }
    // Atrás cierra el panel (lo escrito se conserva) en vez de salir de la pestaña.
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
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
            onValueChange = onName,
            label = "Nombre",
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).testTag("shopping-add-name"),
        )
        AppTextField(
            value = quantityText,
            onValueChange = onQuantityText,
            label = "Cantidad (opcional)",
            modifier = Modifier.fillMaxWidth().testTag("shopping-add-quantity"),
            error = if (quantityInvalid) "No se entiende la cantidad" else null,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(manualUnits) { u ->
                ChoiceChip(unitLabel(u), selected = u == unit, onClick = { onUnitName(u.name) }, role = Role.RadioButton)
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
