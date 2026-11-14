package app.recetario.cooking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.components.AnimatedCheck
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.TagPill
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.detail.RoundButton
import app.recetario.recipes.list.formatDurationSeconds
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Step

internal enum class StepStatus { DONE, CURRENT, PENDING }

private val EaseOut = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)

/** ✕, el título de la receta con «Paso 2 de 4» (se anuncia al cambiar) y ☀ (la pantalla no se apaga). */
@Composable
internal fun CookingTopBar(title: String, progress: String, onClose: () -> Unit) {
    val c = LocalRecetarioColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton("Cerrar modo cocina", onClose) {
            Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = c.ink,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (progress.isNotEmpty()) {
                Text(
                    progress,
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Box(
            Modifier.size(48.dp).clearAndSetSemantics { contentDescription = "La pantalla se mantiene encendida" },
            contentAlignment = Alignment.Center,
        ) { Text("☀", color = c.muted, fontSize = 15.sp) }
    }
}

/** «Ingredientes» con cuántos están tachados; al tocarlo se pliega o despliega la lista. */
@Composable
internal fun IngredientsHeader(open: Boolean, checked: Int, total: Int, onToggle: () -> Unit) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(if (reduced) 0 else 250), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = if (open) "Plegar" else "Desplegar", onClick = onToggle)
            .semantics { stateDescription = if (open) "Desplegados" else "Plegados" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("Ingredientes", Modifier.weight(1f))
        MutedText("$checked de $total")
        Spacer(Modifier.width(6.dp))
        Icon(AppIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(20.dp).rotate(turn), tint = c.muted)
    }
}

/** Fila de ingrediente para tachar mientras se prepara todo (toda la fila es la casilla). */
@Composable
internal fun CookingIngredientRow(
    ingredient: Ingredient,
    sectionTitle: String?,
    quantity: String,
    checked: Boolean,
    showDivider: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    Column {
        if (sectionTitle != null) {
            Text(sectionTitle, color = c.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedCheck(checked)
            Spacer(Modifier.width(10.dp))
            Text(
                ingredient.name,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = if (checked) 0.5f else 1f },
                color = c.ink,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            if (quantity.isNotEmpty()) {
                Text(quantity, color = c.ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (showDivider) HorizontalDivider(color = c.line)
    }
}

@Composable
internal fun StepsTitle(count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionTitle("Pasos")
        MutedText(if (count == 1) "1 paso" else "$count pasos", fontSizeSp = 12)
    }
}

/**
 * Un paso de la lista: número (✓ si está hecho), texto y, si es el actual, expandido con letra grande, fotos,
 * ingredientes, temporizador y «Hecho ✓». Los demás se pueden tocar para volver a ellos; los hechos se atenúan
 * y, si tienen un temporizador, lo siguen mostrando.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CookingStep(
    number: Int,
    step: Step,
    status: StepStatus,
    uses: List<String>,
    timer: CookingTimer?,
    emoji: String,
    photoStore: PhotoStore,
    now: () -> Long,
    onGoTo: () -> Unit,
    onDone: () -> Unit,
    onStartTimer: () -> Unit,
    onToggleTimer: () -> Unit,
    onAddMinute: () -> Unit,
    onDismissTimer: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val radius = LocalRecetarioShapes.current.radius
    val current = status == StepStatus.CURRENT
    val dim by animateFloatAsState(if (status == StepStatus.DONE) 0.55f else 1f, tween(if (reduced) 0 else 300), label = "stepDim")
    val seconds = step.timerSeconds?.takeIf { it > 0 }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (current) {
                    Modifier
                } else {
                    Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (status == StepStatus.DONE) "Volver a este paso" else "Ir a este paso",
                            onClick = onGoTo,
                        )
                        .semantics { stateDescription = if (status == StepStatus.DONE) "Hecho" else "Pendiente" }
                },
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Los hechos se atenúan (número y texto; su temporizador, si lo tienen, sigue bien visible).
        Box(Modifier.width(30.dp).graphicsLayer { alpha = dim }) { StepNumber(number, status) }
        if (current) {
            val shape = RoundedCornerShape(radius)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(c.surface)
                    .border(1.dp, c.line, shape)
                    .padding(12.dp)
                    .testTag("current-step"),
            ) {
                Text(
                    step.text,
                    color = c.ink,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                )
                Column(Modifier.fadeUpOnAppear()) {
                    StepPhotos(step.photos, photoStore, emoji, number)
                    if (uses.isNotEmpty()) {
                        FlowRow(
                            Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) { uses.forEach { TagPill(it) } }
                    }
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (seconds != null) {
                            StepTimerButton(number, seconds, timer, now, onStartTimer, onToggleTimer, onDismissTimer)
                            if (timer != null) AddMinuteButton(number, onAddMinute)
                        }
                        DoneButton(number, onDone, Modifier.weight(1f).widthIn(min = 112.dp))
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    step.text,
                    color = c.muted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    modifier = Modifier.graphicsLayer { alpha = dim },
                )
                if (seconds != null && timer != null) {
                    StepTimerButton(number, seconds, timer, now, onStartTimer, onToggleTimer, onDismissTimer, Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** Círculo de 28 dp: número (gris), con borde de acento si es el actual, o ✓ relleno si está hecho. */
@Composable
private fun StepNumber(number: Int, status: StepStatus) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val fill by animateColorAsState(
        if (status == StepStatus.DONE) c.accent else c.accent.copy(alpha = 0f),
        tween(if (reduced) 0 else 300),
        label = "stepFill",
    )
    val border = if (status == StepStatus.PENDING) c.line else c.accent
    val ink = when (status) {
        StepStatus.DONE -> c.onAccent
        StepStatus.CURRENT -> c.accent
        StepStatus.PENDING -> c.muted
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, border, CircleShape)
            .clearAndSetSemantics { contentDescription = "Paso $number" },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (status == StepStatus.DONE) "✓" else "$number", color = ink, fontSize = 12.5.sp, fontWeight = FontWeight.W700)
    }
}

