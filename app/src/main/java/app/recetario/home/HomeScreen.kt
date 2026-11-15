package app.recetario.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.backup.BackupMessages
import app.recetario.backup.BackupNotice
import app.recetario.core.ui.components.AddRecipeFab
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.components.EntranceTracker
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.components.rememberEntranceTracker
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.components.staggeredEntrance
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.list.RecipeCardData
import app.recetario.recipes.list.RecipeGridCard

@Composable
fun HomeRoute(
    container: AppContainer,
    onCategory: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onBackup: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(container.recipeRepository, container.settingsRepository.backupStatus, container.clock),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, container.photoStore, onCategory, onRecipe, onAdd, onSettings, onAppearance, onBackup)
}

/** Inicio de la etapa 1: saludo, chips de categorías, recientes y favoritas (§6.3). */
@Composable
fun HomeContent(
    state: HomeState,
    photoStore: PhotoStore,
    onCategory: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val tracker = rememberEntranceTracker()
    // Día en que se ocultó la tarjeta de las copias: vuelve a salir al día siguiente.
    var noticeDismissedOn by rememberSaveable { mutableStateOf<String?>(null) }
    val notice = state.backupNotice?.takeIf { state.loaded && noticeDismissedOn != state.today?.toString() }
    Box(modifier.fillMaxSize().background(c.bg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fullWidth("header") {
                Header(state.greeting, onSettings, onAppearance, Modifier.staggeredEntrance(0, "header", tracker))
            }
            if (notice != null) {
                fullWidth("backup-notice") {
                    BackupNoticeCard(notice, onBackup, onDismiss = { noticeDismissedOn = state.today?.toString() })
                }
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
                                    ChoiceChip(cat.name, selected = false, onClick = { onCategory(cat.id) }, role = Role.Button, leading = cat.emoji)
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
private fun Header(greeting: String, onSettings: () -> Unit, onAppearance: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Row(modifier, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            MutedText(greeting)
            Spacer(Modifier.height(2.dp))
            ScreenTitle("¿Qué cocinamos hoy?")
        }
        // Zonas táctiles de 48 dp; el margen negativo del final alinea los iconos con el borde de la cuadrícula.
        IconButton(onClick = onAppearance, modifier = Modifier.testTag("home-appearance")) {
            Icon(AppIcons.Palette, contentDescription = "Apariencia", tint = c.ink)
        }
        IconButton(onClick = onSettings, modifier = Modifier.testTag("home-settings")) {
            Icon(AppIcons.Settings, contentDescription = "Ajustes", tint = c.ink)
        }
    }
}

/**
 * Tarjeta de las copias: el error de la copia automática en Drive («Abrir copias») o el recordatorio de los 30 días
 * («Hacer copia»). Ambos botones llevan a Copias de seguridad; la ✕ la oculta hasta mañana.
 */
@Composable
private fun BackupNoticeCard(notice: BackupNotice, onBackup: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(LocalRecetarioShapes.current.radius)
    val isError = notice is BackupNotice.Error
    val border = if (isError) MaterialTheme.colorScheme.error else c.line
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(c.surface, shape)
            .border(1.dp, border, shape)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp)
            .testTag("home-backup-card"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = 8.dp)) {
            Text(
                when (notice) {
                    is BackupNotice.Error -> notice.messages.joinToString("\n")
                    BackupNotice.Reminder -> BackupMessages.REMINDER
                },
                color = if (isError) MaterialTheme.colorScheme.error else c.ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.W600,
            )
            TextButton(
                onClick = onBackup,
                modifier = Modifier.heightIn(min = 48.dp).offset(x = (-12).dp).testTag("home-backup-action"),
            ) {
                Text(if (isError) "Abrir copias" else "Hacer copia", color = c.accent, fontWeight = FontWeight.W700)
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("home-backup-dismiss")) {
            Icon(AppIcons.Close, contentDescription = "Ocultar el aviso hasta mañana", tint = c.muted, modifier = Modifier.size(20.dp))
        }
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
