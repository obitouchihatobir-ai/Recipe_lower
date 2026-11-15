package app.recetario.backup

import java.time.LocalDateTime
import java.time.temporal.IsoFields

/** Semanas de las que se conserva una copia automática en el teléfono (§8.5). */
const val WEEKLY_BACKUPS_KEPT = 4

/** Copias previas a «Reemplazar todo» (o a una importación que actualiza recetas) que se conservan. */
const val SAFETY_COPIES_KEPT = 3

/**
 * Copias del teléfono que sobran, solo de tipo [kind] (nunca de otro tipo ni un nombre que no sea de los nuestros:
 * temporales `.zip.tmp`, archivos ajenos):
 * - semanales ([LocalBackupKind.AUTOMATIC]): la más reciente de cada semana ISO, de las [keep] semanas más recientes.
 *   Así, varias copias en la misma semana («Reintentar») sustituyen a la de esa semana y no borran las anteriores;
 * - previas a importar: los [keep] archivos más recientes.
 * Las fechas son las del nombre, en la hora del teléfono. Un nombre con fecha de más de un día después de [now] (el
 * reloj estuvo adelantado) no cuenta y nunca se borra. Tampoco se borra nunca uno de [protect] (la recién escrita,
 * la que se está restaurando).
 */
fun filesToDelete(
    files: List<String>,
    now: LocalDateTime,
    keep: Int = WEEKLY_BACKUPS_KEPT,
    kind: LocalBackupKind = LocalBackupKind.AUTOMATIC,
    protect: Set<String> = emptySet(),
): List<String> {
    require(keep >= 1) { "Siempre se conserva al menos una copia" }
    val limit = now.plusDays(1)
    val ranked = sortLocalBackupsNewestFirst(
        files.distinct().filter { name ->
            val parsed = parseLocalBackupName(name)
            parsed != null && parsed.kind == kind && !parsed.dateTime.isAfter(limit)
        },
    )
    val kept: Set<String> = when (kind) {
        LocalBackupKind.BEFORE_IMPORT -> ranked.take(keep).toSet()
        LocalBackupKind.AUTOMATIC -> {
            // La primera de cada semana en el orden de más reciente a más antigua es la más reciente de esa semana.
            val weeks = LinkedHashMap<Pair<Int, Int>, String>()
            for (name in ranked) {
                val week = isoWeek(parseLocalBackupName(name)!!.dateTime)
                if (week !in weeks && weeks.size < keep) weeks[week] = name
            }
            weeks.values.toSet()
        }
    }
    return ranked.filter { it !in kept && it !in protect }
}

private fun isoWeek(dateTime: LocalDateTime): Pair<Int, Int> =
    dateTime.get(IsoFields.WEEK_BASED_YEAR) to dateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
