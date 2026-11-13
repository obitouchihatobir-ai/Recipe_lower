package app.recetario.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import kotlin.reflect.KClass

/** Las cuatro pestañas de §6.3, con los iconos del boceto. */
enum class Tab(val label: String, val icon: ImageVector, val tag: String, val routeClass: KClass<*>, val route: Any) {
    HOME("Inicio", AppIcons.Home, "tab-home", Home::class, Home),
    RECIPES("Recetas", AppIcons.Recipes, "tab-recipes", Recipes::class, Recipes()),
    SHOPPING("Compra", AppIcons.Shopping, "tab-shopping", Shopping::class, Shopping),
    PAIRING("Maridaje", AppIcons.Pairing, "tab-pairing", Pairing::class, Pairing),
}

@Composable
fun BottomBar(current: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.line)
        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
            for (tab in Tab.entries) {
                NavigationBarItem(
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) },
                    modifier = Modifier.testTag(tab.tag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.accent,
                        selectedTextColor = c.accent,
                        unselectedIconColor = c.muted,
                        unselectedTextColor = c.muted,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
