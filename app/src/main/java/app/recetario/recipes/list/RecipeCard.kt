package app.recetario.recipes.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.components.PhotoOrPlaceholder
import app.recetario.core.ui.components.sharedRecipePhoto
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.RecipeSummary

/** Lo que pinta una tarjeta de receta. */
@Immutable
data class RecipeCardData(
    val id: String,
    val title: String,
    val coverPhoto: String?,
    val emoji: String,
    /** «Bebida · ácida · 10 min». */
    val meta: String,
    /** Sabor que da color al punto de la tarjeta («dulce», «salada»…), o null. */
    val taste: String?,
)

const val DEFAULT_EMOJI = "🍽"

/** Comidas, Bebidas y Postres se nombran por su tipo («Bebida»); las categorías propias, por su nombre. */
fun RecipeSummary.toCardData(categories: Map<String, Category>): RecipeCardData {
    val category = categories[categoryId]
    val kind = category?.let { if (it.isDefault) it.baseType.label else it.name }
    return RecipeCardData(
        id = id,
        title = title,
        coverPhoto = coverPhoto,
        emoji = category?.emoji ?: DEFAULT_EMOJI,
        meta = recipeMeta(kind, flavor, totalMinutes),
        taste = tasteWord(flavor),
    )
}

private fun tasteColor(taste: String?): Color? = when (taste) {
    "dulce" -> Color(0xFFE59BB3)
    "salada" -> Color(0xFFD9603F)
    "ácida" -> Color(0xFFE3C94A)
    "picante" -> Color(0xFFC23B22)
    "amarga" -> Color(0xFF8C6A4F)
    else -> null
}

@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val reduced = LocalReducedMotion.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, tween(150), label = "cardPress")
    return scale
}

@Composable
private fun MetaLine(card: RecipeCardData) {
    val c = LocalRecetarioColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        tasteColor(card.taste)?.let { dot ->
            Box(Modifier.size(7.dp).background(dot, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            card.meta,
            color = c.muted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tarjeta de la cuadrícula: foto de 104 dp, título en dos líneas como máximo (con «…») y datos. */
@Composable
fun RecipeGridCard(
    card: RecipeCardData,
    photoStore: PhotoStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharePhoto: Boolean = true,
) {
    val c = LocalRecetarioColors.current
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(LocalRecetarioShapes.current.radius))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        PhotoOrPlaceholder(
            fileName = card.coverPhoto,
            photoStore = photoStore,
            emoji = card.emoji,
            modifier = Modifier
                .sharedRecipePhoto(card.id, enabled = sharePhoto)
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(LocalRecetarioShapes.current.radius)),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            card.title,
            color = c.ink,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        MetaLine(card)
    }
}

/** Fila de la vista en lista: miniatura de 64 dp a la izquierda. */
@Composable
fun RecipeListRow(
    card: RecipeCardData,
    photoStore: PhotoStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val radius = LocalRecetarioShapes.current.radius
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(c.surface)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoOrPlaceholder(
            fileName = card.coverPhoto,
            photoStore = photoStore,
            emoji = card.emoji,
            modifier = Modifier
                .sharedRecipePhoto(card.id)
                .size(64.dp)
                .clip(RoundedCornerShape(radius - 6.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                card.title,
                color = c.ink,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            MetaLine(card)
        }
    }
}
