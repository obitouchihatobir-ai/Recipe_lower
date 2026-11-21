package app.recetario.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.settings.SettingsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Texto del menú completo sin ningún plato principal. */
const val FULL_MENU_EMPTY_TEXT =
    "Para proponerte un menú completo hace falta al menos un plato principal entre tus recetas."

/** Null mientras carga; [FullMenuState.menu] null si no hay ningún plato principal. */
data class FullMenuState(val menu: FullMenu?)

@Composable
fun FullMenuRoute(container: AppContainer, onBack: () -> Unit, onRecipe: (String) -> Unit) {
    val flow = remember(container) {
        container.pairingRepository.observeData()
            .map { d -> FullMenuState(fullMenu(d.recipes, d.catalog, d.ratings, d.weights)) }
            .flowOn(Dispatchers.Default)
    }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    FullMenuContent(state, onBack, onRecipe)
}

/** Entrada, principal, postre y bebida que combinan entre sí (§7.5), con el porqué. */
@Composable
fun FullMenuContent(state: FullMenuState?, onBack: () -> Unit, onRecipe: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    SettingsPage("Menú completo", onBack, tag = "full-menu", modifier = modifier) {
        val menu = state?.menu
        when {
            state == null -> Unit
            menu == null -> Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    FULL_MENU_EMPTY_TEXT,
                    color = c.muted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("full-menu-empty"),
                )
            }
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CourseRow("Entrada", menu.starter, "Sin entrada", onRecipe)
                CourseRow("Principal", menu.main, "", onRecipe)
                CourseRow("Postre", menu.dessert, "Sin postre", onRecipe)
                CourseRow("Bebida", menu.drink, "Sin bebida", onRecipe)
                Spacer(Modifier.height(4.dp))
                Text(menu.reason, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("full-menu-reason"))
                Text(
                    "Usa tus recetas siempre que puede y completa con ideas.",
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CourseRow(course: String, item: PairingItem?, missing: String, onRecipe: (String) -> Unit) {
    val c = LocalRecetarioColors.current
    val radius = LocalRecetarioShapes.current.radius
    val shape = RoundedCornerShape(radius)
    val ownRecipe = item?.takeIf { it.kind == "RECIPE" }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .let { m ->
                if (ownRecipe != null) m.clickable(role = Role.Button, onClickLabel = "Abrir la receta") { onRecipe(ownRecipe.id) } else m
            }
            .heightIn(min = 64.dp)
            .padding(12.dp)
            .testTag("full-menu-$course"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item != null) PairingIcon(item)
        Column(Modifier.weight(1f)) {
            Text(course.uppercase(), color = c.muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.W600)
            if (item != null) {
                Text(item.name, color = c.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.W600)
                Text(if (item.kind == "RECIPE") "De tu recetario" else "Idea", color = c.muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(missing, color = c.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
