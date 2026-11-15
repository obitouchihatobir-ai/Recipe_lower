package app.recetario.recipes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.appSwitchColors
import app.recetario.core.ui.components.FlavorWheel
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.search.Taste
import app.recetario.recipes.search.classifyTaste

// Sección «Sabor» del editor: cinco chips de 0 a 3, mini rueda en vivo, atajos «Dulce» y «Salada».

private val flavorAxes: List<Pair<String, (FlavorProfile) -> Int>> = listOf(
    "Dulce" to { it.sweet },
    "Salado" to { it.salty },
    "Ácido" to { it.sour },
    "Amargo" to { it.bitter },
    "Picante" to { it.spicy },
)

/** Un toque sube el nivel de 0 a 3 (y vuelve a 0). */
internal fun FlavorProfile.bumped(axis: Int): FlavorProfile {
    fun next(v: Int) = (v + 1) % 4
    return when (axis) {
        0 -> copy(sweet = next(sweet))
        1 -> copy(salty = next(salty))
        2 -> copy(sour = next(sour))
        3 -> copy(bitter = next(bitter))
        else -> copy(spicy = next(spicy))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlavorSection(state: EditorState, onEvent: (EditorEvent) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val flavor = state.flavor
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                flavorAxes.forEachIndexed { i, (label, level) ->
                    val v = level(flavor)
                    ChoiceChip(
                        label,
                        selected = v > 0,
                        role = Role.Button,
                        stateDescription = "$v de 3",
                        onClick = { onEvent(EditorEvent.SetFlavor(flavor.bumped(i))) },
                        trailing = { tint -> LevelDots(v, tint) },
                    )
                }
            }
            FlavorWheel(flavor, size = 108.dp)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MutedText("Atajos", Modifier.padding(end = 8.dp))
            val taste = classifyTaste(flavor.sweet, flavor.salty)
            ChoiceChip("Dulce", taste == Taste.SWEET, role = Role.Button, onClick = { onEvent(EditorEvent.MarkSweet) })
            Spacer(Modifier.width(6.dp))
            ChoiceChip("Salada", taste == Taste.SALTY, role = Role.Button, onClick = { onEvent(EditorEvent.MarkSalty) })
        }
        ChipGroup("Intensidad") {
            for (i in Intensity.entries) {
                ChoiceChip(i.label, flavor.intensity == i, role = Role.RadioButton, onClick = {
                    onEvent(EditorEvent.SetFlavor(flavor.copy(intensity = i)))
                })
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Switch) { onEvent(EditorEvent.SetFlavor(flavor.copy(rich = !flavor.rich))) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Rica o cremosa", color = c.ink, style = MaterialTheme.typography.bodyLarge)
                MutedText("Con grasa o nata: el maridaje lo tiene en cuenta.", fontSizeSp = 12)
            }
            Switch(
                checked = flavor.rich,
                onCheckedChange = null,
                colors = appSwitchColors(),
            )
        }
    }
}

@Composable
private fun LevelDots(level: Int, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            val filled = i < level
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .then(if (filled) Modifier.background(tint) else Modifier.border(1.dp, tint, CircleShape)),
            )
        }
    }
}
