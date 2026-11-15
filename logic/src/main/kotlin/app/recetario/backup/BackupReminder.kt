package app.recetario.backup

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

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

/** Tarjeta de Inicio sobre las copias. [kind] distingue un aviso de otro al ocultarlo hasta mañana. */
sealed interface BackupNotice {
    val kind: String

    /**
     * La copia automática falló: en el teléfono ([localError]) o en Drive ([driveError], también si se perdió el
     * permiso). Si fallan las dos se muestran las dos, primero la del teléfono.
     */
    data class Error(val localError: String?, val driveError: String?) : BackupNotice {
        val messages: List<String> get() = listOfNotNull(localError, driveError)
        override val kind: String
            get() = when {
                localError != null && driveError != null -> "error-telefono-drive"
                localError != null -> "error-telefono"
                else -> "error-drive"
            }
    }

    /** Hace más de 30 días que no sale ninguna copia del teléfono. */
    data object Reminder : BackupNotice {
        override val kind: String = "recordatorio"
    }
}

/** Primero los errores de la copia automática (aunque no haya recetas); si no los hay, el recordatorio. */
fun backupNoticeFor(now: Instant, localError: String?, driveError: String?, lastExternal: Instant?, firstRecipeAt: Instant?): BackupNotice? = when {
    localError != null || driveError != null -> BackupNotice.Error(localError, driveError)
    shouldRemindBackup(now, lastExternal, firstRecipeAt) -> BackupNotice.Reminder
    else -> null
}

/** Clave con la que se guarda que el usuario ocultó [notice] el día [day]: otro día u otro aviso vuelven a salir. */
fun noticeDismissalKey(day: LocalDate, notice: BackupNotice): String = "$day|${notice.kind}"
