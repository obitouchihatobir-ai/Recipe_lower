package app.recetario.core.data

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recetario.AppContainer
import app.recetario.backup.BackupMessages
import app.recetario.backup.ExportResult
import app.recetario.backup.LocalBackupKind
import app.recetario.backup.parseLocalBackupName
import app.recetario.backup.sortLocalBackupsNewestFirst
import app.recetario.core.ui.theme.LocalRecetarioColors
import kotlinx.coroutines.launch
import java.io.File

/** La copia de la base de datos de antes de actualizar más reciente de [backupsDir], o null si no hay ninguna. */
fun newestUpdateCopy(backupsDir: File): String? =
    sortLocalBackupsNewestFirst(backupsDir.list().orEmpty().filter { parseLocalBackupName(it)?.kind == LocalBackupKind.BEFORE_UPDATE })
        .firstOrNull()

/**
 * La base de datos no se puede abrir (diseño de la etapa 2, §6.2): no se hizo la copia previa (no se migra) o la
 * migración falló. Solo muestra el motivo y, si hay copia previa ([copyFileName]), «Guardar la copia fuera del
 * teléfono». No abre la base de datos ni se cierra: al volver a abrir Sazón se intenta de nuevo.
 */
@Composable
fun UpgradeErrorRoute(container: AppContainer, message: String, copyFileName: String?) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    fun say(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && copyFileName != null) {
            busy = true
            scope.launch {
                val result = container.backupManager.copyLocalToDocument(copyFileName, uri.toString())
                busy = false
                say(if (result is ExportResult.Ok) BackupMessages.EXPORTED else BackupMessages.EXPORT_FAILED)
            }
        }
    }
    UpgradeErrorContent(
        message = message,
        canSaveOutside = copyFileName != null,
        busy = busy,
        snackbar = snackbar,
        onSaveOutside = {
            try {
                launcher.launch(copyFileName)
            } catch (_: ActivityNotFoundException) {
                say(BackupMessages.NO_PICKER)
            }
        },
    )
}

@Composable
fun UpgradeErrorContent(
    message: String,
    canSaveOutside: Boolean,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onSaveOutside: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecetarioColors.current
    Box(modifier.fillMaxSize().background(c.bg).safeDrawingPadding().testTag("upgrade-error")) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No se puede abrir Sazón",
                color = c.ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.W700,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                message,
                color = c.ink,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("upgrade-error-message"),
            )
            if (canSaveOutside) {
                Button(
                    onClick = onSaveOutside,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("upgrade-save-outside"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                ) { Text("Guardar la copia fuera del teléfono", fontSize = 15.sp, fontWeight = FontWeight.W700) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
