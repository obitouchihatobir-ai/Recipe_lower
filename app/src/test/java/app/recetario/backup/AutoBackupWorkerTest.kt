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

/**
 * Proveedor de documentos de prueba que hace de Google Drive: guarda en [dir] y anota el modo de cada apertura para
 * escribir ([modes]) y cuántas veces se leyó ([reads]). [rejectWt]: no admite «wt»; [wKeepsOldTail]: con «w» no
 * vacía el archivo, como algunos proveedores.
 */
class FakeDriveProvider : ContentProvider() {
    lateinit var dir: File
    val modes: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var reads = 0

    @Volatile
    var fail = false

    @Volatile
    var rejectWt = false

    @Volatile
    var wKeepsOldTail = false

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode == "r") reads++ else modes += mode
        if (fail) throw FileNotFoundException("Drive no responde")
        if (rejectWt && mode == "wt") throw FileNotFoundException("Modo no admitido: wt")
        val flags = if (mode == "w" && wKeepsOldTail) {
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
        } else {
            ParcelFileDescriptor.parseMode(mode)
        }
        return ParcelFileDescriptor.open(File(dir, uri.lastPathSegment!!), flags)
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

    private fun runWorker(attempt: Int = 0): ListenableWorker.Result =
        runBlocking { TestListenableWorkerBuilder<AutoBackupWorker>(context, runAttemptCount = attempt).build().doWork() }

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
        // Después de escribir se lee para comprobar el tamaño.
        assertThat(drive.reads).isAtLeast(1)
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
    fun worker_localFailure_retries_recordsTheError_andNeverTouchesDrive() {
        seed()
        setStatus { it.copy(driveUri = driveUri) }
        container.backupManager.localBackupsDir.apply { parentFile?.mkdirs() }.writeText("no es una carpeta")

        assertThat(runWorker(attempt = 0)).isEqualTo(ListenableWorker.Result.retry())
        assertThat(runWorker(attempt = 2)).isEqualTo(ListenableWorker.Result.retry())

        assertThat(drive.modes).isEmpty()
        assertThat(status()).isEqualTo(BackupStatus(driveUri = driveUri, localError = "No se pudo hacer la copia semanal en el teléfono."))
    }

    @Test
    fun worker_localFailure_afterThreeAttempts_waitsForTheNextWeek() {
        seed()
        container.backupManager.localBackupsDir.apply { parentFile?.mkdirs() }.writeText("no es una carpeta")

        assertThat(runWorker(attempt = 3)).isEqualTo(ListenableWorker.Result.success())

        assertThat(status().localError).isEqualTo(BackupMessages.LOCAL_FAILED)
    }

    @Test
    fun worker_localSuccess_clearsAnOldLocalError() {
        seed()
        setStatus { it.copy(localError = BackupMessages.LOCAL_FAILED) }

        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())

        assertThat(status()).isEqualTo(BackupStatus(lastLocal = testClock.instant()))
    }

    @Test
    fun worker_providerWithoutWt_retriesWithW_andTheLengthCheckCatchesOldBytes() {
        seed()
        setStatus { it.copy(driveUri = driveUri, lastExternal = t0) }
        drive.rejectWt = true
        drive.wKeepsOldTail = true
        // La copia de la semana pasada, más larga que la nueva.
        driveFile().writeBytes(ByteArray(200_000) { 7 })

        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())

        assertThat(drive.modes).containsExactly("wt", "w").inOrder()
        assertThat(status().lastError).isEqualTo(BackupMessages.DRIVE_FAILED)
        assertThat(status().lastExternal).isEqualTo(t0)
        assertThat(localCopies()).containsExactly("Recetario-2026-11-13-2140.zip")
    }

    @Test
    fun worker_providerWithoutWt_andAnEmptyFile_savesWithW() {
        seed()
        setStatus { it.copy(driveUri = driveUri) }
        drive.rejectWt = true
        drive.wKeepsOldTail = true

        assertThat(runWorker()).isEqualTo(ListenableWorker.Result.success())

        assertThat(drive.modes).containsExactly("wt", "w").inOrder()
        val local = File(container.backupManager.localBackupsDir, "Recetario-2026-11-13-2140.zip")
        assertThat(driveFile().readBytes().toList()).isEqualTo(local.readBytes().toList())
        assertThat(status().lastError).isNull()
        assertThat(status().lastExternal).isEqualTo(testClock.instant())
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
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            object : Worker(appContext, workerParameters) {
                override fun doWork(): Result = Result.success()
            }
    }

    private companion object {
        const val AUTHORITY = "app.recetario.prueba.drive"
    }
}
