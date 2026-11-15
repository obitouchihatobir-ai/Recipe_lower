package app.recetario.backup

/** Copias automáticas semanales que se conservan en el teléfono (§8.5). */
const val WEEKLY_BACKUPS_KEPT = 4

/** Copias previas a «Reemplazar todo» (o a una importación que actualiza recetas) que se conservan. */
const val SAFETY_COPIES_KEPT = 3

/**
 * Copias del teléfono que sobran: de las de tipo [kind] se conservan las [keep] más recientes según su nombre
 * (fecha, hora y número). Nunca devuelve una de otro tipo ni un nombre que no sea de los nuestros (temporales
 * `.zip.tmp`, archivos ajenos), y nunca una de [protect] (la que se acaba de escribir aunque su nombre parezca
 * antiguo, o la que se está restaurando): esas se conservan además de las [keep].
 */
fun filesToDelete(
    files: List<String>,
    keep: Int = WEEKLY_BACKUPS_KEPT,
    kind: LocalBackupKind = LocalBackupKind.AUTOMATIC,
    protect: Set<String> = emptySet(),
): List<String> {
    require(keep >= 1) { "Siempre se conserva al menos una copia" }
    val ofKind = files.distinct().filter { parseLocalBackupName(it)?.kind == kind }
    return sortLocalBackupsNewestFirst(ofKind).drop(keep).filter { it !in protect }
}
