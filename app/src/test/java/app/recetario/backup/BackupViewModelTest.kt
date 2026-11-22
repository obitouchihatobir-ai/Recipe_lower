package app.recetario.backup

import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.MainDispatcherRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** «Guardar fuera» recuerda qué copia guardar aunque Android cierre la app mientras está abierto el selector. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Se cierra al final: después de desechar los ViewModel (`main`). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { env.close() }

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    private lateinit var env: BackupTestEnv
    private val created = "content://descargas/antes-de-importar-2026-11-13-2140.zip"

    @Before
    fun setUp() {
        env = BackupTestEnv(tmp)
    }

    private fun viewModel(handle: SavedStateHandle) = main.track(BackupViewModel(env.manager, env.settings, env.clock, env.documents, handle))

    private fun BackupViewModel.nextMessage(): String = runBlocking { withTimeout(10_000) { messages.first() } }

    @Test
    fun saveOutside_withoutAPendingCopy_deletesTheCreatedDocument_andSaysSo() {
        val vm = viewModel(SavedStateHandle())

        vm.saveOutside(created)

        assertThat(vm.nextMessage()).isEqualTo("No se pudo guardar la copia. Inténtalo de nuevo.")
        assertThat(env.documents.deleted).containsExactly(created)
        assertThat(env.documents.openedForWriting).isEmpty()
    }

    @Test
    fun saveOutside_thePendingCopySurvivesTheProcessBeingKilled() {
        runBlocking {
            env.saveAt(sampleRecipe("local", "Tortilla"), backupClock.instant())
            env.import(backupZip(backupOf(sampleRecipe("copia", "Paella", updatedAt = 900))), ImportMode.REPLACE_ALL)
        }
        val name = "antes-de-importar-2026-11-13-2140.zip"
        val handle = SavedStateHandle()
        viewModel(handle).prepareSaveOutside(name)

        // Android cerró la app con el selector abierto: un ViewModel nuevo con el estado que se guardó.
        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        restored.saveOutside(created)

        assertThat(restored.nextMessage()).isEqualTo("Copia guardada.")
        assertThat(env.documents.files.getValue(created).toList()).isEqualTo(File(env.backupsDir, name).readBytes().toList())
        assertThat(env.documents.deleted).isEmpty()
    }

    private fun eventually(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "No llegó a pasar: $what" }
            Thread.sleep(20)
        }
    }

    @Test
    fun driveFile_whenItCannotBeSaved_releasesThePermission_andDeletesTheEmptyDocument() {
        val vm = viewModel(SavedStateHandle())
        val newDrive = "content://drive/Sazon-copia-automatica.zip"
        env.localState.failWrites = true

        vm.onDriveDocumentCreated(newDrive)

        assertThat(vm.nextMessage()).isEqualTo("No se pudo guardar la copia.")
        assertThat(env.documents.deleted).containsExactly(newDrive)
        assertThat(env.documents.persisted).isEmpty()
        assertThat(env.documents.released).contains(newDrive)
        assertThat(env.documents.openedForWriting).isEmpty()
    }

    @Test
    fun driveFile_arrivingWhileBusy_deletesTheEmptyDocument_andChangesNothing() {
        val vm = viewModel(SavedStateHandle())
        // Una comprobación en curso (el archivo que se importa tarda en abrirse).
        val importing = "content://descargas/copia.zip"
        val gate = java.util.concurrent.CountDownLatch(1)
        env.documents.inputGates[importing] = gate
        vm.onImportFilePicked(importing)

        val newDrive = "content://drive/Sazon-copia-automatica.zip"
        vm.onDriveDocumentCreated(newDrive)

        eventually("borrar el documento vacío") { newDrive in env.documents.deleted }
        gate.countDown()
        assertThat(env.documents.persisted).isEmpty()
        assertThat(env.documents.openedForWriting).isEmpty()
        assertThat(runBlocking { env.settings.backupStatus.first().driveUri }).isNull()
    }

    private val drive = "content://drive/Sazon-copia-automatica.zip"

    private fun BackupViewModel.awaitDrivePanel(): DrivePanel =
        runBlocking { withTimeout(10_000) { state.first { it.drivePanel != null }.drivePanel!! } }

    /** El archivo de Drive sigue como estaba ([bytes], o sin contenido propio si es null) y no se usa para nada. */
    private fun assertDriveFileUntouched(vm: BackupViewModel, bytes: ByteArray?) {
        // Ningún aviso: ni «Copia guardada en Google Drive.» ni un error de escritura.
        assertThat(runBlocking { withTimeoutOrNull(1_500) { vm.messages.first() } }).isNull()
        if (bytes == null) assertThat(env.documents.files).doesNotContainKey(drive)
        else assertThat(env.documents.files.getValue(drive).toList()).isEqualTo(bytes.toList())
        assertThat(env.documents.openedForWriting).isEmpty()
        assertThat(env.documents.deleted).isEmpty()
        assertThat(env.documents.persisted).isEmpty()
        assertThat(runBlocking { env.settings.backupStatus.first().driveUri }).isNull()
    }

    @Test
    fun driveFile_withACopyFromANewerVersion_isNeverOfferedForReplacing_andStaysAsItWas() {
        runBlocking { env.saveAt(sampleRecipe("local", "Tortilla"), backupClock.instant()) }
        val newer = zipOf(BACKUP_JSON_ENTRY to """{"schemaVersion":99,"appVersion":"9.0.0"}""".toByteArray())
        env.documents.files[drive] = newer
        val vm = viewModel(SavedStateHandle())

        vm.onDriveDocumentCreated(drive)
        // Se dice el error de verdad, no «no es una copia de Sazón».
        assertThat(vm.awaitDrivePanel()).isEqualTo(DrivePanel.Unusable(drive, BackupMessages.NEWER_BACKUP))
        // Aunque llegara un «Reemplazar» (no se ofrece), no se toca.
        vm.confirmDriveReplace()

        assertDriveFileUntouched(vm, newer)
    }

    @Test
    fun driveFile_thatCannotBeRead_isNeverOfferedForReplacing_andStaysAsItWas() {
        runBlocking { env.saveAt(sampleRecipe("local", "Tortilla"), backupClock.instant()) }
        // Drive sin conexión: el archivo existe, pero no se puede leer ahora.
        env.documents.unreadable += drive
        val vm = viewModel(SavedStateHandle())

        vm.onDriveDocumentCreated(drive)
        // Se dice el error de verdad, no «no es una copia de Sazón».
        assertThat(vm.awaitDrivePanel()).isEqualTo(DrivePanel.Unusable(drive, BackupMessages.OPEN_FAILED))
        vm.confirmDriveReplace()

        assertDriveFileUntouched(vm, null)
    }

    @Test
    fun driveFile_withAnIncompleteCopy_isNeverOfferedForReplacing_andStaysAsItWas() {
        runBlocking { env.saveAt(sampleRecipe("local", "Tortilla"), backupClock.instant()) }
        // El manifiesto nombra una foto que no está en el zip.
        val incomplete = backupZip(backupOf(sampleRecipe("copia", "Paella").copy(coverPhoto = "a.jpg")).copy(images = listOf("a.jpg")))
        env.documents.files[drive] = incomplete
        val vm = viewModel(SavedStateHandle())

        vm.onDriveDocumentCreated(drive)
        // Se dice el error de verdad, no «no es una copia de Sazón».
        assertThat(vm.awaitDrivePanel()).isEqualTo(DrivePanel.Unusable(drive, BackupMessages.INCOMPLETE))
        vm.confirmDriveReplace()

        assertDriveFileUntouched(vm, incomplete)
    }

    /** Deja en el teléfono una copia previa a importar y devuelve su nombre. */
    private fun localCopy(): String {
        runBlocking {
            env.saveAt(sampleRecipe("local", "Tortilla"), backupClock.instant())
            env.import(backupZip(backupOf(sampleRecipe("copia", "Paella", updatedAt = 900))), ImportMode.REPLACE_ALL)
        }
        return "antes-de-importar-2026-11-13-2140.zip"
    }

    @Test
    fun saveOutside_whenAnExistingFileCannotBeOpened_leavesItAsItWas() {
        val name = localCopy()
        val vm = viewModel(SavedStateHandle())
        // El usuario eligió un archivo que ya existía y el proveedor no deja abrirlo para escribir.
        val old = "una copia anterior".toByteArray()
        env.documents.files[created] = old
        env.documents.failOpenOutput = true
        vm.prepareSaveOutside(name)

        vm.saveOutside(created)

        assertThat(vm.nextMessage()).isEqualTo("No se pudo guardar la copia.")
        assertThat(env.documents.deleted).isEmpty()
        assertThat(env.documents.files.getValue(created).toList()).isEqualTo(old.toList())
    }

    @Test
    fun saveOutside_whenTheNewEmptyDocumentCannotBeOpened_deletesIt() {
        val name = localCopy()
        val vm = viewModel(SavedStateHandle())
        env.documents.files[created] = ByteArray(0)
        env.documents.failOpenOutput = true
        vm.prepareSaveOutside(name)

        vm.saveOutside(created)

        assertThat(vm.nextMessage()).isEqualTo("No se pudo guardar la copia.")
        assertThat(env.documents.deleted).containsExactly(created)
    }

    @Test
    fun saveOutside_whenWritingFailsAfterOpening_deletesTheHalfWrittenDocument() {
        val name = localCopy()
        val vm = viewModel(SavedStateHandle())
        env.documents.failOutput = true
        vm.prepareSaveOutside(name)

        vm.saveOutside(created)

        assertThat(vm.nextMessage()).isEqualTo("No se pudo guardar la copia.")
        assertThat(env.documents.deleted).containsExactly(created)
    }
}
