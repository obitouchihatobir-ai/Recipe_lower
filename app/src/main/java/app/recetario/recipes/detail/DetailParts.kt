package app.recetario.recipes.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.components.AnimatedCheck
import app.recetario.core.ui.components.FlavorWheel
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import kotlinx.coroutines.delay

/** Botón redondo de la cabecera: círculo de 34 dp con zona táctil de 48 dp. */
@Composable
fun RoundButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalRecetarioColors.current
    Box(
        modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** «♡» que al marcarse se vuelve «♥» en el color de acento y da un pequeño salto de escala. */
@Composable
fun FavoriteHeart(favorite: Boolean) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    var popped by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(favorite) {
        if (first) {
            first = false
        } else if (!reduced) {
            popped = true
            delay(300)
            popped = false
        }
    }
    val scale by animateFloatAsState(
        if (popped) 1.25f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "heart",
    )
    Text(
        if (favorite) "♥" else "♡",
        color = if (favorite) c.accent else c.ink,
        fontSize = 17.sp,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    )
}

/** Sube el contenido [by] hacia la cabecera sin dejar hueco (el cuerpo se solapa con la foto). */
fun Modifier.overlapAbove(by: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val up = by.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
}

/** Rueda de sabores con una leyenda de barras (una por sabor, 14 dp por nivel). */
@Composable
fun FlavorCard(profile: FlavorProfile, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val radius = LocalRecetarioShapes.current.radius
    val rows = listOf(
        "Dulce" to profile.sweet, "Salado" to profile.salty, "Ácido" to profile.sour,
        "Amargo" to profile.bitter, "Picante" to profile.spicy,
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(radius))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlavorWheel(profile, size = 120.dp)
        // La rueda ya describe el perfil completo a los lectores de pantalla.
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, value) in rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = c.ink, fontSize = 12.sp, modifier = Modifier.width(52.dp))
                    Box(Modifier.width((value * 14).dp).height(5.dp).clip(RoundedCornerShape(4.dp)).background(c.accent))
                }
            }
        }
    }
}

/** Cantidad que destella en el color de acento durante 300 ms cuando su texto cambia. */
@Composable
fun FlashingQuantity(text: String, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    val flash = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (text != previous) {
            previous = text
            if (!reduced) {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(300))
            }
        }
    }
    Text(
        text,
        modifier = modifier,
        color = lerp(c.ink, c.accent, flash.value),
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.End,
    )
}

/** Fila de ingrediente: casilla, nombre (tachado si está marcado) y cantidad (pulsable para las conversiones). */
@Composable
fun IngredientRow(
    ingredient: Ingredient,
    quantity: String,
    checked: Boolean,
    showDivider: Boolean,
    onToggle: () -> Unit,
    onQuantityTap: () -> Unit,
) {
    val c = LocalRecetarioColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Checkbox, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedCheck(checked)
            Spacer(Modifier.width(10.dp))
            Text(
                ingredient.name,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = if (checked) 0.5f else 1f },
                color = c.ink,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            if (quantity.isNotEmpty()) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onQuantityTap)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) { FlashingQuantity(quantity) }
            }
        }
        if (showDivider) HorizontalDivider(color = c.line)
    }
}

/** Botón de borde discontinuo «Lo que necesito → a la compra». */
@Composable
fun NeedButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    c.accent,
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("🛒 Lo que necesito → a la compra", color = c.accent, style = MaterialTheme.typography.labelLarge)
    }
}

/** Aviso inferior breve («✓ 7 ingredientes añadidos…»). Se anuncia a los lectores de pantalla. */
@Composable
fun Toast(message: String?, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
        exit = if (reduced) ExitTransition.None else fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 2 },
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = c.ink,
            contentColor = c.bg,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                last,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W600,
            )
        }
    }
}
