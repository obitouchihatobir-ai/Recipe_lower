package app.recetario.pantry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.settings.SettingsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Una receta en «¿Qué puedo hacer?»: su línea es «Tienes 5 de 6 · falta: cilantro» o «¡Tienes todo!». */
data class WhatCanIMakeRow(val recipeId: String, val title: String, val line: String, val complete: Boolean)

@Composable
fun WhatCanIMakeRoute(container: AppContainer, onBack: () -> Unit, onRecipe: (String) -> Unit) {
    val rowsFlow = remember(container) {
        combine(
            container.recipeRepository.observeSummaries(),
            container.pantryRepository.observe(),
            container.settingsRepository.pantryBasics,
        ) { recipes, pantry, basics ->
            val titles = recipes.associate { it.id to it.title }
            pantryCoverage(recipes, pantry.map { it.name }, basics).map {
                WhatCanIMakeRow(it.recipeId, titles.getValue(it.recipeId), coverageLine(it), it.missing.isEmpty())
            }
        }.flowOn(Dispatchers.Default)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = null)
    WhatCanIMakeContent(rows, onBack, onRecipe)
}

/** Recetas ordenadas por lo que hay en la despensa, sin contar los básicos (§7.2). */
@Composable
fun WhatCanIMakeContent(rows: List<WhatCanIMakeRow>?, onBack: () -> Unit, onRecipe: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRecetarioColors.current
    SettingsPage("¿Qué puedo hacer?", onBack, tag = "what-can-i-make", modifier = modifier) {
        when {
            rows == null -> Unit
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Cuando tengas recetas con ingredientes, aquí verás cuáles puedes hacer.",
                    color = c.muted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize().testTag("what-can-i-make-list"), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                items(rows, key = { it.recipeId }) { row ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClickLabel = "Abrir la receta") { onRecipe(row.recipeId) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .testTag("what-can-i-make-${row.recipeId}"),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        SectionTitle(row.title)
                        // Opaco: acento si está todo, `muted` si falta algo (contraste ≥ 4,5:1).
                        Text(row.line, color = if (row.complete) c.accent else c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
