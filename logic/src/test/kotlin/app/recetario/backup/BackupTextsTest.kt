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
    }

    @Test
    fun fileNames() {
        assertThat(exportFileName(LocalDate.of(2026, 11, 3))).isEqualTo("Recetario-2026-11-03.zip")
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
    fun messages_areTheAgreedOnes() {
        assertThat(BackupMessages.NOT_A_BACKUP).isEqualTo("Este archivo no es una copia del Recetario.")
        assertThat(BackupMessages.NEWER_BACKUP)
            .isEqualTo("Esta copia es de una versión más nueva de la app. Actualiza la app e inténtalo de nuevo.")
        assertThat(BackupMessages.SAFETY_COPY_FAILED)
            .isEqualTo("No se pudo hacer una copia de seguridad previa. No se ha cambiado nada.")
        assertThat(BackupMessages.EXPORTED).isEqualTo("Copia guardada.")
    }
}
