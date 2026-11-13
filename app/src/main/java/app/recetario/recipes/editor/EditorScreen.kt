package app.recetario.recipes.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** De dónde sale una foto nueva. */
enum class PhotoSource { CAMERA, GALLERY }

/** Pide fotos a la cámara o la galería; [discardPending] tira las que aún no se han entregado al editor. */
class PhotoRequester(
    val request: (PhotoTarget, PhotoSource) -> Unit,
    val discardPending: () -> Unit,
)

@Composable
fun EditorRoute(
    container: AppContainer,
    isNew: Boolean,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: EditorViewModel = viewModel(factory = EditorViewModel.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val pendingDraft by vm.pendingDraft.collectAsStateWithLifecycle()
    val repo = container.recipeRepository
    val categories by remember(repo) { repo.observeCategories() }.collectAsStateWithLifecycle(emptyList())
    val tags by remember(repo) { repo.observeTags() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }
    var saveFailures by remember { mutableIntStateOf(0) }
    // Etiqueta escrita pero aún sin añadir: «Guardar» la añade.
    var pendingTag by rememberSaveable { mutableStateOf("") }
    // Las fotos solo se entregan con el editor listo y el borrador ya decidido.
    val photos = rememberPhotoRequester(
        editorReady = ready,
        draftPending = pendingDraft != null,
        onPicked = { stream, target -> vm.onEvent(EditorEvent.ImportPhoto(stream, target)) },
        onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
    )
    // Quitar un ingrediente o un paso ofrece «Deshacer».
    val onEvent: (EditorEvent) -> Unit = remember(vm) {
        { e ->
            val current = vm.state.value
            val undo = when (e) {
                is EditorEvent.RemoveIngredient -> current.removalOfIngredient(e.id)?.let { it to "Se eliminó el ingrediente" }
                is EditorEvent.RemoveStep -> current.removalOfStep(e.id)?.let { it to "Se eliminó el paso" }
                else -> null
            }
            vm.onEvent(e)
            if (undo != null) {
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(undo.second, actionLabel = "Deshacer", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) vm.onEvent(EditorEvent.UndoRemoval(undo.first))
                }
            }
        }
    }

    EditorContent(
        state = state,
        ready = ready,
        isNew = isNew,
        categories = categories,
        allTags = tags,
        photoStore = container.photoStore,
        saving = saving,
        saveFailures = saveFailures,
        snackbar = snackbar,
        pendingTag = pendingTag,
        onPendingTagChange = { pendingTag = it },
        onEvent = onEvent,
        onSave = {
            if (!saving) {
                // Guardar cierra el «Deshacer» pendiente: lo guardado no debe cambiar después.
                snackbar.currentSnackbarData?.dismiss()
                val tags = vm.state.value.tags
                val withPending = addTagsFromInput(tags, pendingTag)
                if (withPending !== tags) vm.onEvent(EditorEvent.SetTags(withPending))
                pendingTag = ""
                saving = true
                scope.launch {
                    val id = try {
                        vm.save()
                    } finally {
                        saving = false
                    }
                    if (id != null) onSaved(id) else saveFailures++
                }
            }
        },
        onBack = onBack,
        onPickPhoto = photos.request,
    )

    if (pendingDraft != null) {
        RecoverDraftDialog(
            onRecover = vm::recoverDraft,
            onDiscard = {
                // Las fotos hechas para ese borrador se tiran con él (antes de que se puedan entregar).
                photos.discardPending()
                scope.launch { vm.discardPendingDraft() }
            },
        )
    }
}

