package app.recetario.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.text.normalize
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.settings.BottomPanel
import app.recetario.settings.StepperRow
import app.recetario.shopping.ShoppingTabPage
import kotlinx.coroutines.launch
import java.time.LocalDate

const val WEEK_EMPTY_TEXT = "Añade recetas para planear tu semana"
private const val UNDO = "Deshacer"

/** Etiqueta de prueba de un hueco: `week-slot-2026-11-13-COMIDA`. */
fun weekSlotTag(date: LocalDate, slot: MealSlot) = "week-slot-$date-${slot.key}"

/** El hueco abierto en el panel, guardable: «2026-11-13|COMIDA». */
private fun slotKey(date: LocalDate, slot: MealSlot) = "$date|${slot.key}"

private fun parseSlotKey(key: String): Pair<LocalDate, MealSlot>? {
    val (date, slot) = key.split('|').takeIf { it.size == 2 } ?: return null
    val s = MealSlot.entries.firstOrNull { it.key == slot } ?: return null
    return runCatching { LocalDate.parse(date) }.getOrNull()?.let { it to s }
}

/** Pestaña Semana de Compra (diseño de la etapa 2, §7.6). */
@Composable
fun WeekRoute(container: AppContainer, tabs: @Composable () -> Unit) {
    val vm: WeekViewModel = viewModel(factory = WeekViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Mientras corre «Rellenar» o «Generar lista», sus botones se apagan (dos toques no rellenan dos veces).
    var working by remember { mutableStateOf(false) }

    suspend fun notify(message: String, undo: (suspend () -> Unit)? = null) {
        snackbar.currentSnackbarData?.dismiss()
        if (undo == null) {
            snackbar.showSnackbar(message)
        } else if (snackbar.showSnackbar(message, actionLabel = UNDO, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
            undo()
        }
    }

    fun busy(block: suspend () -> Unit) {
        if (working) return
        working = true
        scope.launch {
            try {
                block()
            } finally {
                working = false
            }
        }
    }

    val selected = open?.let(::parseSlotKey)
    WeekContent(
        state = state,
        selected = selected,
        query = query,
        working = working,
        tabs = tabs,
        snackbar = snackbar,
        onPrevious = vm::previousWeek,
        onNext = vm::nextWeek,
        onOpenSlot = { date, slot ->
            query = ""
            open = slotKey(date, slot)
        },
        onDismissPanel = { open = null },
        onQuery = { query = it },
        onPick = { choice ->
            val (date, slot) = selected ?: return@WeekContent
            open = null
            scope.launch { vm.set(date, slot, choice.id, choice.servings) }
        },
        onServings = { delta ->
            val (date, slot) = selected ?: return@WeekContent
            scope.launch { vm.changeServings(date, slot, delta) }
        },
        onRemove = {
            val (date, slot) = selected ?: return@WeekContent
            val title = state.slot(date, slot)?.title
            open = null
            scope.launch {
                val removed = vm.clear(date, slot) ?: return@launch
                notify("Se quitó «${title ?: "la receta"}»") { vm.restore(removed) }
            }
        },
        onFill = {
            busy {
                val added = vm.fill()
                if (added.isEmpty()) {
                    scope.launch { notify(filledMessage(0)) }
                } else {
                    scope.launch { notify(filledMessage(added.size)) { vm.undoFill(added) } }
                }
            }
        },
        onGenerate = {
            busy {
                val count = vm.generateList()
                scope.launch { notify(weekListMessage(count)) }
            }
        },
    )
}

@Composable
fun WeekContent(
    state: WeekState,
    selected: Pair<LocalDate, MealSlot>?,
    query: String,
    working: Boolean,
    tabs: @Composable () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSlot: (LocalDate, MealSlot) -> Unit,
    onDismissPanel: () -> Unit,
    onQuery: (String) -> Unit,
    onPick: (WeekRecipeChoice) -> Unit,
    /** «−/+»: cuánto sumar a las porciones (−1 o 1). */
    onServings: (Int) -> Unit,
    onRemove: () -> Unit,
    onFill: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
) {
    val c = LocalRecetarioColors.current
    val panelSlot = selected?.let { (date, slot) -> state.slot(date, slot) ?: WeekSlotView(date, slot, null, null, null) }
    ShoppingTabPage(
        tabs,
        tag = "week-screen",
        modifier = modifier,
        snackbar = snackbar,
        overlay = {
            if (panelSlot != null && state.loaded) {
                BottomPanel(onDismissPanel, "week-panel") {
                    SlotPanel(panelSlot, state.recipes, query, onQuery, onPick, onServings, onRemove)
                }
            }
        },
    ) {
        when {
            !state.loaded -> Unit
            !state.hasRecipes -> Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    WEEK_EMPTY_TEXT,
                    color = c.muted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("week-empty"),
                )
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 8.dp, bottom = 96.dp)
                    .testTag("week-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WeekHeader(state.title, onPrevious, onNext)
                Button(
                    onClick = onFill,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("week-fill"),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                ) { Text("Rellenar con sugerencias") }
                OutlinedButton(
                    onClick = onGenerate,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("week-generate"),
                    border = BorderStroke(1.dp, c.accent),
                ) { Text("Generar lista de compra", color = c.ink) }
                for (day in state.days) DayCard(day, onOpenSlot)
            }
        }
    }
}

