package app.recetario.cooking

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.model.Recipe
import kotlinx.coroutines.delay

/** Cada cuánto se repinta la cuenta atrás de la pantalla mientras corre algún temporizador. */
private const val COUNTDOWN_REFRESH_MS = 250L

/**
 * Modo cocina en lista (opción B del boceto). Mantiene la pantalla encendida. Los temporizadores siguen
 * con la app en segundo plano gracias a [TimerService], que se arranca al tocar uno.
 */
@Composable
fun CookingRoute(container: AppContainer, onClose: () -> Unit) {
    val vm: CookingViewModel = viewModel(factory = CookingViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    KeepScreenOn()

    // La primera vez que se inicia un temporizador se pide permiso para notificar (Android 13 o superior).
    // Sin él los temporizadores siguen y el servicio hace sonar la alarma igualmente.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val ensureService = {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications && !TimerNotifications.canPost(context)) {
            askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TimerService.start(context)
    }

    val running = state.timers.values.any { it.isRunning }
    val now = produceState(vm.now(), running) {
        value = vm.now()
        while (running) {
            delay(COUNTDOWN_REFRESH_MS)
            value = vm.now()
        }
    }

    CookingContent(
        state = state,
        photoStore = container.photoStore,
        now = { now.value },
        onClose = onClose,
        onToggleIngredient = vm::toggleIngredient,
        onGoTo = vm::goTo,
        onDone = vm::markDone,
        onStartTimer = { index ->
            vm.startTimer(index)
            ensureService()
        },
        onToggleTimer = { stepId ->
            vm.toggleTimer(stepId)
            ensureService()
        },
        onAddMinute = { stepId ->
            TimerNotifications.cancelFinished(context, stepId)
            vm.addMinute(stepId)
            ensureService()
        },
        onDismissTimer = { stepId ->
            vm.dismissTimer(stepId)
            TimerNotifications.cancelFinished(context, stepId)
        },
    )
}

/**
 * Modo cocina sin ViewModel (lo usan la ruta y las capturas): cabecera, ingredientes plegables, la lista de
 * pasos con el actual expandido y, al terminar, la tarjeta «¡Listo!». [now] da la hora de los temporizadores.
 */
@Composable
fun CookingContent(
    state: CookingUiState,
    photoStore: PhotoStore,
    now: () -> Long,
    onClose: () -> Unit,
    onToggleIngredient: (String) -> Unit,
    onGoTo: (Int) -> Unit,
    onDone: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onToggleTimer: (String) -> Unit,
    onAddMinute: (String) -> Unit,
    onDismissTimer: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val recipe = state.recipe
    var ingredientsOpen by rememberSaveable { mutableStateOf(true) }
    val bars = WindowInsets.systemBars.asPaddingValues()

    // Al cambiar de paso, la lista se desplaza sola hasta él (dejando asomar el anterior); sin animación si el
    // sistema las tiene quitadas. Al girar o volver no se mueve: la lista recuerda dónde estaba.
    var scrolledTo by rememberSaveable { mutableStateOf<Int?>(null) }
    val peekPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    LaunchedEffect(state.loading, recipe != null, state.currentIndex) {
        if (state.loading || recipe == null) return@LaunchedEffect
        val previous = scrolledTo
        scrolledTo = state.currentIndex
        if (previous == null || previous == state.currentIndex) return@LaunchedEffect
        val target = stepItemIndex(recipe, ingredientsOpen, state.currentIndex)
        if (reduced) listState.scrollToItem(target, -peekPx) else listState.animateScrollToItem(target, -peekPx)
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("cooking-list"),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = bars.calculateTopPadding() + 8.dp,
                bottom = bars.calculateBottomPadding() + 40.dp,
            ),
        ) {
            item(key = "top") {
                CookingTopBar(title = recipe?.title.orEmpty(), progress = progressText(state), onClose = onClose)
            }
            if (recipe == null) {
                item(key = "empty") {
                    if (!state.loading) MutedText("Esta receta ya no existe.", Modifier.padding(top = 24.dp), fontSizeSp = 15)
                }
            } else {
                if (recipe.ingredients.isNotEmpty()) {
                    item(key = "ingredients-title") {
                        IngredientsHeader(
                            open = ingredientsOpen,
                            checked = state.checkedIngredients.size,
                            total = recipe.ingredients.size,
                            onToggle = { ingredientsOpen = !ingredientsOpen },
                        )
                    }
                    if (ingredientsOpen) {
                        itemsIndexed(recipe.ingredients, key = { _, ing -> "ing-${ing.id}" }) { index, ing ->
                            CookingIngredientRow(
                                ingredient = ing,
                                sectionTitle = ing.section?.takeIf { it != recipe.ingredients.getOrNull(index - 1)?.section },
                                quantity = state.quantities[ing.id].orEmpty(),
                                checked = ing.id in state.checkedIngredients,
                                showDivider = index < recipe.ingredients.lastIndex,
                                onToggle = { onToggleIngredient(ing.id) },
                            )
                        }
                    }
                }
                item(key = "steps-title") { StepsTitle(recipe.steps.size) }
                if (recipe.steps.isEmpty()) {
                    item(key = "no-steps") { MutedText("Esta receta aún no tiene pasos.", fontSizeSp = 15) }
                }
                itemsIndexed(recipe.steps, key = { _, step -> "step-${step.id}" }) { index, step ->
                    val status = when {
                        index == state.currentIndex -> StepStatus.CURRENT
                        index in state.doneSteps -> StepStatus.DONE
                        else -> StepStatus.PENDING
                    }
                    CookingStep(
                        number = index + 1,
                        step = step,
                        status = status,
                        uses = if (status == StepStatus.CURRENT) stepUses(recipe, step.ingredientIds, state.quantities) else emptyList(),
                        timer = state.timers[step.id],
                        emoji = state.categoryEmoji,
                        photoStore = photoStore,
                        now = now,
                        onGoTo = { onGoTo(index) },
                        onDone = onDone,
                        onStartTimer = { onStartTimer(index) },
                        onToggleTimer = { onToggleTimer(step.id) },
                        onAddMinute = { onAddMinute(step.id) },
                        onDismissTimer = { onDismissTimer(step.id) },
                    )
                }
                if (state.finished) {
                    item(key = "finish") { FinishCard(onBack = onClose) }
                }
            }
        }
    }
}

/** «Paso 2 de 4», o «¡Listo!» al terminar. */
private fun progressText(state: CookingUiState): String = when {
    state.recipe == null -> ""
    state.stepCount == 0 -> "Sin pasos"
    state.finished -> "¡Listo!"
    else -> "Paso ${state.currentIndex + 1} de ${state.stepCount}"
}

/** Posición en la lista del paso [stepIndex] (con [stepIndex] = número de pasos, la tarjeta «¡Listo!»). */
private fun stepItemIndex(recipe: Recipe, ingredientsOpen: Boolean, stepIndex: Int): Int {
    var index = 1 // cabecera
    if (recipe.ingredients.isNotEmpty()) index += 1 + if (ingredientsOpen) recipe.ingredients.size else 0
    index += 1 // «Pasos»
    return index + stepIndex
}

/** Chips de los ingredientes que usa un paso, con la cantidad escalada: «Calabaza 1,2 kg». */
private fun stepUses(recipe: Recipe, ids: Set<String>, quantities: Map<String, String>): List<String> =
    recipe.ingredients.filter { it.id in ids }.map { ing ->
        listOf(ing.name, quantities[ing.id].orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
    }