/** Mientras haya un borrador sin resolver no se edita nada: el cuadro no se cierra hasta elegir. */
@Composable
private fun RecoverDraftDialog(onRecover: () -> Unit, onDiscard: () -> Unit) {
    val c = LocalRecetarioColors.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = c.surface,
        title = { Text("¿Recuperar el borrador?", color = c.ink) },
        text = { Text("La última vez saliste sin guardar. ¿Sigues donde lo dejaste?", color = c.muted) },
        confirmButton = { TextButton(onClick = onRecover) { Text("Recuperar", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Descartar", color = c.accent) } },
    )
}

/** Editor sin estado propio (lo que sí guarda: secciones plegadas y desplazamiento); lo usan la ruta y las capturas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorContent(
    state: EditorState,
    ready: Boolean,
    isNew: Boolean,
    categories: List<Category>,
    allTags: List<String>,
    photoStore: PhotoStore,
    saving: Boolean,
    saveFailures: Int,
    snackbar: SnackbarHostState,
    pendingTag: String,
    onPendingTagChange: (String) -> Unit,
    onEvent: (EditorEvent) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onPickPhoto: (PhotoTarget, PhotoSource) -> Unit,
) {
    val c = LocalRecetarioColors.current
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val collapsedSections = collapsed.mapNotNull { name -> EditorSection.entries.firstOrNull { it.name == name } }.toSet()
    val rows = editorRows(state, collapsedSections)
    val listState = rememberLazyListState()
    val latestState by rememberUpdatedState(state)

    // Guardar con errores: se despliegan las secciones afectadas y se va al primer error.
    LaunchedEffect(saveFailures) {
        if (saveFailures == 0) return@LaunchedEffect
        val withErrors = sectionsWithErrors(latestState)
        collapsed = collapsed.filter { name -> withErrors.none { it.name == name } }
        val target = firstErrorKey(latestState) ?: return@LaunchedEffect
        val newRows = editorRows(latestState, collapsedSections - withErrors)
        val index = newRows.indexOfFirst { it.key == target }
        if (index < 0) return@LaunchedEffect
        // Espera a que la lista ya tenga las filas de las secciones recién desplegadas.
        withTimeoutOrNull(1_000) { snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= newRows.size } }
        listState.animateScrollToItem(index)
    }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nueva receta" else "Editar receta", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Volver") }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = ready && !saving) {
                        Text("Guardar", color = if (ready && !saving) c.accent else c.muted, style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.bg,
                    scrolledContainerColor = c.surface,
                    titleContentColor = c.ink,
                    navigationIconContentColor = c.ink,
                    actionIconContentColor = c.ink,
                ),
            )
        },
    ) { padding ->
        if (!ready) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
        } else {
            EditorList(
                rows = rows,
                state = state,
                listState = listState,
                padding = padding,
                categories = categories,
                allTags = allTags,
                photoStore = photoStore,
                pendingTag = pendingTag,
                onPendingTagChange = onPendingTagChange,
                onEvent = onEvent,
                onPickPhoto = onPickPhoto,
                onToggleSection = { section ->
                    collapsed = if (section.name in collapsed) collapsed - section.name else collapsed + section.name
                },
            )
        }
    }
}

@Composable
private fun EditorList(
    rows: List<EditorRow>,
    state: EditorState,
    listState: LazyListState,
    padding: PaddingValues,
    categories: List<Category>,
    allTags: List<String>,
    photoStore: PhotoStore,
    pendingTag: String,
    onPendingTagChange: (String) -> Unit,
    onEvent: (EditorEvent) -> Unit,
    onPickPhoto: (PhotoTarget, PhotoSource) -> Unit,
    onToggleSection: (EditorSection) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val latest by rememberUpdatedState(state)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        moveRow(latest, from.key, to.key)?.let(onEvent)
    }
    val category = categories.firstOrNull { it.id == state.categoryId }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(padding)
            .imePadding()
            .testTag("editor-list"),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
    ) {
        items(rows, key = { it.key }, contentType = { it.contentType }) { row ->
            val animate = if (reduced) Modifier else Modifier.animateItem()
            when (row) {
                EditorRow.Cover -> CoverPicker(
                    coverPhoto = state.coverPhoto,
                    emoji = category?.emoji ?: "📷",
                    photoStore = photoStore,
                    onPick = { source -> onPickPhoto(PhotoTarget.Cover, source) },
                    onRemove = { onEvent(EditorEvent.RemoveCover) },
                    modifier = animate,
                )
                is EditorRow.Header -> SectionHeader(
                    section = row.section,
                    count = row.count,
                    expanded = row.expanded,
                    onToggle = { onToggleSection(row.section) },
                    modifier = animate,
                )
                EditorRow.Basics -> BasicsSection(state, categories, allTags, pendingTag, onPendingTagChange, onEvent, animate)
                EditorRow.Flavor -> FlavorSection(state, onEvent, animate.testTag("flavor-section"))
                EditorRow.Notes -> NotesSection(state, onEvent, animate)
                is EditorRow.IngredientItem -> ReorderableItem(
                    reorderState,
                    key = row.key,
                    animateItemModifier = if (reduced) Modifier else Modifier.animateItem(),
                ) { dragging ->
                    IngredientRow(
                        draft = row.draft,
                        sectionTitle = row.sectionTitle,
                        nameError = state.rowErrors[ingredientNameErrorKey(row.draft.id)],
                        quantityError = state.rowErrors[ingredientQuantityErrorKey(row.draft.id)],
                        dragging = dragging,
                        handle = Modifier.draggableHandle(),
                        canMoveUp = row.index > 0,
                        canMoveDown = row.index < state.ingredients.lastIndex,
                        onMove = { delta -> onEvent(EditorEvent.MoveIngredient(row.index, row.index + delta)) },
                        onChange = { onEvent(EditorEvent.UpdateIngredient(it)) },
                        onRemove = { onEvent(EditorEvent.RemoveIngredient(row.draft.id)) },
                    )
                }
                EditorRow.AddIngredient -> AddRowButton("Añadir ingrediente", animate) { onEvent(EditorEvent.AddIngredient) }
                is EditorRow.StepItem -> ReorderableItem(
                    reorderState,
                    key = row.key,
                    animateItemModifier = if (reduced) Modifier else Modifier.animateItem(),
                ) { dragging ->
                    StepRow(
                        step = row.draft,
                        number = row.index + 1,
                        ingredients = state.ingredients,
                        error = state.rowErrors[stepTextErrorKey(row.draft.id)],
                        dragging = dragging,
                        handle = Modifier.draggableHandle(),
                        canMoveUp = row.index > 0,
                        canMoveDown = row.index < state.steps.lastIndex,
                        photoStore = photoStore,
                        onMove = { delta -> onEvent(EditorEvent.MoveStep(row.index, row.index + delta)) },
                        onEvent = onEvent,
                        onPickPhoto = { source -> onPickPhoto(PhotoTarget.StepPhoto(row.draft.id), source) },
                    )
                }
                EditorRow.AddStep -> AddRowButton("Añadir paso", animate) { onEvent(EditorEvent.AddStep) }
            }
        }
    }
}

/** Mover arrastrando: solo dentro de la misma lista (ingrediente con ingrediente, paso con paso). */
internal fun moveRow(state: EditorState, fromKey: Any, toKey: Any): EditorEvent? {
    val from = fromKey as? String ?: return null
    val to = toKey as? String ?: return null
    fun indexIn(ids: List<String>, key: String, prefix: String) = ids.indexOfFirst { "$prefix$it" == key }
    return when {
        from.startsWith(INGREDIENT_PREFIX) && to.startsWith(INGREDIENT_PREFIX) -> {
            val ids = state.ingredients.map { it.id }
            EditorEvent.MoveIngredient(indexIn(ids, from, INGREDIENT_PREFIX), indexIn(ids, to, INGREDIENT_PREFIX))
        }
        from.startsWith(STEP_PREFIX) && to.startsWith(STEP_PREFIX) -> {
            val ids = state.steps.map { it.id }
            EditorEvent.MoveStep(indexIn(ids, from, STEP_PREFIX), indexIn(ids, to, STEP_PREFIX))
        }
        else -> null
    }?.takeIf { e ->
        when (e) {
            is EditorEvent.MoveIngredient -> e.from >= 0 && e.to >= 0
            is EditorEvent.MoveStep -> e.from >= 0 && e.to >= 0
            else -> false
        }
    }
}
