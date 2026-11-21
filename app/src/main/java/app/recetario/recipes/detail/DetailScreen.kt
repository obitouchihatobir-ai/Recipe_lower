package app.recetario.recipes.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.pairing.PairingItem
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.TagPill
import app.recetario.core.ui.components.ServingsStepper
import app.recetario.core.ui.components.sharedRecipePhoto
import app.recetario.core.ui.components.staggeredEntrance
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.diary.LogDraft
import app.recetario.diary.LogEntryPanel
import app.recetario.diary.LogFormActions
import app.recetario.diary.rememberLogFormActions
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.list.formatDurationSeconds
import app.recetario.recipes.list.formatMinutes
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.scaleQuantity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOAST_MILLIS = 2_500L

/** «✓ 7 ingredientes añadidos a tu lista de compra» (o el aviso de que no había nada que añadir). */
fun addedMessage(count: Int): String = when (count) {
    0 -> "No había nada que añadir: ya tienes todo marcado"
    1 -> "✓ 1 ingrediente añadido a tu lista de compra"
    else -> "✓ $count ingredientes añadidos a tu lista de compra"
}

@Composable
fun DetailRoute(
    container: AppContainer,
    recipeId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCooking: (servings: Int) -> Unit,
    /** «Ver más» del maridaje (null: sin el botón). */
    onMorePairings: (() -> Unit)? = null,
    /** Tocar una pareja del maridaje: la rueda comparativa. */
    onComparePairing: ((PairingItem) -> Unit)? = null,
) {
    val vm: DetailViewModel = viewModel(factory = DetailViewModel.factory(container, recipeId))
    val live by vm.state.collectAsStateWithLifecycle()
    // Mientras se borra (o al salir) la receta desaparece de la base de datos: se conserva el último cuadro
    // para que la pantalla no se vacíe durante la transición de salida.
    var last by remember { mutableStateOf<DetailUiState?>(null) }
    if (live.recipe != null) last = live
    val state = last ?: live

    val cupMl by container.settingsRepository.cupMl.collectAsStateWithLifecycle(initialValue = 240)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var toast by rememberSaveable { mutableStateOf<String?>(null) }
    // Se reinicia con cada aviso terminado (aunque el texto sea el mismo): la clave es el contador, no el texto.
    var toastCount by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(toastCount) {
        if (toast != null) {
            delay(TOAST_MILLIS)
            toast = null
        }
    }
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(vm) {
        vm.events.collect {
            when (it) {
                DetailEvent.Deleted -> currentOnBack()
                DetailEvent.DeleteFailed -> snackbar.showSnackbar("No se pudo borrar la receta.")
            }
        }
    }

    val editor = vm.logEditor
    val logDraft = editor?.draft?.collectAsStateWithLifecycle()?.value
    val logActions = editor?.let { rememberLogFormActions(it) }

    DetailContent(
        state = state,
        recipeId = recipeId,
        photoStore = vm.photoStore,
        toast = toast,
        onBack = onBack,
        onFavorite = vm::toggleFavorite,
        onEdit = onEdit,
        onShare = { shareRecipe(context, vm.shareText()) },
        onDelete = vm::delete,
        onServings = vm::setServings,
        onToggleIngredient = vm::toggleIngredient,
        cupMl = cupMl,
        onNeed = {
            if (!adding) {
                adding = true
                scope.launch {
                    try {
                        toast = addedMessage(vm.sendMissingToShopping())
                        toastCount++
                    } finally {
                        adding = false
                    }
                }
            }
        },
        snackbar = snackbar,
        onCooking = { onCooking(state.servings) },
        logDraft = logDraft,
        logActions = logActions,
        onLog = vm::startLog,
        onRatePairing = vm::ratePairing,
        onComparePairing = onComparePairing,
        onMorePairings = onMorePairings,
    )
}

private fun shareRecipe(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Compartir receta").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sin apps para compartir: no hay nada que hacer.
    }
}

