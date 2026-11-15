package app.recetario.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupRotationTest {
    private val weekly = listOf(
        "Recetario-2026-10-11-0300.zip",
        "Recetario-2026-10-18-0300.zip",
        "Recetario-2026-10-25-0300.zip",
        "Recetario-2026-11-01-0300.zip",
        "Recetario-2026-11-08-0300.zip",
        "Recetario-2026-11-15-0300.zip",
    )

    @Test
    fun rotation_keepsNewest4() {
        assertThat(filesToDelete(weekly.shuffled(), keep = 4))
            .containsExactly("Recetario-2026-10-11-0300.zip", "Recetario-2026-10-18-0300.zip")
        // 4 es lo de serie para las copias semanales.
        assertThat(filesToDelete(weekly)).containsExactly("Recetario-2026-10-11-0300.zip", "Recetario-2026-10-18-0300.zip")
    }

    @Test
    fun rotation_withFewCopies_deletesNothing() {
        assertThat(filesToDelete(weekly.take(4))).isEmpty()
        assertThat(filesToDelete(emptyList())).isEmpty()
    }

    @Test
    fun rotation_neverMixesKinds_norTouchesUnknownNames() {
        val safety = listOf(
            "antes-de-importar-2026-09-01-1000.zip",
            "antes-de-importar-2026-09-02-1000.zip",
            "antes-de-importar-2026-09-03-1000.zip",
            "antes-de-importar-2026-09-04-1000.zip",
        )
        val others = listOf("otra.zip", "Recetario-2026-11-13.zip", "Recetario-2026-11-15-0300.zip.tmp", "a-mano.zip")
        val all = weekly + safety + others

        // Las semanales: 4, aunque haya copias previas más antiguas.
        assertThat(filesToDelete(all, keep = 4, kind = LocalBackupKind.AUTOMATIC))
            .containsExactly("Recetario-2026-10-11-0300.zip", "Recetario-2026-10-18-0300.zip")
        // Las previas a importar: 3, aunque haya semanales más recientes.
        assertThat(filesToDelete(all, keep = SAFETY_COPIES_KEPT, kind = LocalBackupKind.BEFORE_IMPORT))
            .containsExactly("antes-de-importar-2026-09-01-1000.zip")
        assertThat(SAFETY_COPIES_KEPT).isEqualTo(3)
        assertThat(WEEKLY_BACKUPS_KEPT).isEqualTo(4)
    }

    @Test
    fun rotation_usesTheSequenceWithinTheSameMinute() {
        val names = listOf(
            "Recetario-2026-11-15-0300.zip",
            "Recetario-2026-11-15-0300-2.zip",
            "Recetario-2026-11-15-0300-3.zip",
        )
        assertThat(filesToDelete(names, keep = 2)).containsExactly("Recetario-2026-11-15-0300.zip")
    }

    @Test
    fun rotation_neverDeletesAProtectedFile_evenIfItIsTheOldest() {
        // El reloj del teléfono iba adelantado: la copia que se acaba de escribir parece la más antigua.
        val justWritten = "Recetario-2026-09-01-0300.zip"
        val deleted = filesToDelete(weekly + justWritten, protect = setOf(justWritten))
        assertThat(deleted).doesNotContain(justWritten)
        assertThat(deleted).containsExactly("Recetario-2026-10-11-0300.zip", "Recetario-2026-10-18-0300.zip")

        // La copia que se está restaurando tampoco se borra.
        val restoring = "antes-de-importar-2026-09-01-1000.zip"
        val safety = listOf(
            restoring,
            "antes-de-importar-2026-09-02-1000.zip",
            "antes-de-importar-2026-09-03-1000.zip",
            "antes-de-importar-2026-09-04-1000.zip",
        )
        assertThat(filesToDelete(safety, keep = 3, kind = LocalBackupKind.BEFORE_IMPORT, protect = setOf(restoring))).isEmpty()
    }

    @Test
    fun weeklyFileName_isTheAutomaticKind() {
        val name = weeklyBackupFileName(java.time.LocalDateTime.of(2026, 11, 15, 3, 5, 59))
        assertThat(name).isEqualTo("Recetario-2026-11-15-0305.zip")
        assertThat(parseLocalBackupName(name)?.kind).isEqualTo(LocalBackupKind.AUTOMATIC)
    }
}
