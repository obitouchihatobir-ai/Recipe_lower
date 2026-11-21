package app.recetario.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.today.TodaySlot

/** El texto de la tarjeta Hoy sin recetas (diseño 2, §7.3). */
const val TODAY_EMPTY_TEXT = "Cuando tengas recetas, aquí te propondré qué cocinar."

/** El enlace de un hueco sin recetas: crea una receta de ese tipo. */
private fun TodaySlot.createLabel(): String = when (this) {
    TodaySlot.DRINK -> "Crear una"
    TodaySlot.DESSERT, TodaySlot.MAIN -> "Crear uno"
    TodaySlot.SNACK -> "Crear algo"
}

private val TodaySlot.tag: String get() = name.lowercase()

/** El contenedor de la tarjeta, con el estilo de cada tema (§6.1): degradado terracota, verde claro u oscuro con borde ámbar. */
@Composable
private fun TodayContainer(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(LocalRecetarioShapes.current.radius)
    val border = c.todayBorder
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(c.todayBrush, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.semantics { heading() },
        color = LocalRecetarioColors.current.todayInk,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
    )
}

/**
 * La tarjeta Hoy de Inicio: el momento y la estación, el tiempo de ahora (si lo hay), el porqué, la receta que manda
 * en grande y las demás en mini tarjetas, «Empezar» (modo cocina) y «Otra idea». Tocar una receta abre su detalle;
 * un hueco vacío enlaza a crear una receta de ese tipo. El cambio de menú es un fundido (al instante sin animaciones).
 */
@Composable
fun TodayCard(
    state: TodayState,
    onRecipe: (String) -> Unit,
    onStart: (id: String, servings: Int) -> Unit,
    onAnotherIdea: () -> Unit,
    onCreate: (TodaySlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val reduced = LocalReducedMotion.current
    TodayContainer(modifier.testTag("today-card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(state.eyebrow, Modifier.weight(1f).testTag("today-eyebrow"))
            state.weather?.let { WeatherChip(it) }
        }
        Crossfade(
            targetState = state.reason to state.items,
            animationSpec = if (reduced) snap<Float>() else tween<Float>(250),
            label = "todayMenu",
        ) { (reason, items) ->
            Column {
                Text(
                    reason,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp).testTag("today-reason"),
                    color = c.todayInk,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                )
                val first = items.firstOrNull()
                if (first != null) TodaySlotItem(first, big = true, onRecipe, onCreate, Modifier.fillMaxWidth())
                val rest = items.drop(1)
                if (rest.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (item in rest) {
                            TodaySlotItem(item, big = false, onRecipe, onCreate, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val lead = state.lead
            if (lead != null) {
                Button(
                    onClick = { onStart(lead.id, lead.servings) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today-start"),
                    colors = ButtonDefaults.buttonColors(containerColor = c.todayButton, contentColor = c.todayButtonInk),
                ) { Text("Empezar", fontWeight = FontWeight.W600) }
            }
            OutlinedButton(
                onClick = onAnotherIdea,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today-another"),
                border = BorderStroke(1.dp, c.todayGhostLine),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.todayInk),
            ) { Text("Otra idea", fontWeight = FontWeight.W600) }
        }
    }
}

/** El tiempo de ahora, pequeño: icono, «24 °C · Madrid». TalkBack lo lee entero en palabras. */
@Composable
private fun WeatherChip(chip: TodayWeatherChip) {
    val c = LocalRecetarioColors.current
    Row(
        Modifier
            .padding(start = 8.dp)
            // La descripción manda sobre el texto: TalkBack lee «El tiempo ahora: lluvia, 9 °C en Madrid».
            .semantics(mergeDescendants = true) { contentDescription = chip.description }
            .testTag("today-weather"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(chip.emoji, fontSize = 14.sp, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.width(4.dp))
        Text(chip.text, color = c.todayInk, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TodaySlotItem(
    item: TodayCardItem,
    big: Boolean,
    onRecipe: (String) -> Unit,
    onCreate: (TodaySlot) -> Unit,
    modifier: Modifier,
) {
    val recipe = item.recipe
    if (recipe == null) MissingSlot(item.slot, onCreate, modifier) else RecipeItem(recipe, item.slot, big, onRecipe, modifier)
}

@Composable
private fun RecipeItem(recipe: TodayCardRecipe, slot: TodaySlot, big: Boolean, onRecipe: (String) -> Unit, modifier: Modifier) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(maxOf(LocalRecetarioShapes.current.radius - 6.dp, 8.dp))
    val iconSize = if (big) 48.dp else 36.dp
    val icon = @Composable {
        Box(
            Modifier.size(iconSize).background(c.todayIcon, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // El emoji de la categoría se lee por su nombre («Bebidas»).
            Text(
                recipe.emoji,
                fontSize = if (big) 24.sp else 19.sp,
                modifier = Modifier.clearAndSetSemantics { contentDescription = recipe.categoryName },
            )
        }
    }
    val texts = @Composable { m: Modifier ->
        Column(m) {
            Text(
                recipe.title,
                modifier = Modifier.testTag("today-${slot.tag}-title"),
                color = c.todayInk,
                style = if (big) MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp)
                else MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(recipe.subtitle, color = c.todayInk, style = MaterialTheme.typography.bodySmall)
        }
    }
    val clickable = modifier
        .clip(shape)
        .background(c.todayItem, shape)
        .clickable(role = Role.Button, onClickLabel = "Ver receta") { onRecipe(recipe.id) }
        .heightIn(min = 48.dp)
        .padding(8.dp)
        .testTag("today-${slot.tag}")
    if (big) {
        Row(clickable, verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            texts(Modifier.weight(1f))
        }
    } else {
        Column(clickable, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            icon()
            texts(Modifier)
        }
    }
}

/** «Aún no tienes bebidas» y un enlace para crear una. */
@Composable
private fun MissingSlot(slot: TodaySlot, onCreate: (TodaySlot) -> Unit, modifier: Modifier) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(maxOf(LocalRecetarioShapes.current.radius - 6.dp, 8.dp))
    Column(
        modifier.background(c.todayItem, shape).padding(start = 10.dp, end = 4.dp, top = 8.dp).testTag("today-missing-${slot.tag}"),
    ) {
        Text(slot.missing, color = c.todayInk, style = MaterialTheme.typography.bodyMedium)
        TextButton(
            onClick = { onCreate(slot) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("today-create-${slot.tag}"),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text("+ ${slot.createLabel()}", color = c.todayInk, fontWeight = FontWeight.W700)
        }
    }
}

/** Sin recetas: «Cuando tengas recetas, aquí te propondré qué cocinar» y «+ Nueva receta». */
@Composable
fun TodayEmptyCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    TodayContainer(modifier.testTag("today-empty")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Hoy", Modifier.weight(1f))
            Text("🍳", fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics { contentDescription = "Sartén" })
        }
        Text(
            TODAY_EMPTY_TEXT,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            color = c.todayInk,
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = onAdd,
            modifier = Modifier.heightIn(min = 48.dp).testTag("today-new"),
            colors = ButtonDefaults.buttonColors(containerColor = c.todayButton, contentColor = c.todayButtonInk),
        ) { Text("+ Nueva receta", fontWeight = FontWeight.W600) }
    }
}
