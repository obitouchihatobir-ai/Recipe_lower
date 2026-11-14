package app.recetario.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

    @Before
    fun setUp() {
        val store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("ajustes.preferences_pb").also { it.delete() } }
        repo = SettingsRepository(store)
    }

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
            val corrupt = SettingsRepository(createSettingsDataStore(corruptScope) { file })
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
    }
}
