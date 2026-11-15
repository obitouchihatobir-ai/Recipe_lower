package app.recetario.backup

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
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

    private lateinit var env: BackupTestEnv
    private val created = "content://descargas/antes-de-importar-2026-11-13-2140.zip"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        env = BackupTestEnv(tmp)
    }

    @After
    fun tearDown() {
        env.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle) = BackupViewModel(env.manager, env.settings, env.clock, env.documents, handle)

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
        val newDrive = "content://drive/Recetario-copia-automatica.zip"
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

        val newDrive = "content://drive/Recetario-copia-automatica.zip"
        vm.onDriveDocumentCreated(newDrive)

        eventually("borrar el documento vacío") { newDrive in env.documents.deleted }
        gate.countDown()
        assertThat(env.documents.persisted).isEmpty()
        assertThat(env.documents.openedForWriting).isEmpty()
        assertThat(runBlocking { env.settings.backupStatus.first().driveUri }).isNull()
    }
}
