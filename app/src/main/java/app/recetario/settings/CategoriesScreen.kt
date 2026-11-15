package app.recetario.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.AppTextField
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.FieldLabel
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface CategoryPanel {
    /** [category] null crea una nueva. */
    data class Edit(val category: Category?) : CategoryPanel
    data class Delete(val category: Category) : CategoryPanel
}

@Composable
fun CategoriesRoute(container: AppContainer, onBack: () -> Unit, repo: RecipeRepository = container.recipeRepository) {
    val categories by repo.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
    val counts by remember(repo) {
        repo.observeSummaries().map { list -> list.groupingBy { it.categoryId }.eachCount() }
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    CategoriesContent(
        snackbar = snackbar,
        categories = categories,
        recipeCounts = counts,
        onBack = onBack,
        onSave = { category -> scope.launch { snackbar.reportFailure("No se pudo guardar la categoría.") { repo.saveCategory(category) } } },
        onDelete = { id, reassignTo ->
            scope.launch { snackbar.reportFailure("No se pudo borrar la categoría.") { repo.deleteCategory(id, reassignTo) } }
        },
    )
}

@Composable
fun CategoriesContent(
    snackbar: SnackbarHostState,
    categories: List<Category>,
    recipeCounts: Map<String, Int>,
    onBack: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (id: String, reassignTo: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    var panel by remember { mutableStateOf<CategoryPanel?>(null) }
    SettingsPage(
        title = "Categorías",
        onBack = onBack,
        tag = "categories-screen",
        modifier = modifier,
        snackbar = snackbar,
        actions = {
            TextButton(onClick = { panel = CategoryPanel.Edit(null) }, modifier = Modifier.testTag("category-add")) {
                Text("Nueva", color = c.accent)
            }
        },
        overlay = {
            when (val p = panel) {
                is CategoryPanel.Edit -> EditCategoryPanel(
                    category = p.category,
                    nextSortOrder = (categories.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                    onDismiss = { panel = null },
                    onSave = { onSave(it); panel = null },
                )
                is CategoryPanel.Delete -> DeleteCategoryPanel(
                    category = p.category,
                    others = categories.filter { it.id != p.category.id },
                    recipeCount = recipeCounts[p.category.id] ?: 0,
                    onDismiss = { panel = null },
                    onConfirm = { target -> onDelete(p.category.id, target); panel = null },
                )
                null -> Unit
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxWidth().testTag("categories-list"), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
            items(categories, key = { it.id }) { category ->
                CategoryRow(
                    category,
                    recipeCounts[category.id] ?: 0,
                    onEdit = { panel = CategoryPanel.Edit(category) },
                    onDelete = { panel = CategoryPanel.Delete(category) },
                )
            }
        }
    }
}

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

@Composable
private fun CategoryRow(category: Category, recipeCount: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = LocalRecetarioColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClickLabel = "Editar ${category.name}", role = Role.Button, onClick = onEdit)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .testTag("category-row-${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(parseColor(category.colorHex).copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(category.emoji, fontSize = 22.sp) }
        Column(Modifier.weight(1f)) {
            SectionTitle(category.name)
            MutedText("${category.baseType.label} · ${if (recipeCount == 1) "1 receta" else "$recipeCount recetas"}")
        }
        if (!category.isDefault) {
            TextButton(onClick = onDelete, modifier = Modifier.testTag("category-delete-${category.id}")) {
                Text("Borrar", color = c.accent)
            }
        }
    }
}

@Composable
private fun BoxScope.EditCategoryPanel(
    category: Category?,
    nextSortOrder: Int,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
) {
    val c = LocalRecetarioColors.current
    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    var emoji by remember(category) { mutableStateOf(category?.emoji ?: DEFAULT_CATEGORY_EMOJI) }
    var colorHex by remember(category) { mutableStateOf(category?.colorHex ?: categoryColors.first().second) }
    var baseType by remember(category) { mutableStateOf(category?.baseType ?: BaseType.FOOD) }
    val canSave = name.isNotBlank()
    BottomPanel(onDismiss, "category-panel") {
        SectionTitle(if (category == null) "Nueva categoría" else "Editar categoría")
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre",
            modifier = Modifier.fillMaxWidth().testTag("category-name"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        OutlinedTextField(
            value = emoji,
            onValueChange = { emoji = lastGrapheme(it) },
            label = { Text("Emoji") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("category-emoji"),
            colors = appTextFieldColors(),
        )
        FieldLabel("Color")
        // Ocho colores en dos filas de cuatro, para que se vean todos sin desplazar.
        for (row in categoryColors.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((colorName, hex) in row) {
                    val selected = hex == colorHex
                    Box(
                        Modifier
                            .size(48.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { colorHex = hex })
                            .semantics { contentDescription = "Color $colorName" }
                            .testTag("category-color-${hex.removePrefix("#")}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(parseColor(hex))
                                .border(if (selected) 3.dp else 1.dp, if (selected) c.ink else c.line, CircleShape),
                        )
                    }
                }
            }
        }
        // Las categorías de serie conservan su tipo base: de él depende cómo se sugieren y se combinan.
        if (category?.isDefault != true) {
            FieldLabel("Tipo base")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BaseType.entries, key = { it.name }) { type ->
                    ChoiceChip(
                        type.label,
                        selected = type == baseType,
                        onClick = { baseType = type },
                        role = Role.RadioButton,
                        modifier = Modifier.testTag("category-base-${type.name}"),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = c.muted) }
            TextButton(
                onClick = {
                    onSave(
                        Category(
                            id = category?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            emoji = emoji.ifBlank { DEFAULT_CATEGORY_EMOJI },
                            colorHex = colorHex,
                            baseType = baseType,
                            sortOrder = category?.sortOrder ?: nextSortOrder,
                            isDefault = category?.isDefault ?: false,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.heightIn(min = 48.dp).testTag("category-save"),
            ) { Text("Guardar", color = if (canSave) c.accent else c.muted) }
        }
    }
}

@Composable
private fun BoxScope.DeleteCategoryPanel(
    category: Category,
    others: List<Category>,
    recipeCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (reassignTo: String) -> Unit,
) {
    val c = LocalRecetarioColors.current
    var target by remember(category) { mutableStateOf(defaultReassignTarget(category, others)?.id) }
    BottomPanel(onDismiss, "category-delete-panel") {
        SectionTitle("Borrar «${category.name}»")
        if (recipeCount == 0) {
            MutedText("No tiene recetas.")
        } else {
            MutedText(if (recipeCount == 1) "Tiene 1 receta." else "Tiene $recipeCount recetas.")
            Text("¿A qué categoría pasan sus recetas?", color = c.ink, style = MaterialTheme.typography.titleMedium)
            Column {
                for (option in others) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option.id == target, role = Role.RadioButton, onClick = { target = option.id })
                            .testTag("reassign-${option.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = option.id == target,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.muted),
                        )
                        Text("${option.emoji}  ${option.name}", color = c.ink, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = c.muted) }
            TextButton(
                onClick = { target?.let(onConfirm) },
                enabled = target != null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("category-delete-confirm"),
            ) { Text("Borrar", color = c.accent) }
        }
    }
}
