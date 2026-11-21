package app.recetario.recipes.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.TagPill
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.pairing.PairingItem
import app.recetario.pairing.Suggestion
import app.recetario.pairing.SuggestionRow

/**
 * «Maridaje» en el detalle (§7.5): las 3 mejores parejas con su porqué y 👍/👎 (solo si se pueden valorar). Tocar una
 * abre la rueda comparativa; «Ver más», la pestaña Maridaje con esta receta elegida.
 */
@Composable
fun DetailPairingSection(
    suggestions: List<Suggestion>,
    onRate: (PairingItem, Boolean?) -> Unit,
    onCompare: ((PairingItem) -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(LocalRecetarioShapes.current.radius)
    Column(modifier.testTag("detail-pairing")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Maridaje", Modifier.weight(1f))
            if (onMore != null) {
                TextButton(onClick = onMore, modifier = Modifier.heightIn(min = 48.dp).testTag("detail-pairing-more")) {
                    Text("Ver más", color = c.accent, fontWeight = FontWeight.W600)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.line, shape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            suggestions.forEachIndexed { index, s ->
                SuggestionRow(
                    suggestion = s,
                    onCompare = onCompare?.let { compare -> { compare(s.item) } },
                    onRate = { onRate(s.item, it) },
                )
                if (index < suggestions.lastIndex) HorizontalDivider(color = c.line)
            }
        }
    }
}

/** «Le iría bien…»: hasta 6 sabores que la receta todavía no lleva (no sale si no hay ninguno). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoesWellWithSection(flavors: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.testTag("detail-goes-well")) {
        SectionTitle("Le iría bien…")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            flavors.forEach { TagPill(it, accent = true) }
        }
    }
}
