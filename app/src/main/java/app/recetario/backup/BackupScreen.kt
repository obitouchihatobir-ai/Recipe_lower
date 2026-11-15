package app.recetario.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recetario.AppContainer
import app.recetario.core.ui.components.MutedText
import app.recetario.core.ui.components.SectionTitle
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.LocalRecetarioShapes
import app.recetario.settings.BottomPanel
import app.recetario.settings.SettingsPage
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Tipos que ofrece el selector al importar (algunos proveedores marcan los .zip de otra forma o como binario genérico). */
private val IMPORT_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/** «Copias de seguridad»: exportar e importar un zip. [documents] null usa los del sistema. */
@Composable
fun BackupRoute(container: AppContainer, onBack: () -> Unit, documents: BackupDocuments? = null) {
    val context = LocalContext.current
    val docs = documents ?: remember(context) { ContentResolverDocuments(context.applicationContext.contentResolver) }
    val vm: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(container.backupManager, container.settingsRepository, container.clock, docs),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    LaunchedEffect(vm) { vm.messages.collect { say(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri.toString())
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onImportFilePicked(uri.toString())
    }
    val saveOutsideLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.saveOutside(uri.toString())
    }
    BackupContent(
        state = state,
        zone = container.clock.zone,
        snackbar = snackbar,
        onBack = onBack,
        onExport = {
            try {
                exportLauncher.launch(vm.suggestedFileName())
            } catch (_: ActivityNotFoundException) {
                say(BackupMessages.NO_PICKER)
            }
        },
        onImport = {
            try {
                importLauncher.launch(IMPORT_TYPES)
            } catch (_: ActivityNotFoundException) {
                say(BackupMessages.NO_PICKER)
            }
        },
        onRestoreLocal = vm::restoreLocal,
        onSaveOutside = { name ->
            vm.prepareSaveOutside(name)
            try {
                saveOutsideLauncher.launch(name)
            } catch (_: ActivityNotFoundException) {
                say(BackupMessages.NO_PICKER)
            }
        },
        onChooseMerge = vm::chooseMerge,
        onChooseReplace = vm::chooseReplace,
        onConfirmReplace = vm::confirmReplace,
        onDismissPanel = vm::dismissPanel,
    )
}