/**
 * Detalle sin ViewModel (lo usan la ruta y las capturas). La cabecera con la foto está siempre compuesta,
 * con la clave de la transición compartida, aunque la receta aún no haya llegado: así la foto de la tarjeta
 * viaja a un hueco del mismo tamaño y no aparece ningún marcador de posición de más.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailContent(
    state: DetailUiState,
    recipeId: String,
    photoStore: PhotoStore,
    toast: String?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onServings: (Int) -> Unit,
    onToggleIngredient: (String) -> Unit,
    cupMl: Int,
    onNeed: () -> Unit,
    onCooking: () -> Unit,
    logDraft: LogDraft? = null,
    logActions: LogFormActions? = null,
    /** «Lo hice» (null: sin diario). */
    onLog: (() -> Unit)? = null,
    /** 👍/👎 de una pareja del maridaje. */
    onRatePairing: (PairingItem, Boolean?) -> Unit = { _, _ -> },
    /** Tocar una pareja: la rueda comparativa (null: no se puede tocar). */
    onComparePairing: ((PairingItem) -> Unit)? = null,
    /** «Ver más»: la pestaña Maridaje con esta receta (null: sin el botón). */
    onMorePairings: (() -> Unit)? = null,
) {
    val c = LocalRecetarioColors.current
    val radius = LocalRecetarioShapes.current.radius
    val recipe = state.recipe
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Ingrediente cuya hoja de equivalencias está abierta.
    var conversionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }

    // Alto real del botón fijo «▶ Modo cocina» (crece con el tamaño de letra); el final de la lista queda por encima.
    val density = LocalDensity.current
    var cookButtonHeight by remember { mutableStateOf(52.dp) }
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottom = if (recipe != null) cookButtonHeight + COOK_BUTTON_MARGIN * 2 + navigationBottom else navigationBottom

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("detail-list"),
            contentPadding = PaddingValues(bottom = listBottom),
        ) {
            item(key = "hero") {
                Box {
                Box(Modifier.sharedRecipePhoto(recipeId).fillMaxWidth().height(220.dp)) {
                    if (recipe != null) {
                        PhotoOrPlaceholder(
                            fileName = recipe.coverPhoto,
                            photoStore = photoStore,
                            emoji = state.categoryEmoji,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = recipe.title,
                            emojiSize = 84.sp,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(c.heroBrush))
                    }
                }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundButton("Volver", onBack) { Icon(AppIcons.Back, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.ink) }
                if (recipe != null) {
                    Row {
                        RoundButton(
                            if (recipe.favorite) "Quitar de favoritas" else "Marcar como favorita",
                            onFavorite,
                        ) { FavoriteHeart(recipe.favorite) }
                        Box {
                            RoundButton("Más opciones", { menuOpen = true }) { Text("⋯", color = c.ink, fontSize = 17.sp) }
                            DropdownMenu(menuOpen, { menuOpen = false }, containerColor = c.surface) {
                                DropdownMenuItem(
                                    text = { Text("Editar", color = c.ink) },
                                    onClick = { menuOpen = false; onEdit() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Compartir", color = c.ink) },
                                    onClick = { menuOpen = false; onShare() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Borrar", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                }
            }
                }
            }
            if (recipe == null) {
                item(key = "empty") {
                    Column(Modifier.overlapAbove(22.dp).fillMaxWidth().background(c.bg, TopRounded).padding(18.dp)) {
                        if (!state.loading) MutedText("Esta receta ya no existe.", fontSizeSp = 15)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                item(key = "header") {
                    Column(
                        Modifier
                            .staggeredEntrance(0)
                            .overlapAbove(22.dp)
                            .fillMaxWidth()
                            .background(c.bg, TopRounded)
                            .padding(start = 18.dp, end = 18.dp, top = 16.dp),
                    ) {
                        ScreenTitle(recipe.title)
                        if (recipe.description.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(recipe.description, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            recipe.totalMinutes?.let { TagPill("⏱ ${formatMinutes(it)}") }
                            TagPill(recipe.difficulty.label)
                            if (state.categoryName.isNotBlank()) TagPill(state.categoryName)
                            recipe.tags.forEach { TagPill(it, accent = true) }
                            recipe.serveTemp?.let { TagPill(serveTempText(it), accent = true) }
                        }
                        if (onLog != null) {
                            CookStatsRow(state.stats, state.today, onHistory = { historyOpen = true }, onLog = onLog)
                        }
                    }
                }
                // Sin ningún sabor apuntado, el pentágono vacío no diría nada: el bloque no sale (se rellena en el editor).
                if (recipe.flavor.hasTastes()) {
                    item(key = "flavor") {
                        Column(Modifier.staggeredEntrance(1).padding(horizontal = 18.dp).padding(top = 18.dp)) {
                            SectionTitle("Sabor")
                            Spacer(Modifier.height(10.dp))
                            FlavorCard(recipe.flavor)
                        }
                    }
                }
                item(key = "ingredients-title") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SectionTitle("Ingredientes", Modifier.weight(1f, fill = false))
                        ServingsStepper(
                            state.servings, onServings,
                            range = DetailViewModel.MIN_SERVINGS..DetailViewModel.MAX_SERVINGS,
                        )
                    }
                }
                itemsIndexed(recipe.ingredients, key = { _, ing -> "ing-${ing.id}" }) { index, ing ->
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        val previous = recipe.ingredients.getOrNull(index - 1)?.section
                        if (ing.section != null && ing.section != previous) {
                            Text(
                                ing.section.orEmpty(),
                                color = c.muted,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        val scaled = scaleQuantity(ing.quantity, recipe.servings, state.servings)
                        IngredientRow(
                            ingredient = ing,
                            quantity = state.quantities[ing.id].orEmpty(),
                            convertible = ing.isConvertible(scaled),
                            checked = ing.id in state.checkedIngredientIds,
                            showDivider = index < recipe.ingredients.lastIndex,
                            onToggle = { onToggleIngredient(ing.id) },
                            onQuantityTap = { conversionsFor = ing.id },
                        )
                    }
                }
                if (recipe.ingredients.isNotEmpty()) {
                    item(key = "need") {
                        NeedButton(onNeed, Modifier.padding(horizontal = 18.dp).padding(top = 12.dp))
                    }
                }
                item(key = "steps-title") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SectionTitle("Pasos")
                        MutedText(if (recipe.steps.size == 1) "1 paso" else "${recipe.steps.size} pasos", fontSizeSp = 12)
                    }
                }
                itemsIndexed(recipe.steps, key = { _, s -> "step-${s.id}" }) { index, step ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val thumb = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                        if (step.photos.isNotEmpty()) {
                            PhotoOrPlaceholder(
                                fileName = step.photos.first(),
                                photoStore = photoStore,
                                emoji = state.categoryEmoji,
                                modifier = thumb,
                                emojiSize = 24.sp,
                            )
                        } else {
                            Box(thumb.background(c.soft), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = c.accent, fontSize = 20.sp, fontWeight = FontWeight.W600)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            val timer = step.timerSeconds?.let { " · ⏱ ${formatDurationSeconds(it)}" }.orEmpty()
                            Text(
                                "PASO ${index + 1}$timer",
                                color = c.muted,
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.6.sp,
                            )
                            Text(step.text, color = c.ink, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp))
                        }
                    }
                }
                if (state.pairings.isNotEmpty()) {
                    item(key = "pairing") {
                        DetailPairingSection(
                            suggestions = state.pairings,
                            onRate = onRatePairing,
                            onCompare = onComparePairing,
                            onMore = onMorePairings,
                            modifier = Modifier.padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 10.dp),
                        )
                    }
                }
                if (state.goesWellWith.isNotEmpty()) {
                    item(key = "goes-well") {
                        GoesWellWithSection(state.goesWellWith, Modifier.padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 10.dp))
                    }
                }
                if (recipe.notes.isNotBlank()) {
                    item(key = "notes") {
                        Column(Modifier.padding(horizontal = 18.dp).padding(top = 8.dp)) {
                            SectionTitle("Mis notas")
                            Spacer(Modifier.height(10.dp))
                            Text(
                                recipe.notes,
                                color = c.ink,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(radius))
                                    .background(c.surface)
                                    .border(1.dp, c.line, RoundedCornerShape(radius))
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 18.dp).padding(bottom = COOK_BUTTON_MARGIN),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SnackbarHost(snackbar)
            Toast(toast)
            if (recipe != null) {
                Button(
                    onClick = onCooking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .onSizeChanged { cookButtonHeight = with(density) { it.height.toDp() } }
                        .testTag("detail-cook"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                ) { Text("▶ Modo cocina", fontSize = 15.sp, fontWeight = FontWeight.W700) }
            }
        }

        val open = recipe?.ingredients?.firstOrNull { it.id == conversionsFor }
        val openQuantity = if (recipe != null && open != null) scaleQuantity(open.quantity, recipe.servings, state.servings) else null
        if (open != null && openQuantity != null) {
            ConversionsPanel(open, openQuantity, state.quantities[open.id].orEmpty(), cupMl, onDismiss = { conversionsFor = null })
        }
        if (historyOpen && recipe != null && logDraft == null) {
            HistoryPanel(
                stats = state.stats,
                history = state.history,
                today = state.today,
                photoStore = photoStore,
                emoji = state.categoryEmoji,
                onLog = onLog?.let { log -> { historyOpen = false; log() } },
                onDismiss = { historyOpen = false },
            )
        }
        if (logDraft != null && logActions != null) {
            LogEntryPanel("Lo hice", logDraft, state.today, photoStore, logActions)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text("¿Borrar esta receta?", color = c.ink) },
            text = { Text("Se borrará con sus fotos y no se puede deshacer.", color = c.muted) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, modifier = Modifier.testTag("confirm-delete")) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = c.accent) } },
        )
    }
}

private val TopRounded = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

private fun serveTempText(t: ServeTemp) = when (t) {
    ServeTemp.HOT -> "Se sirve caliente"
    ServeTemp.COLD -> "Se sirve fría"
    ServeTemp.ROOM -> "Se sirve a temperatura ambiente"
}

/** Margen bajo «▶ Modo cocina» y hueco entre el final de la lista y el botón. */
private val COOK_BUTTON_MARGIN = 18.dp
