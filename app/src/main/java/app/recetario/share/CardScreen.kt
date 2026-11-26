package app.recetario.share

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalThemeChoice
import app.recetario.settings.SettingsPage
import java.io.File

/** Lo que dura el giro de la vista previa al cambiar de formato o de diseño (diseño de la etapa 2, §8). */
const val CARD_FLIP_MS = 300

/** Ángulo del giro de la vista previa (0 en reposo). Solo lo leen las pruebas. */
val CardFlipAngle = SemanticsPropertyKey<Float>("CardFlipAngle")
private var SemanticsPropertyReceiver.cardFlipAngle by CardFlipAngle

const val PHOTO_DROPPED_TEXT = "La foto no cabe con tanto texto"
const val PHOTO_UNREADABLE_TEXT = "No se pudo leer la foto"
const val NO_PHOTO_TEXT = "La receta no tiene foto"

@Composable
fun CardRoute(
    container: AppContainer,
    recipeId: String,
    servings: Int,
    onBack: () -> Unit,
    /** Dibuja la ficha y crea el PDF (las pruebas pasan uno con un PDF falso). */
    painter: CardPainter? = null,
    /** El `content://` del archivo que se comparte (las pruebas pasan uno falso). */
    uriFor: ((File) -> Uri)? = null,
    /** Abre el selector de compartir (las pruebas pasan uno que falla). */
    startShare: ((Intent) -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val cardPainter = remember(app, painter) { painter ?: CardPainter(CardFonts(app)) }
    val vm: CardViewModel = viewModel(factory = CardViewModel.factory(container, cardPainter, app.cacheDir, recipeId, servings))
    val state by vm.state.collectAsStateWithLifecycle()
    val theme = LocalThemeChoice.current
    LaunchedEffect(theme) { vm.setTheme(theme) }
    val snackbar = remember { SnackbarHostState() }

    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.save(uri.toString())
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.save(uri.toString())
    }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is CardEvent.ShareFile, is CardEvent.ShareText -> {
                    // Cualquier fallo al publicar el archivo o al abrir el selector se avisa (y se anota).
                    val opened = runCatching {
                        val intent = when (e) {
                            is CardEvent.ShareFile -> cardShareIntent(context, e.file, e.mimeType, uriFor ?: { cardFileUri(context, it) })
                            else -> textShareIntent((e as CardEvent.ShareText).text)
                        }
                        (startShare ?: { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })(intent)
                    }.onFailure { Log.e("Ficha", "No se pudo abrir el selector para compartir", it) }.isSuccess
                    if (!opened) snackbar.showSnackbar(CARD_SHARE_FAILED)
                }
                is CardEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    CardContent(
        state = state,
        painter = vm.painter,
        snackbar = snackbar,
        onBack = onBack,
        onText = vm::chooseText,
        onFormat = vm::chooseFormat,
        onDesign = vm::chooseDesign,
        onPhoto = vm::setPhoto,
        onSteps = vm::setSteps,
        onNotes = vm::setNotes,
        onFlavor = vm::setFlavor,
        onShare = vm::share,
        onSave = {
            val name = vm.fileName() ?: return@CardContent
            if (name.endsWith(".pdf")) savePdf.launch(name) else savePng.launch(name)
        },
    )
}

