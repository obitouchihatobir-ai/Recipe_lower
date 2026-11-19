package app.recetario.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

/**
 * Copia semanal del teléfono (`writeLocalBackup`), su rotación, la copia a Drive (`copyToExternal`), la copia
 * automática completa (`backupNow`), la rotación de las copias previas a importar y la exportación comprobada.
 */
@RunWith(AndroidJUnit4::class)
class WeeklyBackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var env: BackupTestEnv
    private val drive = "content://drive/Sazon-copia-automatica.zip"
    private val t0 = Instant.parse("2026-10-01T08:00:00Z")

    @Before
    fun setUp() {
        env = BackupTestEnv(tmp)
    }

    @After
    fun tearDown() = env.close()

    private suspend fun seed() {
        env.writePhoto("tortilla.jpg", fakePhoto(1))
        env.saveAt(sampleRecipe("tortilla", "Tortilla").copy(coverPhoto = "tortilla.jpg"), t0)
        env.saveAt(sampleRecipe("gazpacho", "Gazpacho"), t0)
    }

    private suspend fun status() = env.settings.backupStatus.first()

    /** Una copia de verdad (se puede restaurar) con el nombre dado. */
    private suspend fun validCopy(name: String) {
        env.backupsDir.mkdirs()
        File(env.backupsDir, name).writeBytes(env.exportBytes())
    }

    @Test
    fun writeLocalBackup_writesAVerifiedCopy_andRecordsLastLocal() = runBlocking<Unit> {
        seed()

        val file = env.manager.writeLocalBackup()

        assertThat(file?.name).isEqualTo("Sazon-2026-11-13-2140.zip")
        assertThat(env.backups()).containsExactly("Sazon-2026-11-13-2140.zip")
        assertThat(env.manager.localBackups()).containsExactly(LocalBackup("Sazon-2026-11-13-2140.zip", recipes = 2))
        val entries = unzip(file!!.readBytes())
        assertThat(entries.keys).containsExactly("backup.json", "images/tortilla.jpg")
        assertThat(status().lastLocal).isEqualTo(env.clock.instant())
        // Ni restos del temporal ni cambios en lo que sale del teléfono.
        assertThat(status().lastExternal).isNull()
        assertThat(env.documents.openedForWriting).isEmpty()
    }

    @Test
    fun writeLocalBackup_twiceInTheSameMinute_neverOverwrites_andKeepsOnlyTheNewestOfTheWeek() = runBlocking<Unit> {
        seed()
        val first = env.manager.writeLocalBackup()!!
        assertThat(first.name).isEqualTo("Sazon-2026-11-13-2140.zip")

        val second = env.manager.writeLocalBackup()

        // Nunca pisa la anterior (otro nombre); después, la de esta semana sustituye a la anterior de la semana.
        assertThat(second?.name).isEqualTo("Sazon-2026-11-13-2140-2.zip")
        assertThat(env.backups()).containsExactly("Sazon-2026-11-13-2140-2.zip")
        assertThat(env.manager.localBackups()).containsExactly(LocalBackup("Sazon-2026-11-13-2140-2.zip", recipes = 2))
    }

    @Test
    fun retryingManyTimesInAWeek_keepsTheCopiesOfThePreviousWeeks() = runBlocking<Unit> {
        seed()
        val history = listOf("Sazon-2026-10-25-0300.zip", "Sazon-2026-11-01-0300.zip", "Sazon-2026-11-08-0300.zip")
        history.forEach { validCopy(it) }
        validCopy("Sazon-2026-11-10-0300.zip")

        repeat(4) { assertThat(env.manager.backupNow()).isInstanceOf(AutoBackupResult.Saved::class.java) }

        assertThat(env.backups()).containsExactlyElementsIn(history + "Sazon-2026-11-13-2140-4.zip")
    }

    @Test
    fun writeLocalBackup_keepsTheNewest4Weekly_andNeverTouchesOtherCopies() = runBlocking<Unit> {
        seed()
        val olderWeekly = listOf(
            "Sazon-2026-10-11-0300.zip",
            "Sazon-2026-10-18-0300.zip",
            "Sazon-2026-10-25-0300.zip",
            "Sazon-2026-11-01-0300.zip",
            "Sazon-2026-11-08-0300.zip",
        )
        val safety = listOf(
            "antes-de-importar-2026-09-01-1000.zip",
            "antes-de-importar-2026-09-02-1000.zip",
            "antes-de-importar-2026-09-03-1000.zip",
            "antes-de-importar-2026-09-04-1000.zip",
        )
        (olderWeekly + safety).forEach { validCopy(it) }
        File(env.backupsDir, "otra.zip").writeText("ajena")
        // Resto de una copia que Android cortó a medias: nunca es una copia terminada.
        File(env.backupsDir, "Sazon-2026-11-12-0300.zip.tmp").writeText("a medias")

        val file = env.manager.writeLocalBackup()

        assertThat(file?.name).isEqualTo("Sazon-2026-11-13-2140.zip")
        assertThat(env.backups()).containsExactly(
            "Sazon-2026-10-25-0300.zip",
            "Sazon-2026-11-01-0300.zip",
            "Sazon-2026-11-08-0300.zip",
            "Sazon-2026-11-13-2140.zip",
            *safety.toTypedArray(),
            "otra.zip",
        )
    }

    @Test
    fun writeLocalBackup_neverDeletesTheCopyItJustWrote_evenIfTheClockWentBack() = runBlocking<Unit> {
        seed()
        val future = listOf(
            "Sazon-2027-01-03-0300.zip",
            "Sazon-2027-01-10-0300.zip",
            "Sazon-2027-01-17-0300.zip",
            "Sazon-2027-01-24-0300.zip",
        )
        future.forEach { validCopy(it) }

        val file = env.manager.writeLocalBackup()

        assertThat(file?.exists()).isTrue()
        assertThat(env.backups()).containsExactlyElementsIn(future + "Sazon-2026-11-13-2140.zip")
    }

    @Test
    fun localBackup_isNeverCopiedIfVerificationFails() = runBlocking<Unit> {
        seed()
        validCopy("Sazon-2026-11-08-0300.zip")
        env.settings.updateBackupStatus { it.copy(driveUri = drive, lastExternal = t0) }
        // Una receta sin identificador se exporta, pero la copia no pasaría las comprobaciones de la importación.
        env.saveAt(sampleRecipe(" ", "Sin identificador"), t0)

        assertThat(env.manager.writeLocalBackup()).isNull()
        val result = env.manager.backupNow()

        assertThat(result).isEqualTo(AutoBackupResult.LocalFailed)
        // Nada nuevo en el teléfono (ni el temporal), la copia anterior intacta y Drive sin tocar.
        assertThat(env.backups()).containsExactly("Sazon-2026-11-08-0300.zip")
        assertThat(env.documents.openedForWriting).isEmpty()
        assertThat(status()).isEqualTo(
            app.recetario.settings.BackupStatus(lastExternal = t0, driveUri = drive, localError = "No se pudo hacer la copia semanal en el teléfono."),
        )
    }

    @Test
    fun localFailure_isRecorded_andTheNextSuccessClearsIt() = runBlocking<Unit> {
        seed()
        env.backupsDir.writeText("no es una carpeta")

        assertThat(env.manager.writeLocalBackup()).isNull()
        assertThat(status().localError).isEqualTo(BackupMessages.LOCAL_FAILED)

        env.backupsDir.delete()
        assertThat(env.manager.writeLocalBackup()).isNotNull()
        assertThat(status().localError).isNull()
        assertThat(status().lastLocal).isEqualTo(env.clock.instant())
    }

    @Test
    fun writeLocalBackup_whenTheFolderCannotBeCreated_fails() = runBlocking<Unit> {
        seed()
        env.backupsDir.writeText("no es una carpeta")

        assertThat(env.manager.writeLocalBackup()).isNull()
        assertThat(env.manager.backupNow()).isEqualTo(AutoBackupResult.LocalFailed)
        assertThat(status().lastLocal).isNull()
    }

    @Test
    fun copyToExternal_success_recordsLastExternal_andClearsTheError() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive, lastError = BackupMessages.DRIVE_FAILED) }
        val file = env.manager.writeLocalBackup()!!

        assertThat(env.manager.copyToExternal(file, drive)).isTrue()

        assertThat(env.documents.files.getValue(drive).toList()).isEqualTo(file.readBytes().toList())
        val now = env.clock.instant()
        assertThat(status()).isEqualTo(
            app.recetario.settings.BackupStatus(lastLocal = now, lastExternal = now, lastDrive = now, driveUri = drive),
        )
    }

    @Test
    fun copyToExternal_failure_recordsTheError_keepsTheLocalCopy_andLastExternal() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive, lastExternal = t0, lastDrive = t0) }
        val file = env.manager.writeLocalBackup()!!
        env.documents.failOutput = true

        assertThat(env.manager.copyToExternal(file, drive)).isFalse()

        assertThat(file.exists()).isTrue()
        assertThat(env.manager.localBackups()).containsExactly(LocalBackup(file.name, recipes = 2))
        val s = status()
        assertThat(s.lastError).isEqualTo("No se pudo guardar en Google Drive. Vuelve a elegir el archivo.")
        assertThat(s.lastExternal).isEqualTo(t0)
        assertThat(s.lastDrive).isEqualTo(t0)
        assertThat(s.driveUri).isEqualTo(drive)
    }

    @Test
    fun copyToExternal_checksTheLengthReadBack_aShortDocumentIsAFailure() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive, lastExternal = t0) }
        val file = env.manager.writeLocalBackup()!!
        env.documents.dropLastByte = true

        assertThat(env.manager.copyToExternal(file, drive)).isFalse()

        assertThat(env.documents.opened).contains(drive)
        assertThat(status().lastError).isEqualTo(BackupMessages.DRIVE_FAILED)
        assertThat(status().lastExternal).isEqualTo(t0)
    }

    @Test
    fun copyToExternal_providerWithoutWt_retriesOnceWithW() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive) }
        val file = env.manager.writeLocalBackup()!!
        env.documents.rejectedModes = setOf("wt")

        assertThat(env.manager.copyToExternal(file, drive)).isTrue()

        assertThat(env.documents.modes).containsExactly("wt", "w").inOrder()
        assertThat(env.documents.files.getValue(drive).toList()).isEqualTo(file.readBytes().toList())
        assertThat(status().lastError).isNull()
        assertThat(status().lastExternal).isEqualTo(env.clock.instant())
    }

    @Test
    fun copyToExternal_withoutWt_overALongerOldCopy_doesNotTryW_andLeavesTheOldCopyWhole() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive) }
        val file = env.manager.writeLocalBackup()!!
        // La copia de la semana pasada era más larga: con «w», algunos proveedores dejarían su final detrás de la
        // nueva (un zip que otras herramientas no abren). Mejor no tocarla y avisar.
        val old = ByteArray(file.length().toInt() + 5_000) { 7 }
        env.documents.files[drive] = old
        env.documents.rejectedModes = setOf("wt")
        env.documents.wKeepsOldTail = true

        assertThat(env.manager.copyToExternal(file, drive)).isFalse()

        assertThat(env.documents.modes).containsExactly("wt")
        assertThat(env.documents.files.getValue(drive).toList()).isEqualTo(old.toList())
        assertThat(status().lastError).isEqualTo(BackupMessages.DRIVE_FAILED)
        assertThat(status().lastExternal).isNull()
    }

    @Test
    fun copyToExternal_withoutWt_overAShorterOldCopy_usesW() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive) }
        val file = env.manager.writeLocalBackup()!!
        env.documents.files[drive] = ByteArray(100) { 7 }
        env.documents.rejectedModes = setOf("wt")
        env.documents.wKeepsOldTail = true

        assertThat(env.manager.copyToExternal(file, drive)).isTrue()

        assertThat(env.documents.modes).containsExactly("wt", "w").inOrder()
        assertThat(env.documents.files.getValue(drive).toList()).isEqualTo(file.readBytes().toList())
    }

    @Test
    fun copyToExternal_neverUploadsACopyThatCannotBeRestored() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive) }
        val broken = File(env.backupsDir.apply { mkdirs() }, "Sazon-2026-11-13-2140.zip").apply { writeText("roto") }

        assertThat(env.manager.copyToExternal(broken, drive)).isFalse()

        assertThat(env.documents.openedForWriting).isEmpty()
        assertThat(status().lastError).isEqualTo(BackupMessages.DRIVE_FAILED)
        assertThat(status().lastExternal).isNull()
    }

    @Test
    fun backupNow_writesLocal_thenCopiesToDrive() = runBlocking<Unit> {
        seed()
        env.settings.updateBackupStatus { it.copy(driveUri = drive) }

        val result = env.manager.backupNow()

        assertThat(result).isInstanceOf(AutoBackupResult.Saved::class.java)
        result as AutoBackupResult.Saved
        assertThat(result.drive).isTrue()
        assertThat(result.file.name).isEqualTo("Sazon-2026-11-13-2140.zip")
        assertThat(env.documents.files.getValue(drive).toList()).isEqualTo(result.file.readBytes().toList())
        assertThat(status().lastExternal).isEqualTo(env.clock.instant())
    }

    @Test
    fun backupNow_withoutDrive_onlyWritesTheLocalCopy() = runBlocking<Unit> {
        seed()

        val result = env.manager.backupNow() as AutoBackupResult.Saved

        assertThat(result.drive).isNull()
        assertThat(env.backups()).containsExactly("Sazon-2026-11-13-2140.zip")
        assertThat(status().lastExternal).isNull()
    }

    @Test
    fun backupNow_withNoRecipes_writesNothing() = runBlocking<Unit> {
        env.settings.updateBackupStatus { it.copy(driveUri = drive) }

        assertThat(env.manager.backupNow()).isEqualTo(AutoBackupResult.NothingToSave)

        assertThat(env.backups()).isEmpty()
        assertThat(env.documents.openedForWriting).isEmpty()
        assertThat(status()).isEqualTo(app.recetario.settings.BackupStatus(driveUri = drive))
    }

    @Test
    fun safetyCopies_keepTheNewest3_neverTheWeeklyOnes_andNeverTheOneBeingRestored() = runBlocking<Unit> {
        seed()
        validCopy("Sazon-2026-01-04-0300.zip")
        val paella = backupZip(backupOf(sampleRecipe("copia", "Paella", updatedAt = 900)))
        repeat(3) { assertThat(env.import(paella, ImportMode.REPLACE_ALL)).isInstanceOf(ImportResult.Ok::class.java) }
        val oldest = "antes-de-importar-2026-11-13-2140.zip"
        assertThat(env.backups()).containsExactly(
            oldest, "antes-de-importar-2026-11-13-2140-2.zip", "antes-de-importar-2026-11-13-2140-3.zip",
            "Sazon-2026-01-04-0300.zip",
        )

        // Al restaurar la más antigua se guarda otra previa: la que se restaura no se borra aunque sobre.
        assertThat(env.manager.restoreLocal(oldest)).isEqualTo(ImportResult.Ok(recipes = 2, images = 1))
        assertThat(env.backups()).containsExactly(
            oldest, "antes-de-importar-2026-11-13-2140-2.zip", "antes-de-importar-2026-11-13-2140-3.zip",
            "antes-de-importar-2026-11-13-2140-4.zip", "Sazon-2026-01-04-0300.zip",
        )

        // La siguiente importación ya rota: quedan las 3 más recientes y la semanal.
        assertThat(env.import(paella, ImportMode.REPLACE_ALL)).isInstanceOf(ImportResult.Ok::class.java)
        assertThat(env.backups()).containsExactly(
            "antes-de-importar-2026-11-13-2140-3.zip", "antes-de-importar-2026-11-13-2140-4.zip",
            "antes-de-importar-2026-11-13-2140-5.zip", "Sazon-2026-01-04-0300.zip",
        )
    }

    @Test
    fun export_isVerifiedBeforeAnythingReachesTheDestination() = runBlocking<Unit> {
        seed()
        val out = ByteArrayOutputStream()
        assertThat(env.manager.export(out)).isEqualTo(ExportResult.Ok)
        assertThat(decodeBackup(String(unzip(out.toByteArray()).getValue("backup.json")))).isInstanceOf(BackupDecodeResult.Ok::class.java)
        // El temporal de la exportación no se queda en la caché ni en las copias del teléfono.
        assertThat(env.cacheFiles()).isEmpty()
        assertThat(env.backups()).isEmpty()

        env.saveAt(sampleRecipe(" ", "Sin identificador"), t0)
        val broken = ByteArrayOutputStream()
        assertThat(env.manager.export(broken)).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))
        assertThat(broken.size()).isEqualTo(0)
        assertThat(env.cacheFiles()).isEmpty()
    }

    @Test
    fun exportToDocument_neverOpensTheDocumentIfTheCopyIsBroken_andDeletesItOnlyIfEmpty() = runBlocking<Unit> {
        seed()
        val doc = "content://descargas/Sazon-2026-11-13.zip"
        assertThat(env.manager.exportToDocument(doc)).isEqualTo(ExportResult.Ok)
        assertThat(unzip(env.documents.files.getValue(doc)).keys).contains("backup.json")

        env.saveAt(sampleRecipe(" ", "Sin identificador"), t0)
        // El usuario eligió sobrescribir una copia anterior: si la nueva no vale, la anterior no se toca.
        val previous = env.documents.files.getValue(doc)
        env.documents.openedForWriting.clear()
        assertThat(env.manager.exportToDocument(doc)).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))
        assertThat(env.documents.openedForWriting).isEmpty()
        assertThat(env.documents.files.getValue(doc).toList()).isEqualTo(previous.toList())
        assertThat(env.documents.deleted).isEmpty()

        // Un documento nuevo (vacío) que no llega a escribirse se borra.
        val fresh = "content://descargas/Sazon-2026-11-13 (1).zip"
        assertThat(env.manager.exportToDocument(fresh)).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))
        assertThat(env.documents.deleted).containsExactly(fresh)
    }

    @Test
    fun exportToDocument_ifItCannotBeOpened_neverDeletesAnExistingFile() = runBlocking<Unit> {
        seed()
        val existing = "content://descargas/Sazon-2026-11-01.zip"
        env.documents.files[existing] = "copia anterior".toByteArray()
        env.documents.failOpenOutput = true

        assertThat(env.manager.exportToDocument(existing)).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))

        assertThat(env.documents.deleted).isEmpty()
        assertThat(String(env.documents.files.getValue(existing))).isEqualTo("copia anterior")
        // Uno nuevo (vacío) que no se pudo abrir sí se borra.
        val fresh = "content://descargas/Sazon-2026-11-13.zip"
        assertThat(env.manager.exportToDocument(fresh)).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))
        assertThat(env.documents.deleted).containsExactly(fresh)
        assertThat(env.cacheFiles()).isEmpty()
    }

    @Test
    fun exportToDocument_copyFailure_deletesThePartialDocument() = runBlocking<Unit> {
        seed()
        val doc = "content://descargas/Sazon-2026-11-13.zip"
        env.documents.failOutput = true

        assertThat(env.manager.exportToDocument(doc)).isEqualTo(ExportResult.Error("No se pudo guardar la copia."))
        assertThat(env.documents.deleted).containsExactly(doc)
        assertThat(env.cacheFiles()).isEmpty()
    }
}
