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
}
