package app.recetario.recipes.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.FieldLabel
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.search.RecipeFilter
import app.recetario.recipes.search.SortOrder
import app.recetario.recipes.search.Taste

private val maxMinuteOptions = listOf(15, 30, 45, 60, 90, 120)

/** Hoja con todos los filtros de [RecipeFilter]; los cambios se aplican al momento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    filter: RecipeFilter,
    categories: List<Category>,
    tags: List<String>,
    resultCount: Int,
    onChange: ((RecipeFilter) -> RecipeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),
        ) {
            Text("Filtros", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(8.dp))

            Group("Categoría") {
                for (cat in categories) {
                    ChoiceChip(cat.name, cat.id in filter.categoryIds, leading = cat.emoji, onClick = {
                        onChange { f -> f.copy(categoryIds = f.categoryIds.toggle(cat.id)) }
                    })
                }
            }
            if (tags.isNotEmpty()) {
                Group("Etiquetas") {
                    for (tag in tags) {
                        ChoiceChip(tag, tag in filter.tags, onClick = { onChange { f -> f.copy(tags = f.tags.toggle(tag)) } })
                    }
                }
            }
            Options("Sabor", listOf(Taste.SWEET to "Dulce", Taste.SALTY to "Salada"), filter.taste) { v ->
                onChange { it.copy(taste = v) }
            }
            Options("Alcohol", listOf(true to "Con alcohol", false to "Sin alcohol"), filter.alcoholic) { v ->
                onChange { it.copy(alcoholic = v) }
            }
            Options("Se sirve", ServeTemp.entries.map { it to it.label }, filter.serveTemp) { v ->
                onChange { it.copy(serveTemp = v) }
            }
            Options("Tiempo máximo", maxMinuteOptions.map { it to formatMinutes(it) }, filter.maxMinutes) { v ->
                onChange { it.copy(maxMinutes = v) }
            }
            Options("Momento del día", Moment.entries.map { it to it.label }, filter.moment) { v ->
                onChange { it.copy(moment = v) }
            }
            Options("Dificultad", Difficulty.entries.map { it to it.label }, filter.difficulty) { v ->
                onChange { it.copy(difficulty = v) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = filter.favoritesOnly,
                        role = Role.Switch,
                        onValueChange = { on -> onChange { it.copy(favoritesOnly = on) } },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Solo favoritas", Modifier.weight(1f), color = c.ink, style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = filter.favoritesOnly,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                )
            }
            Group("Orden") {
                for (sort in SortOrder.entries) {
                    ChoiceChip(sortLabel(sort), filter.sort == sort, role = Role.RadioButton, onClick = {
                        onChange { it.copy(sort = sort) }
                    })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { onChange { it.withoutSheetFilters().copy(categoryIds = emptySet()) } }) {
                    Text("Quitar filtros", color = c.accent)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                ) { Text("Ver ${resultsLabel(resultCount)}") }
            }
        }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        FieldLabel(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** Opciones excluyentes con «Cualquiera» delante (null = sin filtro). */
@Composable
private fun <T> Options(title: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T?) -> Unit) {
    Group(title) {
        ChoiceChip("Cualquiera", selected == null, role = Role.RadioButton, onClick = { onSelect(null) })
        for ((value, label) in options) {
            ChoiceChip(label, selected == value, role = Role.RadioButton, onClick = { onSelect(value) })
        }
    }
}
