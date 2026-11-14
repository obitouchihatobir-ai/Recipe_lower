package app.recetario.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.recetario.AppContainer
import app.recetario.core.ui.components.ChoiceChip
import app.recetario.core.ui.theme.ThemeSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onCategories: () -> Unit,
    onTags: () -> Unit,
    onAbout: () -> Unit,
) {
    val repo = container.settingsRepository
    val theme by repo.themeSettings.collectAsStateWithLifecycle(initialValue = ThemeSettings())
    val cupMl by repo.cupMl.collectAsStateWithLifecycle(initialValue = 240)
    val scope = rememberCoroutineScope()
    SettingsContent(
        appearanceSummary = if (theme.auto) "Automático · ${theme.dayTheme.label()} de día" else theme.choice.label(),
        cupMl = cupMl,
        onCupMl = { scope.launch { repo.setCupMl(it) } },
        onBack = onBack,
        onAppearance = onAppearance,
        onCategories = onCategories,
        onTags = onTags,
        onAbout = onAbout,
    )
}

/** Ajustes de la etapa 1 (§6.3): apariencia, unidades, categorías, etiquetas y «Acerca de». */
@Composable
fun SettingsContent(
    appearanceSummary: String,
    cupMl: Int,
    onCupMl: (Int) -> Unit,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onCategories: () -> Unit,
    onTags: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage("Ajustes", onBack, tag = "settings-screen", modifier = modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
            SettingsRow("Apariencia", appearanceSummary, Modifier.testTag("settings-appearance"), onClick = onAppearance)
            SettingsRow("Unidades", "Una taza equivale a:", Modifier.testTag("settings-units")) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (ml in listOf(240, 250)) {
                        ChoiceChip(
                            "$ml ml",
                            selected = cupMl == ml,
                            onClick = { onCupMl(ml) },
                            role = Role.RadioButton,
                            modifier = Modifier.testTag("cup-$ml"),
                        )
                    }
                }
            }
            SettingsRow("Categorías", "Tus tipos de receta, con su emoji y su color", Modifier.testTag("settings-categories"), onClick = onCategories)
            SettingsRow("Etiquetas", "Renombra o borra las etiquetas de tus recetas", Modifier.testTag("settings-tags"), onClick = onTags)
            SettingsRow("Acerca de", "Versión, créditos y licencias", Modifier.testTag("settings-about"), onClick = onAbout)
        }
    }
}
