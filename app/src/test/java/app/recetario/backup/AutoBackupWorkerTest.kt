package app.recetario.backup

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.recetario.AppContainer
import app.recetario.settings.BackupStatus
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.testClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Proveedor de documentos de prueba que hace de Google Drive: guarda en [dir] y anota el modo de cada apertura. */
class FakeDriveProvider : ContentProvider() {
    lateinit var dir: File
    val modes: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var fail = false

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        modes += mode
        if (fail) throw FileNotFoundException("Drive no responde")
        return ParcelFileDescriptor.open(File(dir, uri.lastPathSegment!!), ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/zip"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0
}

/** La copia semanal en segundo plano: primero el teléfono (comprobada) y, si hay archivo de Drive, Drive. */
@RunWith(AndroidJUnit4::class)
class AutoBackupWorkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var container: AppContainer
    private lateinit var drive: FakeDriveProvider
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val driveUri = "content://$AUTHORITY/Recetario-copia-automatica.zip"
    private val t0 = Instant.parse("2026-10-01T08:00:00Z")

    @Before
    fun setUp() {
        container = installTestContainer()
        drive = Robolectric.setupContentProvider(FakeDriveProvider::class.java, AUTHORITY)
        drive.dir = tmp.newFolder("drive")
    }

    @After
    fun tearDown() = container.database.close()

    private fun seed() = runBlocking {
        container.recipeRepository.save(sampleRecipe("tortilla", "Tortilla"))
        container.recipeRepository.save(sampleRecipe("gazpacho", "Gazpacho"))
    }

    private fun setStatus(transform: (BackupStatus) -> BackupStatus) = runBlocking { container.settingsRepository.updateBackupStatus(transform) }

    private fun status() = runBlocking { container.settingsRepository.backupStatus.first() }

    private fun runWorker(): ListenableWorker.Result =
        runBlocking { TestListenableWorkerBuilder<AutoBackupWorker>(context).build().doWork() }

    private fun localCopies() = container.backupManager.localBackupsDir.list()?.sorted().orEmpty()

    private fun driveFile() = File(drive.dir, "Recetario-copia-automatica.zip")

    @Test
    fun worker_writesVerifiedLocal_andCopiesToDrive() {
        seed()
        setStatus { it.copy(driveUri = driveUri, lastError = BackupMessages.DRIVE_FAILED) }
        // Lo que había en Drive (la copia de la semana pasada) se sustituye entero.
        driveFile().writeBytes(ByteArray(200_000) { 7 })

        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())

        assertThat(localCopies()).containsExactly("Recetario-2026-11-13-2140.zip")
        val local = File(container.backupManager.localBackupsDir, "Recetario-2026-11-13-2140.zip")
        assertThat(runBlocking { container.backupManager.localBackups() })
            .containsExactly(LocalBackup(local.name, recipes = 2))
        assertThat(drive.modes).containsExactly("wt")
        assertThat(driveFile().readBytes().toList()).isEqualTo(local.readBytes().toList())
        val now = testClock.instant()
        assertThat(status()).isEqualTo(BackupStatus(lastLocal = now, lastExternal = now, lastDrive = now, driveUri = driveUri))
    }

    @Test
    fun worker_driveFails_recordsError_keepsLocal() {
        seed()
        setStatus { it.copy(driveUri = driveUri, lastExternal = t0, lastDrive = t0) }
        drive.fail = true

        // Drive no se reintenta en segundo plano: el error se muestra en Inicio y en Copias de seguridad.
        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())

        assertThat(localCopies()).containsExactly("Recetario-2026-11-13-2140.zip")
        assertThat(status()).isEqualTo(
            BackupStatus(
                lastLocal = testClock.instant(), lastExternal = t0, lastDrive = t0, driveUri = driveUri,
                lastError = "No se pudo guardar en Google Drive. Vuelve a elegir el archivo.",
            ),
        )
    }

    @Test
    fun worker_withoutDrive_onlyWritesTheLocalCopy() {
        seed()

        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())

        assertThat(localCopies()).containsExactly("Recetario-2026-11-13-2140.zip")
        assertThat(drive.modes).isEmpty()
        assertThat(status()).isEqualTo(BackupStatus(lastLocal = testClock.instant()))
    }

    @Test
    fun worker_withNoRecipes_writesNothing_andSucceeds() {
        setStatus { it.copy(driveUri = driveUri) }

        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())

        assertThat(localCopies()).isEmpty()
        assertThat(drive.modes).isEmpty()
        assertThat(status()).isEqualTo(BackupStatus(driveUri = driveUri))
    }

    @Test
    fun worker_localFailure_retries_andNeverTouchesDrive() {
        seed()
        setStatus { it.copy(driveUri = driveUri) }
        container.backupManager.localBackupsDir.apply { parentFile?.mkdirs() }.writeText("no es una carpeta")

        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.retry())

        assertThat(drive.modes).isEmpty()
        assertThat(status()).isEqualTo(BackupStatus(driveUri = driveUri))
    }

    @SuppressLint("RestrictedApi")
    @Test
    fun schedule_isAUniqueWeeklyWork_keptIfAlreadyScheduled() {
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(NoOpWorkerFactory())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        try {
            val workManager = WorkManager.getInstance(context)
            scheduleWeeklyBackup(workManager)
            val first = workManager.getWorkInfosForUniqueWork("copia-semanal").get().single()
            assertThat(first.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.DAYS.toMillis(7))

            // KEEP: volver a programarla (cada vez que arranca la app) no la reinicia.
            scheduleWeeklyBackup(workManager)
            assertThat(workManager.getWorkInfosForUniqueWork("copia-semanal").get().single().id).isEqualTo(first.id)
            assertThat(weeklyBackupRequest().workSpec.workerClassName).isEqualTo(AutoBackupWorker::class.java.name)
        } finally {
            WorkManagerTestInitHelper.closeWorkDatabase()
            // Las demás pruebas arrancan sin WorkManager, como siempre en Robolectric.
            WorkManagerImpl.setDelegate(null)
        }
    }

    /** En la prueba de la programación no se ejecuta la copia de verdad. */
    private class NoOpWorkerFactory : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            object : Worker(appContext, workerParameters) {
                override fun doWork() = Result.success()
            }
    }

    private companion object {
        const val AUTHORITY = "app.recetario.prueba.drive"
    }
}
