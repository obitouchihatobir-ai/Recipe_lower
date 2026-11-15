package app.recetario.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.text.normalize
import app.recetario.core.ui.components.AppTextField
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.FieldLabel
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.UnitKind
import app.recetario.recipes.quantity.formatQuantity
import app.recetario.recipes.quantity.parseQuantity
import app.recetario.settings.SettingsPage

/** Unidades que se pueden elegir: solo las de masa o volumen. */
val converterUnits: List<MeasureUnit> = MeasureUnit.entries.filter { it.kind == UnitKind.MASS || it.kind == UnitKind.VOLUME }

const val DENSITY_MESSAGE = "Para pasar de volumen a peso necesito saber el ingrediente (por ejemplo, harina)."
const val INCOMPATIBLE_MESSAGE = "Esas unidades no se pueden convertir entre sí."
const val VALUE_ERROR = "Escribe un número, por ejemplo 1,5 o ½."

/** Ingredientes de la tabla de densidades que encajan con lo escrito (todos si está vacío; ninguno si ya coincide). */
fun ingredientSuggestions(text: String): List<String> {
    val q = normalize(text).trim()
    if (q.isEmpty()) return Densities.keywords
    return Densities.keywords.filter { q in it && it != q }
}

@Composable
fun ConverterRoute(container: AppContainer, onBack: () -> Unit) {
    val cupMl by container.settingsRepository.cupMl.collectAsStateWithLifecycle(initialValue = 240)
    var valueText by rememberSaveable { mutableStateOf("1") }
    var fromName by rememberSaveable { mutableStateOf(MeasureUnit.CUP.name) }
    var toName by rememberSaveable { mutableStateOf(MeasureUnit.GRAM.name) }
    var ingredient by rememberSaveable { mutableStateOf("") }
    ConverterContent(
        valueText = valueText,
        from = MeasureUnit.valueOf(fromName),
        to = MeasureUnit.valueOf(toName),
        ingredient = ingredient,
        cupMl = cupMl,
        onValueText = { valueText = it },
        onFrom = { fromName = it.name },
        onTo = { toName = it.name },
        onIngredient = { ingredient = it },
        onSwap = { fromName = toName.also { toName = fromName } },
        onBack = onBack,
    )
}

/** Conversor de medidas sin estado propio (lo usan la ruta y las capturas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterContent(
    valueText: String,
    from: MeasureUnit,
    to: MeasureUnit,
    ingredient: String,
    cupMl: Int,
    onValueText: (String) -> Unit,
    onFrom: (MeasureUnit) -> Unit,
    onTo: (MeasureUnit) -> Unit,
    onIngredient: (String) -> Unit,
    onSwap: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val value = parseQuantity(valueText)
    val valueInvalid = valueText.isNotBlank() && value == null
    SettingsPage(title = "Conversor", onBack = onBack, tag = "converter-screen", modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppTextField(
                value = valueText,
                onValueChange = onValueText,
                label = "Cantidad",
                modifier = Modifier.fillMaxWidth().testTag("converter-value"),
                error = if (valueInvalid) VALUE_ERROR else null,
            )
            UnitPicker("De", from, "from", onFrom)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Intercambiar unidades" }.testTag("converter-swap"),
                ) { Text("⇅", color = c.accent, fontSize = 22.sp) }
            }
            UnitPicker("A", to, "to", onTo)
            AppTextField(
                value = ingredient,
                onValueChange = onIngredient,
                label = "Ingrediente (opcional)",
                placeholder = "harina, azúcar, leche…",
                modifier = Modifier.fillMaxWidth().testTag("converter-ingredient"),
            )
            val suggestions = ingredientSuggestions(ingredient)
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        ChoiceChip(s, selected = false, onClick = { onIngredient(s) }, modifier = Modifier.testTag("suggestion-$s"), role = Role.Button)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (value == null) {
                    if (!valueInvalid) MutedText("Escribe una cantidad para convertir.")
                } else when (val r = UnitConverter(cupMl).convert(value, from, to, ingredient.ifBlank { null })) {
                    is Conversion.Ok -> {
                        MutedText("${formatQuantity(value, from, null)} equivale a")
                        Text(
                            formatQuantity(r.value, r.unit, null),
                            color = c.accent,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("converter-result"),
                        )
                        if (from.kind != to.kind) MutedText("Aproximado: depende de la densidad del ingrediente.")
                    }
                    Conversion.NeedsDensity -> Text(DENSITY_MESSAGE, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    Conversion.Incompatible -> Text(INCOMPATIBLE_MESSAGE, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitPicker(label: String, selected: MeasureUnit, tagPrefix: String, onSelect: (MeasureUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            converterUnits.forEach { u ->
                ChoiceChip(
                    u.singular,
                    selected = u == selected,
                    onClick = { onSelect(u) },
                    modifier = Modifier.testTag("$tagPrefix-${u.name}"),
                    role = Role.RadioButton,
                )
            }
        }
    }
}
