package app.recetario.recipes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import app.recetario.converter.equivalencesFor
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.quantity.UnitKind
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.settings.BottomPanel

/** ¿Tiene sentido ofrecer equivalencias? Solo con cantidad y unidad de masa o volumen. */
fun Ingredient.isConvertible(scaledQuantity: Double?): Boolean =
    scaledQuantity != null && (unit.kind == UnitKind.MASS || unit.kind == UnitKind.VOLUME)

/** Hoja con las equivalencias de la cantidad (ya escalada) de un ingrediente, en gramos, ml, cucharadas… */
@Composable
fun BoxScope.ConversionsPanel(
    ingredient: Ingredient,
    quantity: Double,
    quantityText: String,
    cupMl: Int,
    onDismiss: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    val rows = equivalencesFor(quantity, ingredient.unit, ingredient.name, cupMl)
        .map { it to formatQuantity(it.value, it.unit, null) }
        // g y kg (o ml y l) se escriben igual cuando son pequeños: se muestra una sola vez, y nunca la cantidad original.
        .distinctBy { it.second }
        .filter { it.second != quantityText }
    BottomPanel(onDismiss, "conversions-panel") {
        SectionTitle("${ingredient.name} · $quantityText", Modifier.testTag("conversions-title"))
        if (rows.isEmpty()) MutedText("No hay equivalencias para esta cantidad.")
        rows.forEach { (eq, text) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text, color = c.ink, style = MaterialTheme.typography.bodyLarge)
                if (eq.approximate) MutedText("≈ según la densidad")
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End)) { Text("Cerrar", color = c.accent) }
    }
}
