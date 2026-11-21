package app.recetario.pairing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes

// ------------------------------------------------------------------ piezas comunes del maridaje

/** «Bebida sin alcohol», «Plato», «Postre»… */
fun roleLabel(item: PairingItem): String = when (item.role) {
    PairRole.DRINK -> when (item.alcoholic) {
        true -> "Bebida con alcohol"
        false -> "Bebida sin alcohol"
        null -> "Bebida"
    }
    PairRole.FOOD -> when (item.course) {
        app.recetario.recipes.model.Course.STARTER -> "Entrada"
        app.recetario.recipes.model.Course.MAIN -> "Plato principal"
        else -> "Plato"
    }
    PairRole.DESSERT -> "Postre"
    PairRole.SIDE -> "Acompañamiento"
}

/** «Bebida sin alcohol · De tu recetario» o «Postre · Idea». */
fun itemSubtitle(item: PairingItem): String =
    roleLabel(item) + if (item.kind == "RECIPE") " · De tu recetario" else " · Idea"

/** Un dibujo para cada papel (decorativo: el texto de al lado dice lo mismo). */
fun pairingEmoji(item: PairingItem): String = when (item.role) {
    PairRole.DRINK -> if (item.alcoholic == true) "🍷" else "🥤"
    PairRole.FOOD -> "🍽"
    PairRole.DESSERT -> "🍮"
    PairRole.SIDE -> "🥗"
}

const val LIKE_LABEL = "Me gusta esta pareja"
const val DISLIKE_LABEL = "No me gusta esta pareja"

/**
 * 👍 y 👎 (48 dp cada uno). Tocar la que ya está elegida la quita ([onRate] con null). Solo se muestran si la pareja
 * se puede valorar (al menos un lado es una receta propia).
 */
@Composable
fun RateButtons(liked: Boolean?, onRate: (Boolean?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RateButton("👍", LIKE_LABEL, liked == true) { onRate(if (liked == true) null else true) }
        RateButton("👎", DISLIKE_LABEL, liked == false) { onRate(if (liked == false) null else false) }
    }
}

