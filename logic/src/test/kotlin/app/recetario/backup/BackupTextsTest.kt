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
    fun importSummary_countsDiaryEntries_whenThereAreAny() {
        assertThat(importSummary(3, 5, diaryEntries = 2)).isEqualTo("Copia importada: 3 recetas, 2 entradas del diario, 5 fotos.")
        assertThat(importSummary(1, 1, failedImages = 1, diaryEntries = 1))
            .isEqualTo("Copia importada: 1 receta, 1 entrada del diario, 1 foto. 1 foto no se pudo copiar.")
        assertThat(importSummary(3, 5, diaryEntries = 0)).isEqualTo("Copia importada: 3 recetas, 5 fotos.")
    }

    @Test
    fun replaceComparison_comparesTheCopyWithWhatYouHave() {
        assertThat(replaceComparison(backupRecipes = 12, localRecipes = 40)).isEqualTo("La copia tiene 12 recetas; ahora tienes 40.")
        assertThat(replaceComparison(backupRecipes = 1, localRecipes = 1)).isEqualTo("La copia tiene 1 receta; ahora tienes 1.")
        assertThat(replaceComparison(backupRecipes = 0, localRecipes = 3)).isEqualTo("La copia tiene 0 recetas; ahora tienes 3.")
    }

    @Test
    fun replaceComparison_mentionsTheDiary_whenEitherSideHasEntries() {
        assertThat(replaceComparison(backupRecipes = 12, localRecipes = 40, backupDiaryEntries = 0, localDiaryEntries = 3))
            .isEqualTo("La copia tiene 12 recetas y 0 entradas del diario; ahora tienes 40 recetas y 3 entradas del diario.")
        assertThat(replaceComparison(backupRecipes = 1, localRecipes = 1, backupDiaryEntries = 1, localDiaryEntries = 0))
            .isEqualTo("La copia tiene 1 receta y 1 entrada del diario; ahora tienes 1 receta y 0 entradas del diario.")
        assertThat(replaceComparison(backupRecipes = 2, localRecipes = 3, backupDiaryEntries = 0, localDiaryEntries = 0))
            .isEqualTo("La copia tiene 2 recetas; ahora tienes 3.")
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

    @Test
    fun parse_beforeUpdate() {
        assertThat(parseLocalBackupName("antes-de-actualizar-v1-2026-11-20-0915.db"))
            .isEqualTo(LocalBackupName(LocalBackupKind.BEFORE_UPDATE, LocalDateTime.of(2026, 11, 20, 9, 15), 1, fromVersion = 1))
        assertThat(parseLocalBackupName("antes-de-actualizar-v12-2026-11-20-0915-3.db"))
            .isEqualTo(LocalBackupName(LocalBackupKind.BEFORE_UPDATE, LocalDateTime.of(2026, 11, 20, 9, 15), 3, fromVersion = 12))
        for (name in listOf(
            "antes-de-actualizar-v1-2026-11-20-0915.zip",
            "antes-de-actualizar-v1-2026-11-20-0915.db.tmp",
            "antes-de-actualizar-v1-2026-11-20-0915.db-wal",
            "antes-de-actualizar-2026-11-20-0915.db",
            "antes-de-importar-2026-11-20-0915.db",
            "Sazon-2026-11-20-0915.db",
            "antes-de-actualizar-v1-2026-02-30-0915.db",
        )) {
            assertThat(parseLocalBackupName(name)).isNull()
        }
        assertThat(parseLocalBackupName("Sazon-2026-11-09-0305.zip")?.fromVersion).isNull()
        assertThat(beforeUpdateFileName(1, LocalDateTime.of(2026, 11, 20, 9, 15, 59))).isEqualTo("antes-de-actualizar-v1-2026-11-20-0915.db")
        assertThat(isDatabaseCopy("antes-de-actualizar-v1-2026-11-20-0915.db")).isTrue()
        assertThat(isDatabaseCopy("antes-de-importar-2026-11-20-0915.zip")).isFalse()
        assertThat(isDatabaseCopy("otra.db")).isFalse()
    }

    @Test
    fun beforeUpdate_titleAndOrder() {
        assertThat(localBackupTitle("antes-de-actualizar-v1-2026-11-20-0915.db")).isEqualTo("Antes de actualizar · 20 de noviembre de 2026, 09:15")
        assertThat(sortLocalBackupsNewestFirst(listOf("Sazon-2026-11-14-0300.zip", "antes-de-actualizar-v1-2026-11-20-0915.db", "otra.zip")))
            .containsExactly("antes-de-actualizar-v1-2026-11-20-0915.db", "Sazon-2026-11-14-0300.zip", "otra.zip").inOrder()
        assertThat(BackupMessages.UPDATE_COPY_FAILED)
            .isEqualTo("No hay espacio para hacer la copia de seguridad antes de actualizar. Libera espacio y vuelve a abrir Sazón.")
        assertThat(BackupMessages.DATABASE_UNREADABLE)
            .isEqualTo("No se pudo leer la base de datos de Sazón. No se ha borrado nada.")
        assertThat(DATABASE_FILE_NAME).isEqualTo("Sazon-base-de-datos.db")
        assertThat(BackupMessages.MIGRATION_FAILED)
            .isEqualTo("No se pudo actualizar la base de datos. Tus recetas siguen guardadas en la copia previa.")
    }
}