@Composable
fun BackupContent(
    state: BackupUiState,
    zone: ZoneId,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestoreLocal: (fileName: String) -> Unit,
    onSaveOutside: (fileName: String) -> Unit,
    onChooseMerge: () -> Unit,
    onChooseReplace: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDismissPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    val busy = state.work != null
    SettingsPage(
        title = "Copias de seguridad",
        onBack = onBack,
        tag = "backup-screen",
        modifier = modifier,
        snackbar = snackbar,
        overlay = {
            when (val panel = state.panel) {
                is ImportPanel.ChooseMode -> ImportModePanel(
                    restoring = panel.source is ImportSource.Local,
                    onMerge = onChooseMerge,
                    onReplace = onChooseReplace,
                    onDismiss = onDismissPanel,
                )
                is ImportPanel.ConfirmReplace -> ConfirmReplacePanel(
                    comparison = replaceComparison(panel.preview.backupRecipes, panel.preview.localRecipes),
                    onConfirm = onConfirmReplace,
                    onDismiss = onDismissPanel,
                )
                null -> Unit
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp).testTag("backup-list"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BackupCard(
                title = "Guardar una copia",
                text = "Un archivo .zip con tus recetas y sus fotos, las categorías, la lista de la compra y los ajustes. " +
                    "Guárdalo fuera del teléfono: en Google Drive, en Descargas…",
            ) {
                Button(
                    onClick = onExport,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("backup-export"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                ) { Text("Exportar copia", fontSize = 15.sp, fontWeight = FontWeight.W700) }
                if (state.statusLoaded) {
                    MutedText(lastExternalText(state.lastExternal, zone), Modifier.testTag("backup-last-external"))
                }
            }
            BackupCard(
                title = "Recuperar una copia",
                text = "Abre un archivo .zip del Recetario. Podrás combinarlo con lo que ya tienes o reemplazarlo todo.",
            ) {
                OutlinedButton(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("backup-import"),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, if (busy) c.line else c.accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
                ) { Text("Importar copia", fontSize = 15.sp, fontWeight = FontWeight.W700) }
            }
            if (state.work != null) WorkingRow(state.work)
            BackupCard(
                title = "Copias en el teléfono",
                text = "Se guarda una antes de reemplazar o actualizar recetas al importar. Puedes volver a ella o guardarla fuera.",
            ) {
                val local = state.localBackups
                if (local != null && local.isEmpty()) {
                    MutedText("Todavía no hay ninguna.", Modifier.testTag("local-backups-empty"))
                }
                local?.forEachIndexed { i, backup ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    LocalBackupRow(backup, enabled = !busy, onRestore = onRestoreLocal, onSaveOutside = onSaveOutside)
                }
            }
        }
    }
}

/** Una copia del teléfono: cuándo y de qué tipo, cuántas recetas (o «Copia dañada», sin acciones) y qué hacer con ella. */
@Composable
private fun LocalBackupRow(backup: LocalBackup, enabled: Boolean, onRestore: (String) -> Unit, onSaveOutside: (String) -> Unit) {
    val c = LocalRecetarioColors.current
    val name = backup.fileName
    Column(Modifier.fillMaxWidth().testTag("local-backup-$name"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(localBackupTitle(name), color = c.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.W600)
        if (backup.recipes == null) {
            Text(BackupMessages.DAMAGED, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else {
            MutedText(recipeCountText(backup.recipes), fontSizeSp = 14)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onRestore(name) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("local-restore-$name"),
                ) { Text("Restaurar", color = if (enabled) c.accent else c.muted, fontWeight = FontWeight.W700) }
                TextButton(
                    onClick = { onSaveOutside(name) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("local-save-$name"),
                ) { Text("Guardar fuera", color = if (enabled) c.accent else c.muted, fontWeight = FontWeight.W700) }
            }
        }
    }
}

@Composable
private fun BackupCard(title: String, text: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(LocalRecetarioShapes.current.radius)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(title)
        Text(text, color = c.muted, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

/** Aviso de trabajo en curso (se anuncia con el lector de pantalla). Sin animación si se han quitado. */
@Composable
private fun WorkingRow(work: BackupWork) {
    val c = LocalRecetarioColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("backup-progress"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!LocalReducedMotion.current) CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.5.dp)
        Text(
            when (work) {
                BackupWork.EXPORTING -> "Guardando la copia…"
                BackupWork.CHECKING -> "Comprobando la copia…"
                BackupWork.IMPORTING -> "Importando la copia…"
            },
            color = c.ink,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Primera confirmación. Al restaurar una copia del teléfono solo se ofrece «Reemplazar todo». */
@Composable
private fun BoxScope.ImportModePanel(restoring: Boolean, onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalRecetarioColors.current
    BottomPanel(onDismiss, "import-mode-panel") {
        SectionTitle(if (restoring) "¿Restaurar esta copia?" else "¿Cómo quieres importarla?")
        if (!restoring) {
            ModeOption(
                title = "Combinar",
                explanation = "Añade lo que falte y actualiza lo más reciente. No borra nada.",
                color = c.ink,
                border = c.line,
                tag = "import-merge",
                onClick = onMerge,
            )
        }
        ModeOption(
            title = "Reemplazar todo",
            explanation = "Sustituye todas tus recetas por las de la copia. Antes se guarda una copia de lo actual.",
            color = MaterialTheme.colorScheme.error,
            border = MaterialTheme.colorScheme.error,
            tag = "import-replace",
            onClick = onReplace,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = c.muted) }
        }
    }
}

@Composable
private fun ModeOption(title: String, explanation: String, color: Color, border: Color, tag: String, onClick: () -> Unit) {
    val c = LocalRecetarioColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W700)
        Text(explanation, color = c.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Segunda confirmación de «Reemplazar todo», en rojo. */
@Composable
private fun BoxScope.ConfirmReplacePanel(comparison: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalRecetarioColors.current
    BottomPanel(onDismiss, "replace-confirm-panel") {
        SectionTitle("¿Reemplazar todo?")
        Text(comparison, color = c.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.W700)
        Text(
            "Tus recetas, categorías, lista de la compra y ajustes se sustituirán por los de la copia. " +
                "Antes se guardará en el teléfono una copia de lo que tienes ahora.",
            color = c.ink,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("replace-cancel")) {
                Text("Cancelar", color = c.muted)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).testTag("replace-confirm"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Sí, reemplazar todo", fontWeight = FontWeight.W700) }
        }
    }
}
