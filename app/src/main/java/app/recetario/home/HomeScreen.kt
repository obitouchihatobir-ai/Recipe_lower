package app.recetario.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.AddRecipeFab
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.EntranceTracker
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.rememberEntranceTracker
import app.recetario.core.ui.components.staggeredEntrance
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.list.RecipeCardData
import app.recetario.recipes.list.RecipeGridCard

@Composable
fun HomeRoute(
    container: AppContainer,
    onCategory: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.recipeRepository, container.clock))
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, container.photoStore, onCategory, onRecipe, onAdd)
}

/** Inicio de la etapa 1: saludo, chips de categorías, recientes y favoritas (§6.3). */
@Composable
fun HomeContent(
    state: HomeState,
    photoStore: PhotoStore,
    onCategory: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val tracker = rememberEntranceTracker()
    Box(modifier.fillMaxSize().background(c.bg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fullWidth("header") {
                Header(state.greeting, Modifier.staggeredEntrance(0, "header", tracker))
            }
            when {
                !state.loaded -> Unit
                state.isEmpty -> fullWidth("empty") {
                    EmptyHome(onAdd, Modifier.staggeredEntrance(1, "empty", tracker))
                }
                else -> {
                    if (state.categories.isNotEmpty()) {
                        fullWidth("chips") {
                            LazyRow(
                                Modifier.staggeredEntrance(1, "chips", tracker).padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(state.categories, key = { it.id }) { cat ->
                                    ChoiceChip(cat.name, selected = false, onClick = { onCategory(cat.id) }, leading = cat.emoji)
                                }
                            }
                        }
                    }
                    section("recent", "Recientes", state.recent, 2, tracker, photoStore, onRecipe, sharePhotos = true)
                    val recentIds = state.recent.mapTo(HashSet()) { it.id }
                    if (state.favorites.isEmpty()) {
                        fullWidth("favorites-title") {
                            SectionTitle("Favoritas", Modifier.padding(top = 10.dp).staggeredEntrance(4, "favorites-title", tracker))
                        }
                        fullWidth("favorites-empty") {
                            MutedText("Marca una receta como favorita y la tendrás siempre a mano aquí.")
                        }
                    } else {
                        section(
                            "favorites", "Favoritas", state.favorites, 4, tracker, photoStore, onRecipe,
                            // Una misma receta no puede tener dos fotos compartidas en la pantalla.
                            sharePhotos = false, sharePhotoOf = { it.id !in recentIds },
                        )
                    }
                }
            }
        }
        AddRecipeFab(onAdd, Modifier.align(Alignment.BottomEnd).padding(18.dp))
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.section(
    key: String,
    title: String,
    cards: List<RecipeCardData>,
    firstIndex: Int,
    tracker: EntranceTracker,
    photoStore: PhotoStore,
    onRecipe: (String) -> Unit,
    sharePhotos: Boolean,
    sharePhotoOf: (RecipeCardData) -> Boolean = { sharePhotos },
) {
    fullWidth("$key-title") {
        SectionTitle(title, Modifier.padding(top = 10.dp).staggeredEntrance(firstIndex, "$key-title", tracker))
    }
    itemsIndexed(cards, key = { _, card -> "$key-${card.id}" }) { i, card ->
        RecipeGridCard(
            card, photoStore, onClick = { onRecipe(card.id) },
            modifier = Modifier.staggeredEntrance(firstIndex + 1 + i, "$key-${card.id}", tracker),
            sharePhoto = sharePhotoOf(card),
        )
    }
}

@Composable
private fun Header(greeting: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        MutedText(greeting)
        Spacer(Modifier.height(2.dp))
        ScreenTitle("¿Qué cocinamos hoy?")
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Column(
        modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🍳", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Aún no tienes recetas",
            color = c.ink,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        MutedText("Apunta tu primera receta y aparecerá aquí.", fontSizeSp = 14)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) { Text("Añadir la primera") }
    }
}