/** Fotos del paso (carrusel si hay varias) o, sin fotos, un recuadro suave con el emoji de la categoría. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepPhotos(photos: List<String>, photoStore: PhotoStore, emoji: String, stepNumber: Int) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(12.dp)
    val top = Modifier.padding(top = 10.dp)
    when (photos.size) {
        0 -> Box(
            top.fillMaxWidth().height(120.dp).clip(shape).background(c.soft).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 52.sp) }
        1 -> PhotoOrPlaceholder(
            fileName = photos.single(),
            photoStore = photoStore,
            emoji = emoji,
            modifier = top.fillMaxWidth().height(170.dp).clip(shape),
            contentDescription = "Foto del paso $stepNumber",
            emojiSize = 52.sp,
        )
        else -> {
            val rowState = rememberLazyListState()
            LazyRow(
                top.fillMaxWidth().height(170.dp),
                state = rowState,
                flingBehavior = rememberSnapFlingBehavior(rowState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(photos, key = { index, name -> "$index-$name" }) { index, name ->
                    PhotoOrPlaceholder(
                        fileName = name,
                        photoStore = photoStore,
                        emoji = emoji,
                        modifier = Modifier.fillParentMaxWidth(0.86f).fillMaxHeight().clip(shape),
                        contentDescription = "Foto ${index + 1} de ${photos.size} del paso $stepNumber",
                        emojiSize = 52.sp,
                    )
                }
            }
        }
    }
}

/**
 * Botón del temporizador: «⏱ Iniciar 5:00» → «⏸ 4:58» (relleno y latiendo; tocar pausa) → «▶ 4:58» (en pausa;
 * tocar reanuda) → «✓ ¡Tiempo!» (tocar apaga el aviso). ⏸ y ▶ se dibujan con iconos de trazo.
 */
@Composable
internal fun StepTimerButton(
    stepNumber: Int,
    seconds: Int,
    timer: CookingTimer?,
    now: () -> Long,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val running = timer?.isRunning == true
    val finished = timer?.finished == true
    val clock = formatTimerClock(timer?.remainingMs(now()) ?: (seconds * 1000L))
    val text: String
    // Pausa y reanudar van con iconos de trazo: los caracteres ⏸ y ▶ salen como emojis de color.
    val icon: ImageVector?
    val description: String
    val onClick: () -> Unit
    when {
        timer == null -> {
            text = "⏱ Iniciar $clock"
            icon = null
            description = "Iniciar el temporizador del paso $stepNumber, ${formatDurationSeconds(seconds)}"
            onClick = onStart
        }
        finished -> {
            text = "✓ ¡Tiempo!"
            icon = null
            description = "¡Tiempo! Apagar el aviso del paso $stepNumber"
            onClick = onDismiss
        }
        running -> {
            text = clock
            icon = AppIcons.Pause
            description = "Pausar el temporizador del paso $stepNumber. Quedan $clock"
            onClick = onToggle
        }
        else -> {
            text = clock
            icon = AppIcons.Play
            description = "Reanudar el temporizador del paso $stepNumber. Quedan $clock"
            onClick = onToggle
        }
    }
    val ink = if (running) c.onAccent else c.accent
    // Late mientras corre: un halo que crece y vuelve (como en el boceto); nada si se quitaron las animaciones.
    val halo = if (running && !reduced) {
        rememberInfiniteTransition(label = "timerPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "timerHalo",
        )
    } else {
        null
    }
    Box(
        modifier
            .drawBehind {
                val spread = 6.dp.toPx() * (halo?.value ?: 0f)
                if (spread > 0f) {
                    drawRoundRect(
                        c.accent.copy(alpha = 0.22f),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + 2 * spread, size.height + 2 * spread),
                        cornerRadius = CornerRadius(size.height / 2 + spread),
                    )
                }
            }
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (running) c.accent else Color.Transparent)
            .border(1.5.dp, c.accent, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (finished) liveRegion = LiveRegionMode.Assertive
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = ink)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = ink,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.W700, fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AddMinuteButton(stepNumber: Int, onClick: () -> Unit) {
    val c = LocalRecetarioColors.current
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Sumar un minuto al temporizador del paso $stepNumber" }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+1 min", color = c.ink, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.W700), maxLines = 1)
    }
}

@Composable
private fun DoneButton(stepNumber: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Marcar el paso $stepNumber como hecho" }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Hecho ✓", color = c.onAccent, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.W700), maxLines = 1)
    }
}

/** 🎉 «¡Listo!» con «Volver a la receta». */
@Composable
internal fun FinishCard(onBack: () -> Unit) {
    val c = LocalRecetarioColors.current
    Column(
        Modifier.fillMaxWidth().fadeUpOnAppear().padding(horizontal = 8.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", fontSize = 46.sp, modifier = Modifier.clearAndSetSemantics {})
        Text(
            "¡Listo!",
            color = c.ink,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 6.dp).semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        MutedText("Has terminado todos los pasos.", Modifier.padding(top = 4.dp))
        Box(
            Modifier
                .padding(top = 16.dp)
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(c.accent)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Volver a la receta", color = c.onAccent, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.W700))
        }
    }
}

/** Aparece subiendo 8 dp en 400 ms (lo nuevo del paso actual y la tarjeta final); al instante sin animaciones. */
@Composable
private fun Modifier.fadeUpOnAppear(): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(400, easing = EaseOut))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 8.dp.toPx()
    }
}
