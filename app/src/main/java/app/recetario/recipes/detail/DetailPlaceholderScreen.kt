package app.recetario.recipes.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.components.sharedRecipePhoto
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.recipes.list.DEFAULT_EMOJI

/**
 * Detalle provisional (la Tarea 11 lo sustituye): foto con la transición compartida `photo-$id`,
 * título y «Editar».
 */
@Composable
fun DetailPlaceholderScreen(container: AppContainer, recipeId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val c = LocalRecetarioColors.current
    val repo = container.recipeRepository
    val recipe by remember(recipeId) { repo.observeRecipe(recipeId) }.collectAsStateWithLifecycle(null)
    val categories by remember { repo.observeCategories() }.collectAsStateWithLifecycle(emptyList())
    val emoji = categories.firstOrNull { it.id == recipe?.categoryId }?.emoji ?: DEFAULT_EMOJI
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Box {
            PhotoOrPlaceholder(
                fileName = recipe?.coverPhoto,
                photoStore = container.photoStore,
                emoji = emoji,
                modifier = Modifier.sharedRecipePhoto(recipeId).fillMaxWidth().height(240.dp),
                contentDescription = recipe?.title,
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = c.surface, contentColor = c.ink),
            ) { Icon(AppIcons.Back, contentDescription = "Volver") }
        }
        Column(Modifier.padding(18.dp)) {
            ScreenTitle(recipe?.title.orEmpty())
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onEdit,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Editar") }
        }
    }
}
