package app.recetario.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class BackupRotationTest {
    /** Viernes 13 de noviembre de 2026, 21:40 (semana ISO 46, del lunes 9 al domingo 15). */
    private val now = LocalDateTime.of(2026, 11, 13, 21, 40)

    /** Domingos: cada uno en su semana ISO (de la 41 a la 46). */
    private val weekly = listOf(
        "Recetario-2026-10-11-0300.zip",
        "Recetario-2026-10-18-0300.zip",
        "Recetario-2026-10-25-0300.zip",
        "Recetario-2026-11-01-0300.zip",
        "Recetario-2026-11-08-0300.zip",
        "Recetario-2026-11-13-0300.zip",
    )

    @Test
    fun rotation_keepsNewest4Weeks() {
        assertThat(filesToDelete(weekly.shuffled(), now, keep = 4))
            .containsExactly("Recetario-2026-10-11-0300.zip", "Recetario-2026-10-18-0300.zip")
        // 4 es lo de serie para las copias semanales.
        assertThat(filesToDelete(weekly, now)).containsExactly("Recetario-2026-10-11-0300.zip", "Recetario-2026-10-18-0300.zip")
    }

    @Test
    fun rotation_withFewCopies_deletesNothing() {
        assertThat(filesToDelete(weekly.takeLast(4), now)).isEmpty()
        assertThat(filesToDelete(emptyList(), now)).isEmpty()
    }

    @Test
    fun rotation_sameWeek_keepsOnlyTheNewestOfThatWeek() {
        val names = listOf(
            "Recetario-2026-10-25-0300.zip",
            "Recetario-2026-11-09-0300.zip",
            "Recetario-2026-11-13-2140.zip",
            "Recetario-2026-11-13-2140-2.zip",
        )
        assertThat(filesToDelete(names, now))
            .containsExactly("Recetario-2026-11-09-0300.zip", "Recetario-2026-11-13-2140.zip")
    }

    @Test
    fun rotation_tappingRetryFourTimesInAWeek_keepsTheHistoryOfThePreviousWeeks() {
        val history = listOf("Recetario-2026-10-25-0300.zip", "Recetario-2026-11-01-0300.zip", "Recetario-2026-11-08-0300.zip")
        var files = history + "Recetario-2026-11-10-0300.zip"
        for (minute in listOf("2140", "2141", "2142", "2143")) {
            val written = "Recetario-2026-11-13-$minute.zip"
            files = files + written
            files = files - filesToDelete(files, now.withMinute(minute.takeLast(2).toInt()), protect = setOf(written)).toSet()
        }
        assertThat(files).containsExactlyElementsIn(history + "Recetario-2026-11-13-2143.zip")
    }

    @Test
    fun rotation_weeksAreIsoWeeks_startingOnMonday() {
        val names = listOf(
            "Recetario-2026-10-25-0300.zip",
            "Recetario-2026-11-01-0300.zip",
            // Domingo 8 (semana 45) y lunes 9 (semana 46): semanas distintas.
            "Recetario-2026-11-08-2359.zip",
            "Recetario-2026-11-09-0001.zip",
        )
        assertThat(filesToDelete(names, now)).isEmpty()
        // A caballo entre dos años: el 31 de diciembre de 2026 (jueves) es de la semana 53 de 2026, y el
        // 3 de enero de 2027 (domingo) también; el 4 de enero, de la 1 de 2027.
        val newYear = listOf("Recetario-2026-12-31-1000.zip", "Recetario-2027-01-03-1000.zip", "Recetario-2027-01-04-1000.zip")
        assertThat(filesToDelete(newYear, LocalDateTime.of(2027, 1, 4, 12, 0))).containsExactly("Recetario-2026-12-31-1000.zip")
    }

    @Test
    fun rotation_futureNames_areIgnoredAndNeverDeleted() {
        val future = "Recetario-2027-01-03-0300.zip"
        // Dentro de un día no es «futuro»: cuenta y es la más reciente de su semana.
        val tomorrow = "Recetario-2026-11-14-2000.zip"
        val names = listOf(
            "Recetario-2026-10-18-0300.zip",
            "Recetario-2026-10-25-0300.zip",
            "Recetario-2026-11-01-0300.zip",
            "Recetario-2026-11-08-0300.zip",
            "Recetario-2026-11-13-2140.zip",
            tomorrow,
            future,
        )
        val deleted = filesToDelete(names, now)
        assertThat(deleted).containsExactly("Recetario-2026-10-18-0300.zip", "Recetario-2026-11-13-2140.zip")
        assertThat(deleted).doesNotContain(future)
        // Aunque todas fueran «del futuro», no se borra ninguna.
        assertThat(filesToDelete(listOf(future, "Recetario-2027-02-07-0300.zip", "Recetario-2027-03-07-0300.zip"), now, keep = 1)).isEmpty()
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

        // Las semanales: 4 semanas, aunque haya copias previas más antiguas.
        assertThat(filesToDelete(all, now, keep = 4, kind = LocalBackupKind.AUTOMATIC))
            .containsExactly("Recetario-2026-10-11-0300.zip", "Recetario-2026-10-18-0300.zip")
        // Las previas a importar: los 3 archivos más recientes (aunque sean de la misma semana).
        assertThat(filesToDelete(all, now, keep = SAFETY_COPIES_KEPT, kind = LocalBackupKind.BEFORE_IMPORT))
            .containsExactly("antes-de-importar-2026-09-01-1000.zip")
        assertThat(SAFETY_COPIES_KEPT).isEqualTo(3)
        assertThat(WEEKLY_BACKUPS_KEPT).isEqualTo(4)
    }

    @Test
    fun safetyCopies_keepFiles_usingTheSequenceWithinTheSameMinute_andIgnoreFutureNames() {
        val names = listOf(
            "antes-de-importar-2026-11-13-2140.zip",
            "antes-de-importar-2026-11-13-2140-2.zip",
            "antes-de-importar-2026-11-13-2140-3.zip",
            "antes-de-importar-2027-05-01-1000.zip",
        )
        assertThat(filesToDelete(names, now, keep = 2, kind = LocalBackupKind.BEFORE_IMPORT))
            .containsExactly("antes-de-importar-2026-11-13-2140.zip")
    }

    @Test
    fun rotation_neverDeletesAProtectedFile() {
        // La que se acaba de escribir en una semana en la que ya había una más reciente (el reloj retrocedió unas horas).
        val justWritten = "Recetario-2026-11-13-0900.zip"
        val names = listOf("Recetario-2026-11-01-0300.zip", "Recetario-2026-11-08-0300.zip", "Recetario-2026-11-13-2100.zip", justWritten)
        assertThat(filesToDelete(names, now, protect = setOf(justWritten))).isEmpty()

        // La copia que se está restaurando tampoco se borra.
        val restoring = "antes-de-importar-2026-09-01-1000.zip"
        val safety = listOf(
            restoring,
            "antes-de-importar-2026-09-02-1000.zip",
            "antes-de-importar-2026-09-03-1000.zip",
            "antes-de-importar-2026-09-04-1000.zip",
        )
        assertThat(filesToDelete(safety, now, keep = 3, kind = LocalBackupKind.BEFORE_IMPORT, protect = setOf(restoring))).isEmpty()
    }

    @Test
    fun weeklyFileName_isTheAutomaticKind() {
        val name = weeklyBackupFileName(LocalDateTime.of(2026, 11, 15, 3, 5, 59))
        assertThat(name).isEqualTo("Recetario-2026-11-15-0305.zip")
        assertThat(parseLocalBackupName(name)?.kind).isEqualTo(LocalBackupKind.AUTOMATIC)
    }
}
