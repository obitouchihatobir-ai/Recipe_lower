package app.recetario.backup

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.LocalRecetarioColors
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.contrastRatio
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.Settings
import app.recetario.settings.BackupStatus
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class BackupScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    /** Los documentos de fuera que ven la pantalla y las copias (el archivo elegido al importar, lo exportado…). */
    private val documents = FakeDocuments()

    @Before
    fun setUp() {
        container = installTestContainer(documents = documents)
    }

    @After
    fun tearDown() = container.database.close()

    /** Selector de archivos del sistema falso: responde al momento con [uri] y anota lo que se le pidió. */
    private class FakePicker(private val uri: Uri) : ActivityResultRegistryOwner {
        val inputs = mutableListOf<Any?>()
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                inputs += input
                dispatchResult(requestCode, uri)
            }
        }
    }

    private val pickedUri = Uri.parse("content://prueba/Recetario-2026-11-13.zip")

    private fun launch(picker: FakePicker = FakePicker(pickedUri)): FakePicker {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                RecetarioTheme(ThemeChoice.CALIDO) { BackupRoute(container, onBack = {}) }
            }
        }
        rule.waitFor(hasTestTag("backup-screen"))
        return picker
    }

    private fun recipeIds() = runBlocking { container.recipeRepository.observeSummaries().first().map { it.id }.sorted() }

    private fun backupWithPaella() = backupZip(backupOf(sampleRecipe("copia", "Paella de la copia", updatedAt = 900)))

    @Test
    fun replaceAll_needsASecondConfirmation() {
        runBlocking { container.recipeRepository.save(sampleRecipe("local", "Tortilla local")) }
        documents.input = backupWithPaella()
        val picker = launch()

        rule.onNodeWithTag("backup-import").performClick()
        rule.waitFor(hasTestTag("import-mode-panel"))
        assertThat((picker.inputs.single() as Array<*>).toList())
            .containsExactly("application/zip", "application/x-zip-compressed", "application/octet-stream").inOrder()
        rule.onNodeWithText("Añade lo que falte y actualiza lo más reciente. No borra nada.").assertExists()
        rule.onNodeWithText("Sustituye todas tus recetas por las de la copia. Antes se guarda una copia de lo actual.").assertExists()

        // Primera confirmación: aún no se ha tocado nada.
        rule.onNodeWithTag("import-replace").performClick()
        rule.waitFor(hasTestTag("replace-confirm-panel"))
        rule.onNodeWithTag("import-mode-panel").assertDoesNotExist()
        rule.onNodeWithText("La copia tiene 1 receta; ahora tienes 1.", substring = true).assertExists()
        // Solo se ha leído para comprobarla: nada ha cambiado.
        assertThat(documents.opened).containsExactly(pickedUri.toString())
        assertThat(recipeIds()).containsExactly("local")

        // Cancelar en la segunda: sigue sin cambiar nada.
        rule.onNodeWithTag("replace-cancel").performClick()
        rule.waitForGone(hasTestTag("replace-confirm-panel"))
        rule.waitForIdle()
        assertThat(documents.opened).hasSize(1)
        assertThat(recipeIds()).containsExactly("local")

        // Ahora sí: las dos confirmaciones.
        rule.onNodeWithTag("backup-import").performClick()
        rule.waitFor(hasTestTag("import-mode-panel"))
        rule.onNodeWithTag("import-replace").performClick()
        rule.waitFor(hasTestTag("replace-confirm"))
        rule.onNodeWithTag("replace-confirm").performClick()

        rule.waitFor(hasText("Copia importada: 1 receta, 0 fotos."))
        assertThat(recipeIds()).containsExactly("copia")
    }

    @Test
    fun merge_importsAfterOneChoice() {
        runBlocking { container.recipeRepository.save(sampleRecipe("local", "Tortilla local")) }
        documents.input = backupWithPaella()
        launch()

        rule.onNodeWithTag("backup-import").performClick()
        rule.waitFor(hasTestTag("import-merge"))
        rule.onNodeWithTag("import-merge").performClick()

        rule.waitFor(hasText("Copia importada: 1 receta, 0 fotos."))
        assertThat(recipeIds()).containsExactly("copia", "local")
    }

    @Test
    fun importError_isShownRightAfterChoosingTheFile() {
        documents.input = "no es un zip".toByteArray()
        launch()

        rule.onNodeWithTag("backup-import").performClick()

        rule.waitFor(hasText("Este archivo no es una copia del Recetario."))
        rule.onNodeWithTag("import-mode-panel").assertDoesNotExist()
    }

    /** Deja en el teléfono la copia previa de «Tortilla local» y en la app solo la paella de la copia. */
    private fun importOverLocalRecipe(): String {
        runBlocking {
            container.recipeRepository.save(sampleRecipe("local", "Tortilla local"))
            val result = container.backupManager.import(ByteArrayInputStream(backupWithPaella()), ImportMode.REPLACE_ALL)
            assertThat(result).isEqualTo(ImportResult.Ok(recipes = 1, images = 0))
        }
        return "antes-de-importar-2026-11-13-2140.zip"
    }

    @Test
    fun localCopies_restoreAsksTwice_andBringsBackThePreviousData() {
        val name = importOverLocalRecipe()
        launch()
        rule.waitFor(hasText("Antes de importar · 13 de noviembre de 2026, 21:40"))
        rule.onNodeWithText("1 receta").assertExists()

        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("local-restore-$name"))
        rule.onNodeWithTag("local-restore-$name").performClick()
        rule.waitFor(hasTestTag("import-mode-panel"))
        // Restaurar es siempre «Reemplazar todo»: no se ofrece combinar.
        rule.onNodeWithTag("import-merge").assertDoesNotExist()
        rule.onNodeWithTag("import-replace").performClick()
        rule.waitFor(hasTestTag("replace-confirm-panel"))
        rule.onNodeWithText("La copia tiene 1 receta; ahora tienes 1.", substring = true).assertExists()
        assertThat(recipeIds()).containsExactly("copia")
        rule.onNodeWithTag("replace-confirm").performClick()

        rule.waitFor(hasText("Copia importada: 1 receta, 0 fotos."))
        assertThat(recipeIds()).containsExactly("local")
        // La nueva copia previa (con la paella) aparece en la lista.
        rule.waitFor(hasTestTag("local-backup-antes-de-importar-2026-11-13-2140-2.zip"))
    }

    @Test
    fun localCopies_saveOutside_copiesTheFileExactly() {
        val name = importOverLocalRecipe()
        val picker = launch()
        rule.waitFor(hasTestTag("local-save-$name"))

        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("local-save-$name"))
        rule.onNodeWithTag("local-save-$name").performClick()

        rule.waitFor(hasText("Copia guardada."))
        assertThat(picker.inputs.single()).isEqualTo(name)
        val file = java.io.File(container.backupManager.localBackupsDir, name)
        assertThat(documents.written.toList()).isEqualTo(file.readBytes().toList())
    }

    @Test
    fun localCopies_damagedOnes_haveNoActions() {
        val dir = container.backupManager.localBackupsDir.apply { mkdirs() }
        java.io.File(dir, "antes-de-importar-2026-11-01-0900.zip").writeText("roto")
        launch()

        rule.waitFor(hasText("Copia dañada"))
        rule.onNodeWithTag("local-restore-antes-de-importar-2026-11-01-0900.zip").assertDoesNotExist()
        rule.onNodeWithTag("local-save-antes-de-importar-2026-11-01-0900.zip").assertDoesNotExist()
    }

    @Test
    fun export_savesTheZip_andRecordsLastExternal() {
        val picker = launch()
        rule.waitFor(hasText("Todavía no has guardado ninguna copia fuera del teléfono."))

        rule.onNodeWithTag("backup-export").performClick()

        rule.waitFor(hasText("Copia guardada."))
        assertThat(picker.inputs.single()).isEqualTo("Recetario-2026-11-13.zip")
        val entries = unzip(documents.written)
        assertThat(decodeBackup(String(entries.getValue("backup.json")))).isInstanceOf(BackupDecodeResult.Ok::class.java)
        rule.waitUntil(5_000) { runBlocking { container.settingsRepository.backupStatus.first().lastExternal } != null }
        assertThat(runBlocking { container.settingsRepository.backupStatus.first().lastExternal }).isEqualTo(testClock.instant())
        rule.waitFor(hasText("Última copia guardada fuera del teléfono: 13 de noviembre de 2026, 21:40"))
        assertThat(documents.deleted).isEmpty()
    }

    @Test
    fun export_failure_saysSo_deletesThePartialFile_andDoesNotRecordIt() {
        documents.failOutput = true
        launch()

        rule.onNodeWithTag("backup-export").performClick()

        rule.waitFor(hasText("No se pudo guardar la copia."))
        assertThat(documents.deleted).containsExactly(pickedUri.toString())
        assertThat(runBlocking { container.settingsRepository.backupStatus.first().lastExternal }).isNull()
    }

    private val driveUri = "content://drive/Recetario-copia-automatica.zip"

    private fun status() = runBlocking { container.settingsRepository.backupStatus.first() }

    private fun setStatus(transform: (BackupStatus) -> BackupStatus) = runBlocking { container.settingsRepository.updateBackupStatus(transform) }

    private fun saveRecipe() = runBlocking { container.recipeRepository.save(sampleRecipe("local", "Tortilla local")) }

    private fun tap(tag: String) {
        rule.waitFor(hasTestTag(tag))
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performClick()
    }

    @Test
    fun drive_setup_keepsThePermission_andSavesACopyRightAway() {
        saveRecipe()
        val picker = launch(FakePicker(Uri.parse(driveUri)))
        rule.waitFor(hasText("Última copia en el teléfono: todavía ninguna"))

        tap("drive-setup")

        rule.waitFor(hasText("Copia guardada en Google Drive."))
        assertThat(picker.inputs.single()).isEqualTo("Recetario-copia-automatica.zip")
        assertThat(documents.persisted).containsExactly(driveUri)
        val local = java.io.File(container.backupManager.localBackupsDir, "Recetario-2026-11-13-2140.zip")
        assertThat(documents.files.getValue(driveUri).toList()).isEqualTo(local.readBytes().toList())
        val now = testClock.instant()
        assertThat(status()).isEqualTo(BackupStatus(lastLocal = now, lastExternal = now, lastDrive = now, driveUri = driveUri))
        rule.waitFor(hasText("Última copia en Drive: 13 de noviembre de 2026, 21:40"))
        rule.onNodeWithText("Última copia en el teléfono: 13 de noviembre de 2026, 21:40").assertExists()
        rule.onNodeWithTag("drive-error").assertDoesNotExist()
        // La copia nueva también está en «Copias en el teléfono».
        rule.waitFor(hasTestTag("local-backup-Recetario-2026-11-13-2140.zip"))
    }

    @Test
    fun drive_failure_isShown_keepsTheLocalCopy_andRetryFixesIt() {
        saveRecipe()
        documents.persisted += driveUri
        setStatus { it.copy(driveUri = driveUri) }
        documents.failOutput = true
        launch()

        tap("drive-retry")

        rule.waitFor(hasTestTag("drive-error"))
        rule.waitUntil(5_000) { status().lastError != null }
        assertThat(status().lastError).isEqualTo("No se pudo guardar en Google Drive. Vuelve a elegir el archivo.")
        assertThat(status().lastLocal).isEqualTo(testClock.instant())
        assertThat(status().lastExternal).isNull()
        assertThat(container.backupManager.localBackupsDir.list()?.toList()).containsExactly("Recetario-2026-11-13-2140.zip")

        documents.failOutput = false
        tap("drive-retry")

        rule.waitFor(hasText("Copia guardada en Google Drive."))
        rule.waitForGone(hasTestTag("drive-error"))
        assertThat(status().lastError).isNull()
        assertThat(status().lastExternal).isEqualTo(testClock.instant())
    }

    @Test
    fun drive_lostPermission_showsTheError_andOnlyOffersChoosingAnotherFile() {
        saveRecipe()
        // El archivo de Drive se eligió, pero el permiso ya no está (se borró Drive, se revocó…).
        setStatus { it.copy(driveUri = driveUri) }
        val newUri = "content://drive/Recetario-copia-automatica (1).zip"
        val picker = launch(FakePicker(Uri.parse(newUri)))

        rule.waitFor(hasTestTag("drive-error"))
        rule.onNodeWithTag("drive-retry").assertDoesNotExist()
        // También lo verá en Inicio.
        rule.waitUntil(5_000) { status().lastError == BackupMessages.DRIVE_FAILED }

        tap("drive-choose")

        rule.waitFor(hasText("Copia guardada en Google Drive."))
        assertThat(picker.inputs.single()).isEqualTo("Recetario-copia-automatica.zip")
        assertThat(documents.persisted).containsExactly(newUri)
        assertThat(documents.released).containsExactly(driveUri)
        assertThat(status().driveUri).isEqualTo(newUri)
        assertThat(status().lastError).isNull()
        rule.waitForGone(hasTestTag("drive-error"))
        rule.waitFor(hasTestTag("drive-retry"))
    }

    @Test
    fun localError_isShown_andRetryWritesTheCopy() {
        saveRecipe()
        setStatus { it.copy(localError = BackupMessages.LOCAL_FAILED) }
        launch()

        rule.waitFor(hasTestTag("local-error"))
        rule.onNodeWithText("No se pudo hacer la copia semanal en el teléfono.").assertExists()
        tap("local-retry")

        rule.waitFor(hasText("Copia guardada."))
        rule.waitForGone(hasTestTag("local-error"))
        assertThat(status().localError).isNull()
        assertThat(container.backupManager.localBackupsDir.list()?.toList()).containsExactly("Recetario-2026-11-13-2140.zip")
    }

    @Test
    fun drive_existingCopy_asksBeforeReplacing_andReplaces() {
        saveRecipe()
        val existing = backupWithPaella()
        documents.files[driveUri] = existing
        val picker = launch(FakePicker(Uri.parse(driveUri)))

        tap("drive-setup")

        rule.waitFor(hasTestTag("drive-replace-panel"))
        rule.onNodeWithText("Ese archivo ya tiene una copia con 1 receta; ahora tienes 1.").assertExists()
        rule.onNodeWithTag("drive-import-first").assertExists()
        // Aún no se ha tocado nada: ni el archivo ni el estado ni el permiso.
        assertThat(documents.files.getValue(driveUri).toList()).isEqualTo(existing.toList())
        assertThat(documents.openedForWriting).isEmpty()
        assertThat(documents.persisted).isEmpty()
        assertThat(status().driveUri).isNull()

        rule.onNodeWithTag("drive-replace").performClick()

        rule.waitFor(hasText("Copia guardada en Google Drive."))
        assertThat(picker.inputs.single()).isEqualTo("Recetario-copia-automatica.zip")
        assertThat(documents.persisted).containsExactly(driveUri)
        assertThat(status().driveUri).isEqualTo(driveUri)
        val local = java.io.File(container.backupManager.localBackupsDir, "Recetario-2026-11-13-2140.zip")
        assertThat(documents.files.getValue(driveUri).toList()).isEqualTo(local.readBytes().toList())
    }

    @Test
    fun drive_existingCopy_importFirst_opensTheImport_andSavesNothing() {
        saveRecipe()
        val existing = backupWithPaella()
        documents.files[driveUri] = existing
        launch(FakePicker(Uri.parse(driveUri)))

        tap("drive-setup")
        rule.waitFor(hasTestTag("drive-import-first"))
        rule.onNodeWithTag("drive-import-first").performClick()

        rule.waitFor(hasTestTag("import-mode-panel"))
        rule.onNodeWithTag("drive-replace-panel").assertDoesNotExist()
        assertThat(status().driveUri).isNull()
        assertThat(documents.persisted).isEmpty()
        assertThat(documents.files.getValue(driveUri).toList()).isEqualTo(existing.toList())

        // Y la importación es la de siempre, desde ese archivo.
        rule.onNodeWithTag("import-merge").performClick()
        rule.waitFor(hasText("Copia importada: 1 receta, 0 fotos."))
        assertThat(recipeIds()).containsExactly("copia", "local")
        assertThat(documents.files.getValue(driveUri).toList()).isEqualTo(existing.toList())
    }

    @Test
    fun drive_existingFileThatIsNotACopy_asks_andCancelLeavesItAsItWas() {
        saveRecipe()
        documents.files[driveUri] = "mis notas".toByteArray()
        launch(FakePicker(Uri.parse(driveUri)))

        tap("drive-setup")

        rule.waitFor(hasText("Ese archivo no es una copia de la app. ¿Reemplazarlo?"))
        rule.onNodeWithTag("drive-import-first").assertDoesNotExist()
        rule.onNodeWithTag("drive-cancel").performClick()
        rule.waitForGone(hasTestTag("drive-replace-panel"))

        assertThat(String(documents.files.getValue(driveUri))).isEqualTo("mis notas")
        assertThat(documents.deleted).isEmpty()
        assertThat(documents.openedForWriting).isEmpty()
        assertThat(documents.persisted).isEmpty()
        assertThat(status().driveUri).isNull()
        rule.onNodeWithTag("drive-setup").assertExists()
    }

    @Test
    fun drive_fileWithoutPersistentPermission_isDeleted_andNothingIsSaved() {
        saveRecipe()
        documents.grantPersistable = false
        launch(FakePicker(Uri.parse(driveUri)))

        tap("drive-setup")

        rule.waitFor(hasText("No se puede usar ese archivo para las copias automáticas. Elige otro."))
        assertThat(documents.deleted).containsExactly(driveUri)
        assertThat(documents.openedForWriting).isEmpty()
        assertThat(status().driveUri).isNull()
    }

    @Test
    fun settings_backupRow_opensTheBackupScreen() {
        lateinit var nav: NavHostController
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container, navController = nav) }
        }
        rule.runOnIdle { nav.navigate(Settings) }
        rule.waitFor(hasTestTag("settings-backup"))
        rule.onNodeWithTag("settings-backup").performClick()
        rule.waitFor(hasTestTag("backup-screen"))
    }

    @Test
    fun replaceColors_reachContrastInEveryTheme() {
        val colors = mutableMapOf<ThemeChoice, Triple<Color, Color, Color>>()
        rule.setContent {
            for (choice in ThemeChoice.entries) {
                RecetarioTheme(choice) {
                    val scheme = MaterialTheme.colorScheme
                    colors[choice] = Triple(scheme.error, scheme.onError, LocalRecetarioColors.current.surface)
                }
            }
        }
        rule.waitForIdle()
        for ((choice, c) in colors) {
            val (error, onError, surface) = c
            assertWithMessage("$choice: rojo sobre el panel").that(contrastRatio(error, surface)).isAtLeast(4.5)
            assertWithMessage("$choice: texto sobre el botón rojo").that(contrastRatio(onError, error)).isAtLeast(4.5)
        }
        assertThat(colors.keys).containsExactlyElementsIn(ThemeChoice.entries)
    }
}
