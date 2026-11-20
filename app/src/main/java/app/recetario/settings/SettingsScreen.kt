package app.recetario.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onPantryBasics: () -> Unit = {},
) {
    val repo = container.settingsRepository
    val basics by repo.pantryBasics.collectAsStateWithLifecycle(initialValue = null)
    val theme by repo.themeSettings.collectAsStateWithLifecycle(initialValue = ThemeSettings())
    val cupMl by repo.cupMl.collectAsStateWithLifecycle(initialValue = 240)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    SettingsContent(
        appearanceSummary = if (theme.auto) "Automático · ${theme.dayTheme.label()} de día" else theme.choice.label(),
        cupMl = cupMl,
        onCupMl = { ml -> scope.launch { snackbar.reportFailure("No se pudo guardar la medida de la taza.") { repo.setCupMl(ml) } } },
        snackbar = snackbar,
        onBack = onBack,
        onAppearance = onAppearance,
        onCategories = onCategories,
        onTags = onTags,
        onBackup = onBackup,
        onAbout = onAbout,
        pantryBasicsSummary = basics?.let(::basicsSummary).orEmpty(),
        onPantryBasics = onPantryBasics,
    )
}

/** «sal, agua, aceite, pimienta…» (los 4 primeros), o «Ninguno: cuentan todos los ingredientes». */
private fun basicsSummary(basics: List<String>): String = when {
    basics.isEmpty() -> "Ninguno: cuentan todos los ingredientes"
    basics.size <= 4 -> basics.joinToString(", ")
    else -> basics.take(4).joinToString(", ") + "…"
}

/** Ajustes (§6.3): apariencia, unidades, básicos de la despensa, categorías, etiquetas, copias de seguridad y «Acerca de». */
@Composable
fun SettingsContent(
    appearanceSummary: String,
    cupMl: Int,
    onCupMl: (Int) -> Unit,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onCategories: () -> Unit,
    onTags: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
    pantryBasicsSummary: String = "",
    onPantryBasics: () -> Unit = {},
) {
    SettingsPage("Ajustes", onBack, tag = "settings-screen", modifier = modifier, snackbar = snackbar) {
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
            SettingsRow(
                "Despensa › Básicos",
                pantryBasicsSummary.ifEmpty { null },
                Modifier.testTag("settings-pantry-basics"),
                onClick = onPantryBasics,
            )
            SettingsRow("Categorías", "Tus tipos de receta, con su emoji y su color", Modifier.testTag("settings-categories"), onClick = onCategories)
            SettingsRow("Etiquetas", "Renombra o borra las etiquetas de tus recetas", Modifier.testTag("settings-tags"), onClick = onTags)
            SettingsRow("Copias de seguridad", "Guarda o recupera tus recetas y sus fotos", Modifier.testTag("settings-backup"), onClick = onBackup)
            SettingsRow("Acerca de", "Versión, créditos y licencias", Modifier.testTag("settings-about"), onClick = onAbout)
        }
    }
}
