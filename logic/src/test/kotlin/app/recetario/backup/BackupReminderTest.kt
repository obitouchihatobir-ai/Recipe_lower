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
    fun notice_errorsFirst_bothShown_localFirst_thenReminder() {
        val local = "No se pudo hacer la copia semanal en el teléfono."
        val drive = "No se pudo guardar en Google Drive. Vuelve a elegir el archivo."
        assertThat(backupNoticeFor(now, localError = null, driveError = drive, lastExternal = daysAgo(1), firstRecipeAt = daysAgo(1)))
            .isEqualTo(BackupNotice.Error(localError = null, driveError = drive))
        // Los errores se muestran aunque no haya recetas.
        assertThat(backupNoticeFor(now, localError = local, driveError = null, lastExternal = null, firstRecipeAt = null))
            .isEqualTo(BackupNotice.Error(localError = local, driveError = null))
        // Uno no tapa al otro: los dos, primero el del teléfono.
        val both = backupNoticeFor(now, localError = local, driveError = drive, lastExternal = null, firstRecipeAt = daysAgo(31))
        assertThat(both).isEqualTo(BackupNotice.Error(localError = local, driveError = drive))
        assertThat((both as BackupNotice.Error).messages).containsExactly(local, drive).inOrder()
        assertThat(backupNoticeFor(now, localError = null, driveError = null, lastExternal = null, firstRecipeAt = daysAgo(31))).isEqualTo(BackupNotice.Reminder)
        assertThat(backupNoticeFor(now, localError = null, driveError = null, lastExternal = daysAgo(2), firstRecipeAt = daysAgo(31))).isNull()
    }

    @Test
    fun dismissal_isForOneDay_andOneKindOfNotice() {
        val day = java.time.LocalDate.of(2026, 11, 15)
        val reminder = noticeDismissalKey(day, BackupNotice.Reminder)
        val drive = noticeDismissalKey(day, BackupNotice.Error(localError = null, driveError = "Drive"))
        val local = noticeDismissalKey(day, BackupNotice.Error(localError = "Teléfono", driveError = null))
        val both = noticeDismissalKey(day, BackupNotice.Error(localError = "Teléfono", driveError = "Drive"))
        assertThat(reminder).isEqualTo("2026-11-15|recordatorio")
        assertThat(setOf(reminder, drive, local, both)).hasSize(4)
        assertThat(noticeDismissalKey(day.plusDays(1), BackupNotice.Reminder)).isNotEqualTo(reminder)
        // Otro texto del mismo tipo de error es el mismo aviso.
        assertThat(noticeDismissalKey(day, BackupNotice.Error(localError = null, driveError = "Otro"))).isEqualTo(drive)
    }

    @Test
    fun reminderText_isTheAgreedOne() {
        assertThat(BackupMessages.REMINDER).isEqualTo("Hace más de 30 días que no guardas una copia fuera del teléfono.")
        assertThat(BackupMessages.LOCAL_FAILED).isEqualTo("No se pudo hacer la copia semanal en el teléfono.")
    }
}
