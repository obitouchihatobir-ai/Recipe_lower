package app.recetario.recipes.list

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.AddRecipeFab
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.appTextFieldColors
import app.recetario.core.ui.components.rememberEntranceTracker
import app.recetario.core.ui.components.staggeredEntrance
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.search.RecipeFilter
import app.recetario.recipes.search.SortOrder

@Composable
fun RecipesRoute(
    container: AppContainer,
    onRecipe: (String) -> Unit,
    onAdd: (categoryId: String?) -> Unit,
) {
    val vm: RecipesViewModel = viewModel(factory = RecipesViewModel.factory(container.recipeRepository))
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    RecipesContent(
        state = state,
        filter = filter,
        photoStore = container.photoStore,
        onQuery = vm::setQuery,
        onToggleCategory = vm::toggleCategory,
        onAllCategories = vm::clearCategories,
        onSort = { sort -> vm.setFilter { it.copy(sort = sort) } },
        onToggleLayout = vm::toggleLayout,
        onOpenFilters = { showFilters = true },
        onClearFilters = { vm.setFilter { RecipeFilter(query = it.query, sort = it.sort) } },
        onRecipe = onRecipe,
        onAdd = { onAdd(filter.categoryIds.singleOrNull()) },
    )
    if (showFilters) {
        FiltersSheet(
            filter = filter,
            categories = state.categories,
            tags = state.tags,
            resultCount = state.results.size,
            onChange = vm::setFilter,
            onDismiss = { showFilters = false },
        )
    }
}

/** Recetas: buscador, chips de categoría, filtros, orden y cuadrícula o lista (§8.1). */
@Composable
fun RecipesContent(
    state: RecipesState,
    filter: RecipeFilter,
    photoStore: PhotoStore,
    onQuery: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onAllCategories: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onToggleLayout: () -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onRecipe: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val tracker = rememberEntranceTracker()
    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 6.dp).staggeredEntrance(0, "header", tracker)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle("Recetas", Modifier.weight(1f))
                    IconButton(onClick = onToggleLayout) {
                        Icon(
                            if (state.grid) AppIcons.List else AppIcons.Grid,
                            contentDescription = if (state.grid) "Ver en lista" else "Ver en cuadrícula",
                            tint = c.ink,
                        )
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 18.dp).staggeredEntrance(1, "search", tracker),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchField(filter.query, onQuery, Modifier.weight(1f))
                FilterButton(filter.sheetFilterCount(), onOpenFilters)
            }
            LazyRow(
                Modifier.padding(top = 6.dp).staggeredEntrance(2, "chips", tracker),
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "all") {
                    ChoiceChip("Todas", selected = filter.categoryIds.isEmpty(), onClick = onAllCategories)
                }
                items(state.categories, key = { it.id }) { cat ->
                    ChoiceChip(
                        cat.name,
                        selected = cat.id in filter.categoryIds,
                        onClick = { onToggleCategory(cat.id) },
                        leading = cat.emoji,
                    )
                }
            }
            if (state.loaded && state.totalCount > 0) {
                ResultsBar(state.results.size, filter.sort, onSort)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (state.grid) 2 else 1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(if (state.grid) 14.dp else 8.dp),
            ) {
                if (state.loaded && state.results.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyResults(noRecipes = state.totalCount == 0, onAdd = onAdd, onClearFilters = onClearFilters)
                    }
                }
                itemsIndexed(state.results, key = { _, card -> card.id }) { i, card ->
                    val item = Modifier
                        .animateItem(
                            fadeInSpec = if (reduced) null else spring(stiffness = 600f),
                            placementSpec = if (reduced) null else spring(stiffness = 600f, visibilityThreshold = IntOffset.VisibilityThreshold),
                            fadeOutSpec = if (reduced) null else spring(stiffness = 600f),
                        )
                        .staggeredEntrance(3 + i, card.id, tracker)
                    if (state.grid) {
                        RecipeGridCard(card, photoStore, onClick = { onRecipe(card.id) }, modifier = item)
                    } else {
                        RecipeListRow(card, photoStore, onClick = { onRecipe(card.id) }, modifier = item)
                    }
                }
            }
        }
        AddRecipeFab(onAdd, Modifier.align(Alignment.BottomEnd).padding(18.dp))
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val focus = LocalFocusManager.current
    // Estado local: lo escrito se pinta al momento y el ViewModel filtra después (guarda la búsqueda al recrearse).
    var text by remember { mutableStateOf(query) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQuery(it)
        },
        modifier = modifier.testTag("recipes-search"),
        placeholder = { Text("Buscar recetas", maxLines = 1) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = c.muted) },
        trailingIcon = if (text.isEmpty()) null else ({
            IconButton(onClick = {
                text = ""
                onQuery("")
            }) { Icon(AppIcons.Close, contentDescription = "Borrar la búsqueda", tint = c.muted) }
        }),
        singleLine = true,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = appTextFieldColors(),
    )
}

@Composable
private fun FilterButton(active: Int, onClick: () -> Unit) {
    val c = LocalRecetarioColors.current
    Box {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .semantics { contentDescription = if (active == 0) "Filtros" else "Filtros, $active activos" },
            shape = CircleShape,
            color = if (active > 0) c.soft else c.surface,
            border = BorderStroke(1.dp, if (active > 0) c.accent else c.line),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AppIcons.Filter, contentDescription = null, tint = if (active > 0) c.accent else c.ink)
            }
        }
        if (active > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(c.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$active", color = c.onAccent, fontSize = 10.sp, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ResultsBar(count: Int, sort: SortOrder, onSort: (SortOrder) -> Unit) {
    val c = LocalRecetarioColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MutedText(resultsLabel(count), Modifier.weight(1f))
        Box {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Cambiar el orden") { open = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Sort, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(sortLabel(sort), color = c.ink, style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
                for (option in SortOrder.entries) {
                    DropdownMenuItem(
                        text = { Text(sortLabel(option), color = if (option == sort) c.accent else c.ink) },
                        onClick = {
                            onSort(option)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

internal fun sortLabel(sort: SortOrder) = when (sort) {
    SortOrder.RECENT -> "Recientes"
    SortOrder.ALPHABETIC -> "Alfabético"
}

@Composable
private fun EmptyResults(noRecipes: Boolean, onAdd: () -> Unit, onClearFilters: () -> Unit) {
    val c = LocalRecetarioColors.current
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (noRecipes) "📖" else "🔍", fontSize = 44.sp)
        Spacer(Modifier.size(12.dp))
        Text(
            if (noRecipes) "Aún no tienes recetas" else "Ninguna receta coincide",
            color = c.ink,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.size(4.dp))
        if (noRecipes) {
            TextButton(onClick = onAdd) { Text("Añadir la primera", color = c.accent) }
        } else {
            MutedText("Prueba con otra palabra o quita algún filtro.")
            TextButton(onClick = onClearFilters) { Text("Quitar los filtros", color = c.accent) }
        }
    }
}
