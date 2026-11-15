package app.recetario.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BackupTextsTest {
    @Test
    fun importSummary_countsRecipesAndPhotos() {
        assertThat(importSummary(3, 5)).isEqualTo("Copia importada: 3 recetas, 5 fotos.")
        assertThat(importSummary(1, 1)).isEqualTo("Copia importada: 1 receta, 1 foto.")
        assertThat(importSummary(0, 0)).isEqualTo("Copia importada: 0 recetas, 0 fotos.")
        assertThat(importSummary(3, 5, failedImages = 0)).isEqualTo("Copia importada: 3 recetas, 5 fotos.")
        assertThat(importSummary(3, 5, failedImages = 2))
            .isEqualTo("Copia importada: 3 recetas, 5 fotos. 2 fotos no se pudieron copiar.")
        assertThat(importSummary(1, 0, failedImages = 1))
            .isEqualTo("Copia importada: 1 receta, 0 fotos. 1 foto no se pudo copiar.")
    }

    @Test
    fun replaceComparison_comparesTheCopyWithWhatYouHave() {
        assertThat(replaceComparison(backupRecipes = 12, localRecipes = 40)).isEqualTo("La copia tiene 12 recetas; ahora tienes 40.")
        assertThat(replaceComparison(backupRecipes = 1, localRecipes = 1)).isEqualTo("La copia tiene 1 receta; ahora tienes 1.")
        assertThat(replaceComparison(backupRecipes = 0, localRecipes = 3)).isEqualTo("La copia tiene 0 recetas; ahora tienes 3.")
    }

    @Test
    fun localBackupNames_areParsedFromTheFileName() {
        assertThat(parseLocalBackupName("antes-de-importar-2026-11-13-2140.zip"))
            .isEqualTo(LocalBackupName(LocalBackupKind.BEFORE_IMPORT, LocalDateTime.of(2026, 11, 13, 21, 40), 1))
        assertThat(parseLocalBackupName("antes-de-importar-2026-11-13-2140-3.zip"))
            .isEqualTo(LocalBackupName(LocalBackupKind.BEFORE_IMPORT, LocalDateTime.of(2026, 11, 13, 21, 40), 3))
        assertThat(parseLocalBackupName("Sazon-2026-11-09-0305.zip"))
            .isEqualTo(LocalBackupName(LocalBackupKind.AUTOMATIC, LocalDateTime.of(2026, 11, 9, 3, 5), 1))
        val notOurs = listOf(
            "antes-de-importar-2026-13-13-2140.zip",
            "otra.zip",
            "antes-de-importar-2026-11-13-2140.zip.tmp",
            "Sazon-2026-11-13.zip",
            // El nombre del archivo va sin tilde, y el nombre provisional de la app ya no es de los nuestros.
            "Sazón-2026-11-09-0305.zip",
            "Recetario-2026-11-09-0305.zip",
        )
        for (name in notOurs) {
            assertThat(parseLocalBackupName(name)).isNull()
        }
    }

    @Test
    fun localBackupTitle_saysWhatAndWhen() {
        assertThat(localBackupTitle("antes-de-importar-2026-11-13-2140-2.zip")).isEqualTo("Antes de importar · 13 de noviembre de 2026, 21:40")
        assertThat(localBackupTitle("Sazon-2026-11-09-0305.zip")).isEqualTo("Copia automática · 9 de noviembre de 2026, 03:05")
        assertThat(localBackupTitle("otra.zip")).isEqualTo("otra.zip")
        assertThat(recipeCountText(1)).isEqualTo("1 receta")
        assertThat(recipeCountText(7)).isEqualTo("7 recetas")
    }

    @Test
    fun localBackups_newestFirst() {
        val names = listOf(
            "antes-de-importar-2026-11-13-2140.zip",
            "otra.zip",
            "Sazon-2026-11-14-0300.zip",
            "antes-de-importar-2026-11-13-2140-2.zip",
            "antes-de-importar-2026-11-01-0900.zip",
            "a-mano.zip",
        )
        assertThat(sortLocalBackupsNewestFirst(names)).containsExactly(
            "Sazon-2026-11-14-0300.zip",
            "antes-de-importar-2026-11-13-2140-2.zip",
            "antes-de-importar-2026-11-13-2140.zip",
            "antes-de-importar-2026-11-01-0900.zip",
            "otra.zip",
            "a-mano.zip",
        ).inOrder()
    }

    @Test
    fun fileNames() {
        assertThat(exportFileName(LocalDate.of(2026, 11, 3))).isEqualTo("Sazon-2026-11-03.zip")
        assertThat(safetyCopyFileName(LocalDateTime.of(2026, 11, 13, 21, 40, 59))).isEqualTo("antes-de-importar-2026-11-13-2140.zip")
    }

    @Test
    fun lastExternal_inLocalTime() {
        val madrid = ZoneId.of("Europe/Madrid")
        assertThat(lastExternalText(Instant.parse("2026-11-13T20:40:00Z"), madrid))
            .isEqualTo("Última copia guardada fuera del teléfono: 13 de noviembre de 2026, 21:40")
        assertThat(lastExternalText(null, madrid))
            .isEqualTo("Todavía no has guardado ninguna copia fuera del teléfono.")
    }

    @Test
    fun lastLocalAndDrive_inLocalTime() {
        val madrid = ZoneId.of("Europe/Madrid")
        val at = Instant.parse("2026-11-13T20:40:00Z")
        assertThat(lastLocalText(at, madrid)).isEqualTo("Última copia en el teléfono: 13 de noviembre de 2026, 21:40")
        assertThat(lastLocalText(null, madrid)).isEqualTo("Última copia en el teléfono: todavía ninguna")
        assertThat(lastDriveText(at, madrid)).isEqualTo("Última copia en Drive: 13 de noviembre de 2026, 21:40")
        assertThat(lastDriveText(null, madrid)).isEqualTo("Última copia en Drive: todavía ninguna")
    }

    @Test
    fun driveTexts_areTheAgreedOnes() {
        assertThat(BackupMessages.DRIVE_FAILED).isEqualTo("No se pudo guardar en Google Drive. Vuelve a elegir el archivo.")
        assertThat(BackupMessages.SAVE_OUTSIDE_LOST).isEqualTo("No se pudo guardar la copia. Inténtalo de nuevo.")
        assertThat(DRIVE_DOCUMENT_NAME).isEqualTo("Sazon-copia-automatica.zip")
        assertThat(BackupMessages.DRIVE_NOT_A_COPY).isEqualTo("Ese archivo no es una copia de Sazón. ¿Reemplazarlo?")
        assertThat(driveExistingCopyText(backupRecipes = 12, localRecipes = 40)).isEqualTo("Ese archivo ya tiene una copia con 12 recetas; ahora tienes 40.")
        assertThat(driveExistingCopyText(backupRecipes = 1, localRecipes = 1)).isEqualTo("Ese archivo ya tiene una copia con 1 receta; ahora tienes 1.")
    }

    @Test
    fun messages_areTheAgreedOnes() {
        assertThat(BackupMessages.NOT_A_BACKUP).isEqualTo("Este archivo no es una copia de Sazón.")
        assertThat(BackupMessages.NEWER_BACKUP)
            .isEqualTo("Esta copia es de una versión más nueva de Sazón. Actualiza la app e inténtalo de nuevo.")
        assertThat(BackupMessages.SAFETY_COPY_FAILED)
            .isEqualTo("No se pudo hacer una copia de seguridad previa. No se ha cambiado nada.")
        assertThat(BackupMessages.EXPORTED).isEqualTo("Copia guardada.")
        assertThat(BackupMessages.INCOMPLETE).isEqualTo("La copia está incompleta: faltan fotos.")
        assertThat(BackupMessages.DAMAGED).isEqualTo("Copia dañada")
    }
}
