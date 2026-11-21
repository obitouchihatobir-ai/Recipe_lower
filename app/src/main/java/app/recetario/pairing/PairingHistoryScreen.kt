package app.recetario.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.icons.AppIcons
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.settings.SettingsPage
import kotlinx.coroutines.launch

const val HISTORY_EMPTY_TEXT = "Aún no has valorado ninguna pareja. Usa 👍 o 👎 en las sugerencias de maridaje."

@Composable
fun PairingHistoryRoute(container: AppContainer, onBack: () -> Unit, onCompare: (RatedPairView) -> Unit) {
    val flow = remember(container) { container.pairingRepository.observeHistory() }
    val history by flow.collectAsStateWithLifecycle(initialValue = null)
    PairingHistoryContent(
        history = history,
        onRate = { pair, liked ->
            container.applicationScope.launch { container.pairingRepository.rate(pair.a, pair.b, liked) }
        },
        onCompare = onCompare,
        onBack = onBack,
    )
}

/** Las parejas valoradas, de la más reciente a la más antigua: 👍/👎 se cambian y «Quitar valoración» la borra. */
@Composable
fun PairingHistoryContent(
    history: List<RatedPairView>?,
    onRate: (RatedPairView, Boolean?) -> Unit,
    onCompare: (RatedPairView) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    SettingsPage("Historial de maridaje", onBack, tag = "pairing-history-screen", modifier = modifier) {
        when {
            history == null -> Unit
            history.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                Text(HISTORY_EMPTY_TEXT, color = c.muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(Modifier.fillMaxSize().testTag("pairing-history-list"), contentPadding = PaddingValues(start = 18.dp, end = 6.dp, bottom = 24.dp)) {
                items(history, key = { it.pairKey }) { pair ->
                    Column(Modifier.testTag("history-${pair.pairKey}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clickable(role = Role.Button, onClickLabel = "Comparar sabores") { onCompare(pair) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PairingIcon(pair.a)
                                Column(Modifier.weight(1f)) {
                                    Text("${pair.a.name} + ${pair.b.name}", color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W600)
                                    Text(
                                        if (pair.liked) "Te gusta" else "No te gusta: no se sugiere",
                                        color = c.muted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            RateButtons(pair.liked, { onRate(pair, it) })
                            IconButton(onClick = { onRate(pair, null) }) {
                                Icon(AppIcons.Close, contentDescription = "Quitar valoración", tint = c.ink)
                            }
                        }
                        HorizontalDivider(color = c.line)
                    }
                }
            }
        }
    }
}