@Composable
private fun RateButton(symbol: String, label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalRecetarioColors.current
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (on) c.soft else Color.Transparent)
            .border(if (on) BorderStroke(2.dp, c.accent) else BorderStroke(1.dp, c.line), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Icono redondo con el dibujo del papel. */
@Composable
fun PairingIcon(item: PairingItem, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Box(modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.soft), contentAlignment = Alignment.Center) {
        Text(pairingEmoji(item), fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * Una pareja sugerida: dibujo, nombre, de dónde sale, el porqué y 👍/👎 (si se puede valorar). Tocarla abre la
 * rueda comparativa ([onCompare] null: no se puede tocar).
 */
@Composable
fun SuggestionRow(
    suggestion: Suggestion,
    onCompare: (() -> Unit)?,
    onRate: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val item = suggestion.item
    Row(
        modifier.fillMaxWidth().testTag("pairing-row-${item.sideKey()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .let { if (onCompare != null) it.clickable(role = Role.Button, onClickLabel = "Comparar sabores", onClick = onCompare) else it }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PairingIcon(item)
            Column(Modifier.weight(1f)) {
                Text(item.name, color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W600)
                Text(itemSubtitle(item), color = c.muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    suggestion.reasons.joinToString(" "),
                    color = c.ink,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (suggestion.ratable) RateButtons(suggestion.liked, onRate, Modifier.padding(start = 6.dp))
    }
}

// ------------------------------------------------------------------ pestaña

@Composable
fun PairingRoute(
    container: AppContainer,
    onCompare: (subject: PairingItem, other: PairingItem) -> Unit,
    onFullMenu: () -> Unit,
    onHistory: () -> Unit,
) {
    val vm: PairingViewModel = viewModel(factory = PairingViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    PairingContent(
        state = state,
        onQuery = vm::setQuery,
        onChoose = vm::choose,
        onClear = vm::clearSubject,
        onRate = vm::rate,
        onCompare = { other -> state.subject?.let { onCompare(it, other) } },
        onFullMenu = onFullMenu,
        onHistory = onHistory,
    )
}

/** Pestaña Maridaje (§7.5): «¿Con qué combina…?», sus resultados, «Menú completo» e «Historial». */
@Composable
fun PairingContent(
    state: PairingUiState,
    onQuery: (String) -> Unit,
    onChoose: (PairingItem) -> Unit,
    onClear: () -> Unit,
    onRate: (PairingItem, Boolean?) -> Unit,
    onCompare: (PairingItem) -> Unit,
    onFullMenu: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val radius = LocalRecetarioShapes.current.radius
    val focus = LocalFocusManager.current
    // Estado local: lo escrito se pinta al momento; al elegir un resultado, el ViewModel vacía la búsqueda.
    var text by rememberSaveable { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { if (state.query.isEmpty()) text = "" }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().testTag("pairing-screen")) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Maridaje", Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize().testTag("pairing-list"), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
            item(key = "search") {
                Column(Modifier.padding(top = 8.dp)) {
                    SectionTitle("¿Con qué combina…?")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onQuery(it)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pairing-search"),
                        placeholder = { Text("Una receta o una bebida", maxLines = 1) },
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = c.muted) },
                        trailingIcon = if (text.isEmpty()) null else ({
                            IconButton(onClick = {
                                text = ""
                                onQuery("")
                            }) { Icon(AppIcons.Close, contentDescription = "Borrar la búsqueda", tint = c.muted) }
                        }),
                        singleLine = true,
                        shape = CircleShape,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        colors = appTextFieldColors(),
                    )
                }
            }
            item(key = "actions") {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onFullMenu,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pairing-full-menu"),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    ) { Text("Menú completo", fontWeight = FontWeight.W600) }
                    OutlinedButton(
                        onClick = onHistory,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pairing-history"),
                        border = BorderStroke(1.dp, c.line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink),
                    ) { Text("Historial", fontWeight = FontWeight.W600) }
                }
            }
            when {
                text.isNotBlank() -> {
                    if (state.loaded && state.query == text && state.matches.isEmpty()) {
                        item(key = "no-matches") {
                            Text(
                                "No hay ninguna receta ni bebida con «${text.trim()}».",
                                color = c.muted,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    items(state.matches, key = { "match-${it.sideKey()}" }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button, onClickLabel = "Ver con qué combina") {
                                    focus.clearFocus()
                                    onChoose(item)
                                }
                                .padding(vertical = 6.dp)
                                .testTag("pairing-match-${item.sideKey()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PairingIcon(item)
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W600)
                                Text(itemSubtitle(item), color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                state.subject != null -> {
                    val subject = requireNotNull(state.subject)
                    item(key = "subject") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(radius))
                                .background(c.surface)
                                .border(1.dp, c.line, RoundedCornerShape(radius))
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                                .testTag("pairing-subject"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PairingIcon(subject)
                            Column(Modifier.weight(1f)) {
                                Text("Con qué combina", color = c.muted, style = MaterialTheme.typography.labelMedium)
                                Text(subject.name, color = c.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W600)
                            }
                            IconButton(onClick = onClear) { Icon(AppIcons.Close, contentDescription = "Quitar la elección", tint = c.ink) }
                        }
                    }
                    item(key = "own-title") { SectionTitle("Tus recetas", Modifier.padding(top = 18.dp, bottom = 4.dp)) }
                    if (state.own.isEmpty()) {
                        item(key = "own-empty") {
                            Text(
                                "Ninguna de tus recetas combina con esto todavía.",
                                color = c.muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    items(state.own, key = { "own-${it.item.sideKey()}" }) { s ->
                        SuggestionRow(s, onCompare = { onCompare(s.item) }, onRate = { onRate(s.item, it) })
                    }
                    item(key = "ideas-title") { SectionTitle("Ideas", Modifier.padding(top = 18.dp, bottom = 4.dp)) }
                    if (state.ideas.isEmpty()) {
                        item(key = "ideas-empty") {
                            Text("No hay ideas para esto.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(state.ideas, key = { "idea-${it.item.sideKey()}" }) { s ->
                        SuggestionRow(s, onCompare = { onCompare(s.item) }, onRate = { onRate(s.item, it) })
                    }
                }
                state.loaded -> item(key = "intro") {
                    Text(
                        "Busca una receta o una bebida para ver con qué combina. Si partes de una bebida, verás qué platos y postres le van bien.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
            }
        }
    }
}