@Composable
private fun WeekHeader(title: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    val c = LocalRecetarioColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowButton("‹", "Semana anterior", "week-previous", onPrevious)
        Text(
            title,
            Modifier
                .weight(1f)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
                .testTag("week-title"),
            color = c.ink,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        ArrowButton("›", "Semana siguiente", "week-next", onNext)
    }
}

@Composable
private fun ArrowButton(symbol: String, description: String, tag: String, action: () -> Unit) {
    val c = LocalRecetarioColors.current
    TextButton(
        onClick = action,
        // La etiqueta va antes: `clearAndSetSemantics` descarta la semántica de lo que va detrás.
        modifier = Modifier
            .testTag(tag)
            .heightIn(min = 48.dp)
            .width(56.dp)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = null) { action(); true }
            },
    ) { Text(symbol, color = c.accent, style = MaterialTheme.typography.headlineSmall) }
}

@Composable
private fun DayCard(day: WeekDayView, onOpenSlot: (LocalDate, MealSlot) -> Unit) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, if (day.isToday) c.accent else c.line, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("week-day-${day.date}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.label,
                Modifier.semantics { heading() },
                color = if (day.isToday) c.accent else c.ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (day.isToday) MutedText(" · Hoy", fontSizeSp = 14)
        }
        for (slot in day.slots) SlotRow(slot, onOpenSlot)
    }
}

@Composable
private fun SlotRow(view: WeekSlotView, onOpenSlot: (LocalDate, MealSlot) -> Unit) {
    val c = LocalRecetarioColors.current
    val meal = view.meal
    val open = { onOpenSlot(view.date, view.slot) }
    Row(
        Modifier
            .testTag(weekSlotTag(view.date, view.slot))
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // TalkBack lee el hueco entero («Lunes 16, comida: Arroz con tomate, 4 porciones») y lo abre. La etiqueta
            // va antes: `clearAndSetSemantics` descarta la semántica de lo que va detrás (también la de `clickable`).
            .clearAndSetSemantics {
                contentDescription = slotDescription(view.date, view.slot, view.title.takeIf { meal != null }, meal?.servings ?: 0)
                role = Role.Button
                onClick(label = null) { open(); true }
            }
            .clickable(onClick = open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(view.slot.label, Modifier.width(88.dp), color = c.muted, style = MaterialTheme.typography.bodyMedium)
        if (meal == null || view.title == null) {
            Text("+", color = c.accent, style = MaterialTheme.typography.titleLarge)
        } else {
            Text(
                "${view.emoji} ${view.title}",
                Modifier.weight(1f),
                color = c.ink,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            MutedText(servingsLabel(meal.servings), fontSizeSp = 13)
        }
    }
}

@Composable
private fun SlotPanel(
    view: WeekSlotView,
    recipes: List<WeekRecipeChoice>,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (WeekRecipeChoice) -> Unit,
    onServings: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    val meal = view.meal
    SectionTitle("${weekDayLabel(view.date)} · ${view.slot.label}")
    if (meal != null && view.title != null) {
        Text(view.title, color = c.ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("week-panel-title"))
        StepperRow(
            label = "Porciones",
            value = meal.servings.toString(),
            tag = "week-servings",
            minusDescription = "Quitar una porción",
            plusDescription = "Añadir una porción",
            onMinus = { onServings(-1) },
            onPlus = { onServings(1) },
            minusEnabled = meal.servings > RoomMealPlanRepository.MIN_SERVINGS,
            plusEnabled = meal.servings < RoomMealPlanRepository.MAX_SERVINGS,
        )
        TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp).testTag("week-remove")) {
            Text("Quitar", color = c.accent)
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("week-search"),
        label = { Text(if (meal == null) "Buscar receta" else "Cambiar por otra receta") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = appTextFieldColors(),
    )
    val needle = normalize(query.trim())
    val matches = remember(recipes, needle) { recipes.filter { needle.isEmpty() || normalize(it.title).contains(needle) } }
    Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
        if (matches.isEmpty()) MutedText("Ninguna receta coincide", Modifier.padding(vertical = 12.dp), fontSizeSp = 14)
        for (choice in matches) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = { onPick(choice) })
                    .testTag("week-choice-${choice.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(choice.emoji, Modifier.clearAndSetSemantics { }.padding(end = 10.dp))
                Text(choice.title, color = c.ink, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
