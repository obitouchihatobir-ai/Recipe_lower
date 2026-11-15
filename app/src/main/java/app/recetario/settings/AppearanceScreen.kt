package app.recetario.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.appSwitchColors
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalThemeRevealOrigin
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
import app.recetario.core.ui.theme.paletteFor
import app.recetario.core.ui.theme.resolveTheme
import app.recetario.core.ui.theme.typographyFor
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal fun ThemeChoice.label() = when (this) {
    ThemeChoice.CALIDO -> "Cálido"
    ThemeChoice.FRESCO -> "Fresco"
    ThemeChoice.NOCTURNO -> "Nocturno"
}

private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")
private const val NIGHT_STEP_MINUTES = 30L

/**
 * Tocar una muestra: con Automático y un tema claro, ese pasa a ser el de día; si no (Nocturno, o Automático
 * apagado), Automático se apaga y se usa ese tema. Igual que en el boceto.
 */
internal suspend fun SettingsRepository.pickTheme(current: ThemeSettings, choice: ThemeChoice) {
    if (current.auto && choice != ThemeChoice.NOCTURNO) {
        setDayTheme(choice)
    } else {
        if (choice != ThemeChoice.NOCTURNO) setDayTheme(choice)
        setTheme(choice)
        setAuto(false)
    }
}

@Composable
fun AppearanceRoute(container: AppContainer, onBack: () -> Unit) {
    val repo = container.settingsRepository
    val settings by repo.themeSettings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings
    val now = rememberNow(container.clock)
    val systemDark = isSystemInDarkTheme()
    val reveal = LocalThemeRevealOrigin.current
    val scope = rememberCoroutineScope()
    SettingsPage("Apariencia", onBack, tag = "appearance-screen") {
        if (current != null) {
            AppearanceContent(
                settings = current,
                effective = resolveTheme(current, now, systemDark),
                onTheme = { choice, origin ->
                    reveal(origin)
                    scope.launch { repo.pickTheme(current, choice) }
                },
                onAuto = { enabled, origin ->
                    reveal(origin)
                    scope.launch {
                        // Al apagarlo se queda lo que se ve ahora, sin cambio de tema.
                        if (!enabled) repo.setTheme(resolveTheme(current, now, systemDark))
                        repo.setAuto(enabled)
                    }
                },
                onNightWindow = { start, end -> scope.launch { repo.setNightWindow(start, end) } },
            )
        }
    }
}

/** Apariencia (boceto `apariencia.html`): tres muestras, el interruptor Automático y las horas de noche. */
@Composable
fun AppearanceContent(
    settings: ThemeSettings,
    effective: ThemeChoice,
    onTheme: (ThemeChoice, Offset) -> Unit,
    onAuto: (Boolean, Offset) -> Unit,
    onNightWindow: (LocalTime, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 32.dp),
    ) {
        MutedText("Elige cómo se ve tu recetario.", fontSizeSp = 13)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in ThemeChoice.entries) {
                val selected = if (settings.auto) choice == settings.dayTheme else choice == settings.choice
                ThemeSwatch(choice, selected, onClick = { onTheme(choice, it) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        AutoRow(settings.auto, onToggle = onAuto)
        if (settings.auto) {
            MutedText(
                "De día: ${settings.dayTheme.label()} · De noche: Nocturno · Ahora: ${effective.label()}",
                Modifier.padding(start = 2.dp, top = 10.dp).testTag("auto-note"),
                fontSizeSp = 12,
            )
            Spacer(Modifier.height(10.dp))
            NightHours(settings, onNightWindow)
        }
    }
}

@Composable
private fun ThemeSwatch(choice: ThemeChoice, selected: Boolean, onClick: (Offset) -> Unit, modifier: Modifier = Modifier) {
    val current = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val palette = paletteFor(choice)
    val ring by animateColorAsState(
        if (selected) current.accent else Color.Transparent,
        if (reduced) snap() else tween(250),
        label = "swatchRing",
    )
    var center by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onClick(center) })
            .semantics { contentDescription = "Tema ${choice.label()}" }
            .testTag("theme-${choice.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Anillo de selección: borde de 2 dp y un halo de 3 dp con el acento al 25 %.
        Box(Modifier.border(3.dp, ring.copy(alpha = ring.alpha * 0.25f), RoundedCornerShape(19.dp)).padding(3.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(shape)
                    .background(palette.bg)
                    .border(1.dp, palette.line, shape)
                    .border(2.dp, ring, shape),
            ) {
                Text(
                    "Aa",
                    Modifier.align(Alignment.Center),
                    color = palette.ink,
                    style = typographyFor(choice).headlineMedium.copy(fontSize = 22.sp),
                )
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(14.dp).background(palette.accent, CircleShape))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            choice.label(),
            color = current.ink,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            modifier = Modifier.heightIn(min = 20.dp),
        )
    }
}

@Composable
private fun AutoRow(auto: Boolean, onToggle: (Boolean, Offset) -> Unit) {
    val c = LocalRecetarioColors.current
    var center by remember { mutableStateOf(Offset.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clip(RoundedCornerShape(16.dp))
            .background(c.bg)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .toggleable(value = auto, role = Role.Switch, onValueChange = { onToggle(it, center) })
            .padding(12.dp)
            .testTag("auto-switch"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Automático", color = c.ink, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp))
            MutedText("Tu tema claro de día y Nocturno de noche (o cuando el teléfono esté en modo oscuro).", fontSizeSp = 12)
        }
        Switch(checked = auto, onCheckedChange = null, colors = appSwitchColors())
    }
}

/** Horas de noche: cada una con «−» y «+» de media hora. */
@Composable
private fun NightHours(settings: ThemeSettings, onChange: (LocalTime, LocalTime) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HourStepper(
            label = "Empieza la noche",
            time = settings.nightStart,
            other = settings.nightEnd,
            tag = "night-start",
            onTime = { onChange(it, settings.nightEnd) },
        )
        HourStepper(
            label = "Termina la noche",
            time = settings.nightEnd,
            other = settings.nightStart,
            tag = "night-end",
            onTime = { onChange(settings.nightStart, it) },
        )
    }
}

@Composable
private fun HourStepper(label: String, time: LocalTime, other: LocalTime, tag: String, onTime: (LocalTime) -> Unit) {
    val c = LocalRecetarioColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = c.ink, style = MaterialTheme.typography.bodyLarge)
        Surface(shape = CircleShape, color = c.surface, border = BorderStroke(1.dp, c.line)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Si empiezo y termino a la misma hora, la noche queda vacía y Automático nunca la alcanza.
                TextButton(
                    onClick = { onTime(time.minusMinutes(NIGHT_STEP_MINUTES)) },
                    enabled = time.minusMinutes(NIGHT_STEP_MINUTES) != other,
                    modifier = Modifier.semantics { contentDescription = "$label: media hora antes" }.testTag("$tag-minus"),
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Text(
                    time.format(hourFormat),
                    Modifier.widthIn(min = 56.dp),
                    textAlign = TextAlign.Center,
                    color = c.ink,
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(
                    onClick = { onTime(time.plusMinutes(NIGHT_STEP_MINUTES)) },
                    enabled = time.plusMinutes(NIGHT_STEP_MINUTES) != other,
                    modifier = Modifier.semantics { contentDescription = "$label: media hora después" }.testTag("$tag-plus"),
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
