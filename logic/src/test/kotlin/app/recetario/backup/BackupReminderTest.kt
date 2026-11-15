package app.recetario.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BackupReminderTest {
    private val now = Instant.parse("2026-11-15T10:00:00Z")
    private fun daysAgo(days: Long): Instant = now.minus(Duration.ofDays(days))

    @Test
    fun reminder() {
        // 31 días desde la última copia fuera del teléfono → true; 29 → false.
        assertThat(shouldRemindBackup(now, lastExternal = daysAgo(31), firstRecipeAt = daysAgo(400))).isTrue()
        assertThat(shouldRemindBackup(now, lastExternal = daysAgo(29), firstRecipeAt = daysAgo(400))).isFalse()
        // Nunca hubo copia y la primera receta tiene 31 días → true; 29 → false.
        assertThat(shouldRemindBackup(now, lastExternal = null, firstRecipeAt = daysAgo(31))).isTrue()
        assertThat(shouldRemindBackup(now, lastExternal = null, firstRecipeAt = daysAgo(29))).isFalse()
        // Sin recetas → false (no hay nada que proteger).
        assertThat(shouldRemindBackup(now, lastExternal = null, firstRecipeAt = null)).isFalse()
        assertThat(shouldRemindBackup(now, lastExternal = daysAgo(90), firstRecipeAt = null)).isFalse()
    }

    @Test
    fun reminder_onlyAfterMoreThan30Days() {
        assertThat(shouldRemindBackup(now, lastExternal = daysAgo(30), firstRecipeAt = daysAgo(400))).isFalse()
        assertThat(shouldRemindBackup(now, lastExternal = daysAgo(30).minusSeconds(1), firstRecipeAt = daysAgo(400))).isTrue()
        // Una fecha en el futuro (el reloj cambió) no avisa.
        assertThat(shouldRemindBackup(now, lastExternal = now.plus(Duration.ofDays(3)), firstRecipeAt = daysAgo(400))).isFalse()
    }

    @Test
    fun notice_errorFirst_thenReminder() {
        assertThat(backupNoticeFor(now, lastError = "No se pudo guardar en Google Drive. Vuelve a elegir el archivo.", lastExternal = daysAgo(1), firstRecipeAt = daysAgo(1)))
            .isEqualTo(BackupNotice.Error("No se pudo guardar en Google Drive. Vuelve a elegir el archivo."))
        // El error se muestra aunque no haya recetas: la copia de Drive no funciona.
        assertThat(backupNoticeFor(now, lastError = "Fallo", lastExternal = null, firstRecipeAt = null)).isEqualTo(BackupNotice.Error("Fallo"))
        assertThat(backupNoticeFor(now, lastError = null, lastExternal = null, firstRecipeAt = daysAgo(31))).isEqualTo(BackupNotice.Reminder)
        assertThat(backupNoticeFor(now, lastError = null, lastExternal = daysAgo(2), firstRecipeAt = daysAgo(31))).isNull()
    }

    @Test
    fun reminderText_isTheAgreedOne() {
        assertThat(BackupMessages.REMINDER).isEqualTo("Hace más de 30 días que no guardas una copia fuera del teléfono.")
    }
}
