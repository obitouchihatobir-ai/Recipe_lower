package app.recetario.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.core.ui.components.ScreenTitle
import app.recetario.core.ui.theme.LocalRecetarioColors

/** Maridaje en la etapa 1: un estado vacío que anuncia la función (§6.3). */
@Composable
fun PairingPlaceholderScreen(modifier: Modifier = Modifier) {
    ComingSoon(
        emoji = "🍷",
        title = "Maridaje",
        text = "Muy pronto: descubre qué bebida, plato o postre combina con cada receta.",
        modifier = modifier,
    )
}

/** Pantalla de «muy pronto», también para pestañas cuyo contenido llega en otra tarea. */
@Composable
fun ComingSoon(emoji: String, title: String, text: String, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    Column(
        modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        ScreenTitle(title)
        Spacer(Modifier.height(8.dp))
        Text(text, color = c.muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
