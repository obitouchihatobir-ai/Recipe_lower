package app.recetario.backup

import java.time.Duration
import java.time.Instant

/** Días sin una copia fuera del teléfono (Drive o exportación manual) tras los que Inicio lo recuerda. */
private val REMINDER_AFTER: Duration = Duration.ofDays(30)

/**
 * Si Inicio debe recordar que se guarde una copia fuera del teléfono: la última ([lastExternal]) tiene más de
 * 30 días, o nunca la hubo y la primera receta ([firstRecipeAt]) tiene más de 30 días. Sin recetas, nunca (no hay
 * nada que proteger). Una fecha en el futuro (el reloj cambió) no avisa.
 */
fun shouldRemindBackup(now: Instant, lastExternal: Instant?, firstRecipeAt: Instant?): Boolean {
    if (firstRecipeAt == null) return false
    val since = lastExternal ?: firstRecipeAt
    return Duration.between(since, now) > REMINDER_AFTER
}

/** Tarjeta de Inicio sobre las copias. */
sealed interface BackupNotice {
    /** La copia automática no se pudo guardar en Drive (o se perdió el permiso): [message] lo explica. */
    data class Error(val message: String) : BackupNotice

    /** Hace más de 30 días que no sale ninguna copia del teléfono. */
    data object Reminder : BackupNotice
}

/** Primero el error de la copia automática (aunque no haya recetas); si no lo hay, el recordatorio. */
fun backupNoticeFor(now: Instant, lastError: String?, lastExternal: Instant?, firstRecipeAt: Instant?): BackupNotice? = when {
    lastError != null -> BackupNotice.Error(lastError)
    shouldRemindBackup(now, lastExternal, firstRecipeAt) -> BackupNotice.Reminder
    else -> null
}
