package app.recetario.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalTime

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: SettingsRepository
    private lateinit var store: DataStore<Preferences>
    private lateinit var localStore: DataStore<Preferences>

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("ajustes.preferences_pb").also { it.delete() } }
        localStore = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("estado-local.preferences_pb").also { it.delete() } }
        repo = SettingsRepository(store, localStore)
    }

    private suspend fun keysOf(dataStore: DataStore<Preferences>): Set<String> = dataStore.data.first().asMap().keys.map { it.name }.toSet()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaults_autoTrue_dayCalido_cup240() = runTest {
        assertEquals(ThemeSettings(), repo.themeSettings.first())
        val settings = repo.themeSettings.first()
        assertEquals(true, settings.auto)
        assertEquals(ThemeChoice.CALIDO, settings.dayTheme)
        assertEquals(240, repo.cupMl.first())
        assertEquals(BackupStatus(), repo.backupStatus.first())
    }

    @Test
    fun corruptFile_fallsBackToDefaults() = runTest {
        val file = folder.newFile("corrupto.preferences_pb")
        file.writeBytes(ByteArray(64) { (it * 31 + 7).toByte() })
        val corruptScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val corrupt = SettingsRepository(
                createSettingsDataStore(corruptScope) { file },
                createSettingsDataStore(corruptScope) { folder.newFile("estado-local-limpio.preferences_pb").also { it.delete() } },
            )
            assertEquals(ThemeSettings(), corrupt.themeSettings.first())
            assertEquals(240, corrupt.cupMl.first())
            assertEquals(BackupStatus(), corrupt.backupStatus.first())
            // Y se puede volver a guardar.
            corrupt.setCupMl(250)
            assertEquals(250, corrupt.cupMl.first())
        } finally {
            corruptScope.cancel()
        }
    }

    @Test
    fun notificationPermissionAsked_startsFalse_andStaysTrueOnceSet() = runTest {
        assertEquals(false, repo.notificationPermissionAsked.first())
        repo.setNotificationPermissionAsked()
        assertEquals(true, repo.notificationPermissionAsked.first())
        // Es del teléfono: va en «estado-local», que no viaja con la copia de Android.
        assertEquals(setOf("notifications_permission_asked"), keysOf(localStore))
        assertEquals(emptySet<String>(), keysOf(store))
    }

    @Test
    fun backupStatus_deviceStateLivesInEstadoLocal_onlyLastExternalInAjustes() = runTest {
        val at = Instant.ofEpochMilli(1_760_000_000_000)
        repo.updateBackupStatus {
            BackupStatus(
                lastLocal = at, lastExternal = at, driveUri = "content://x/y", lastError = "Drive", lastDrive = at,
                localError = "Teléfono", noticeDismissed = "2026-11-15|recordatorio",
            )
        }
        assertEquals(
            BackupStatus(at, at, "content://x/y", "Drive", at, "Teléfono", "2026-11-15|recordatorio"),
            repo.backupStatus.first(),
        )
        assertEquals(setOf("backup_last_external"), keysOf(store))
        assertEquals(
            setOf(
                "backup_last_local", "backup_drive_uri", "backup_last_error", "backup_last_drive", "backup_local_error",
                "backup_notice_dismissed",
            ),
            keysOf(localStore),
        )
        // Y se borran.
        repo.updateBackupStatus { BackupStatus() }
        assertEquals(BackupStatus(), repo.backupStatus.first())
        assertEquals(emptySet<String>(), keysOf(store) + keysOf(localStore))
    }

    @Test
    fun corruptLocalState_fallsBackToEmpty_andCanBeWrittenAgain() = runTest {
        val file = folder.newFile("estado-local-corrupto.preferences_pb")
        file.writeBytes(ByteArray(64) { (it * 17 + 3).toByte() })
        val corruptScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val corrupt = SettingsRepository(
                createSettingsDataStore(corruptScope) { folder.newFile("ajustes-limpio.preferences_pb").also { it.delete() } },
                createSettingsDataStore(corruptScope) { file },
            )
            assertEquals(BackupStatus(), corrupt.backupStatus.first())
            assertEquals(false, corrupt.notificationPermissionAsked.first())
            corrupt.updateBackupStatus { it.copy(localError = "Teléfono") }
            assertEquals("Teléfono", corrupt.backupStatus.first().localError)
        } finally {
            corruptScope.cancel()
        }
    }

    @Test
    fun setTheme_persists() = runTest {
        repo.setTheme(ThemeChoice.NOCTURNO)
        repo.setAuto(false)
        val settings = repo.themeSettings.first()
        assertEquals(ThemeChoice.NOCTURNO, settings.choice)
        assertEquals(false, settings.auto)
    }

    @Test
    fun setDayTheme_nocturno_isRejected() = runTest {
        assertTrue(runCatching { repo.setDayTheme(ThemeChoice.NOCTURNO) }.exceptionOrNull() is IllegalArgumentException)
        repo.setDayTheme(ThemeChoice.FRESCO)
        assertEquals(ThemeChoice.FRESCO, repo.themeSettings.first().dayTheme)
    }

    @Test
    fun setNightWindow_persists() = runTest {
        repo.setNightWindow(LocalTime.of(21, 30), LocalTime.of(6, 0))
        val settings = repo.themeSettings.first()
        assertEquals(LocalTime.of(21, 30), settings.nightStart)
        assertEquals(LocalTime.of(6, 0), settings.nightEnd)
    }

    @Test
    fun cupMl_acceptsOnly240Or250() = runTest {
        repo.setCupMl(250)
        assertEquals(250, repo.cupMl.first())
        assertTrue(runCatching { repo.setCupMl(300) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(250, repo.cupMl.first())
    }

    @Test
    fun backupStatus_roundTripsAndClearsFields() = runTest {
        val at = Instant.ofEpochMilli(1_760_000_000_000)
        repo.updateBackupStatus { it.copy(lastLocal = at, driveUri = "content://x/y", lastError = "Sin permiso") }
        assertEquals(BackupStatus(lastLocal = at, driveUri = "content://x/y", lastError = "Sin permiso"), repo.backupStatus.first())
        repo.updateBackupStatus { it.copy(lastError = null, lastExternal = at) }
        assertEquals(BackupStatus(lastLocal = at, lastExternal = at, driveUri = "content://x/y"), repo.backupStatus.first())
        // La última copia en Drive se guarda aparte de la última que salió del teléfono (que puede ser manual).
        val later = at.plusSeconds(60)
        repo.updateBackupStatus { it.copy(lastDrive = later) }
        assertEquals(BackupStatus(lastLocal = at, lastExternal = at, lastDrive = later, driveUri = "content://x/y"), repo.backupStatus.first())
        repo.updateBackupStatus { it.copy(lastDrive = null) }
        assertEquals(null, repo.backupStatus.first().lastDrive)
    }
}