/** La pantalla Ficha sin ViewModel (la usan la ruta y las capturas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardContent(
    state: CardUiState,
    painter: CardPainter,
    snackbar: SnackbarHostState?,
    onBack: () -> Unit,
    onText: () -> Unit,
    onFormat: (CardFormat) -> Unit,
    onDesign: (CardDesign) -> Unit,
    onPhoto: (Boolean) -> Unit,
    onSteps: (Boolean) -> Unit,
    onNotes: (Boolean) -> Unit,
    onFlavor: (Boolean) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val s = state.settings
    val o = s.options
    // Hasta que llegan las opciones guardadas, los chips no se tocan (las de serie no las pisan).
    val ready = !state.loading
    SettingsPage(title = "Ficha", onBack = onBack, tag = "card-screen", modifier = modifier, snackbar = snackbar) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.missing) {
                Text("Esta receta ya no existe.", color = c.muted)
                return@Column
            }
            val preview = state.preview
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                if (preview != null) FlippingPreview(preview, painter)
            }
            val hint = when {
                state.photoUnreadable -> PHOTO_UNREADABLE_TEXT
                (preview as? CardPreview.Image)?.layout?.photoDropped == true -> PHOTO_DROPPED_TEXT
                else -> null
            }
            if (hint != null) {
                Text(hint, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("card-photo-hint"))
            }

            Group("Formato")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Texto", s.asText, onText, Modifier.testTag("card-format-TEXT"), role = Role.RadioButton, enabled = ready)
                CardFormat.entries.forEach { f ->
                    ChoiceChip(f.label, !s.asText && o.format == f, { onFormat(f) }, Modifier.testTag("card-format-${f.name}"), role = Role.RadioButton, enabled = ready)
                }
            }
            if (!s.asText) {
                Group("Diseño")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardDesign.entries.forEach { d ->
                        ChoiceChip(d.label, o.design == d, { onDesign(d) }, Modifier.testTag("card-design-${d.name}"), role = Role.RadioButton, enabled = ready)
                    }
                }
                Group("Incluir")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip(
                        "Foto",
                        selected = o.photo && state.recipeHasPhoto,
                        onClick = { onPhoto(!o.photo) },
                        modifier = Modifier.testTag("card-photo"),
                        role = Role.Switch,
                        enabled = ready && state.recipeHasPhoto,
                        stateDescription = if (state.recipeHasPhoto) null else NO_PHOTO_TEXT,
                    )
                    ChoiceChip("Pasos", o.steps, { onSteps(!o.steps) }, Modifier.testTag("card-steps"), role = Role.Switch, enabled = ready)
                    ChoiceChip("Notas", o.notes, { onNotes(!o.notes) }, Modifier.testTag("card-notes"), role = Role.Switch, enabled = ready)
                    ChoiceChip("Sabor", o.flavor, { onFlavor(!o.flavor) }, Modifier.testTag("card-flavor"), role = Role.Switch, enabled = ready)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canAct = preview != null && !state.busy
                Button(
                    onClick = onShare,
                    enabled = canAct,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("card-share"),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = c.onAccent, strokeWidth = 2.dp)
                        Text("  Preparando…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } else {
                        Text("Compartir")
                    }
                }
                if (!s.asText) {
                    OutlinedButton(
                        onClick = onSave,
                        enabled = canAct,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("card-save"),
                        border = BorderStroke(1.dp, c.accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
                    ) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    val c = LocalRecetarioColors.current
    Text(
        title,
        color = c.ink,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
    )
}

/**
 * La vista previa gira sobre su eje vertical al cambiar de formato o de diseño: la vieja hasta 90°, se cambia y la
 * nueva desde −90° (en [CARD_FLIP_MS] en total). Con [LocalReducedMotion], al instante. No captura toques: los chips
 * se pueden tocar durante el giro, y un cambio a mitad sigue desde el ángulo en el que está.
 */
@Composable
fun FlippingPreview(preview: CardPreview, painter: CardPainter, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableStateOf(preview) }
    val angle = remember { Animatable(0f) }
    LaunchedEffect(preview, reduced) {
        if (reduced) {
            shown = preview
            angle.snapTo(0f)
            return@LaunchedEffect
        }
        if (preview.flipKey != shown.flipKey) {
            angle.animateTo(90f, tween(CARD_FLIP_MS / 2, easing = FastOutLinearInEasing))
            shown = preview
            angle.snapTo(-90f)
        } else {
            shown = preview
        }
        if (angle.value != 0f) angle.animateTo(0f, tween(CARD_FLIP_MS / 2, easing = LinearOutSlowInEasing))
    }
    Box(
        modifier
            .graphicsLayer {
                rotationY = angle.value
                cameraDistance = 12f * density
            }
            .testTag("card-preview")
            .clearAndSetSemantics {
                contentDescription = shown.description
                cardFlipAngle = angle.value
            },
    ) {
        PreviewBody(shown, painter)
    }
}

@Composable
private fun PreviewBody(preview: CardPreview, painter: CardPainter) {
    val c = LocalRecetarioColors.current
    when (preview) {
        is CardPreview.Text -> Box(
            Modifier.fillMaxWidth().heightIn(max = 360.dp).background(c.surface, RoundedCornerShape(12.dp))
                .border(1.dp, c.line, RoundedCornerShape(12.dp)).clipToBounds().padding(14.dp),
        ) {
            Text(preview.text, color = c.ink, fontSize = 13.sp, lineHeight = 18.sp)
        }
        is CardPreview.Image -> CardCanvas(preview.layout.format, preview.layout.blocks, preview.look, preview.photo, painter)
        is CardPreview.Pdf -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val first = preview.pages.first()
            CardCanvas(first.format, first.blocks, preview.look, preview.photo, painter)
            val n = preview.pages.size
            Text(if (n == 1) "1 página" else "Página 1 de $n", color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** La ficha dibujada con [CardPainter] (lo mismo que la imagen y el PDF), escalada para caber. */
@Composable
private fun CardCanvas(format: CardFormat, blocks: List<CardBlock>, look: CardLook, photo: android.graphics.Bitmap?, painter: CardPainter) {
    val c = LocalRecetarioColors.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ratio = format.width / format.height
        val maxH = 380.dp
        val w = min(maxWidth, maxH * ratio)
        val h = w / ratio
        Canvas(Modifier.size(w, h).border(1.dp, c.line)) {
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(size.width / format.width, size.height / format.height)
                painter.draw(nc, format, blocks, look, photo)
                nc.restore()
            }
        }
    }
}
