package app.recetario.backup

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos de la pantalla «Copias de seguridad». */
object BackupMessages {
    const val NOT_A_BACKUP = "Este archivo no es una copia de Sazón."
    const val NEWER_BACKUP = "Esta copia es de una versión más nueva de Sazón. Actualiza la app e inténtalo de nuevo."
    const val SAFETY_COPY_FAILED = "No se pudo hacer una copia de seguridad previa. No se ha cambiado nada."
    const val IMPORT_FAILED = "No se pudo importar la copia. No se ha cambiado nada."
    const val OPEN_FAILED = "No se pudo abrir el archivo. No se ha cambiado nada."
    const val EXPORTED = "Copia guardada."
    const val EXPORT_FAILED = "No se pudo guardar la copia."
    const val NO_PICKER = "No hay ninguna app para elegir el archivo."
    const val INCOMPLETE = "La copia está incompleta: faltan fotos."
    const val DAMAGED = "Copia dañada"

    /** La copia automática no llegó a Drive (falló la escritura o se perdió el permiso). */
    const val DRIVE_FAILED = "No se pudo guardar en Google Drive. Vuelve a elegir el archivo."
    const val DRIVE_SAVED = "Copia guardada en Google Drive."
    const val DRIVE_UNUSABLE = "No se puede usar ese archivo para las copias automáticas. Elige otro."
    const val NOTHING_TO_SAVE = "Aún no tienes recetas: no hay nada que guardar."

    /** «Guardar fuera»: llegó el documento creado, pero ya no se sabe qué copia guardar (Android cerró la app). */
    const val SAVE_OUTSIDE_LOST = "No se pudo guardar la copia. Inténtalo de nuevo."

    /** Recordatorio de Inicio. */
    const val REMINDER = "Hace más de 30 días que no guardas una copia fuera del teléfono."

    /** La copia semanal no se pudo escribir o comprobar en el teléfono (se muestra en Inicio y en Copias). */
    const val LOCAL_FAILED = "No se pudo hacer la copia semanal en el teléfono."

    /** El archivo elegido para Drive ya tenía algo que no es una copia. */
    const val DRIVE_NOT_A_COPY = "Ese archivo no es una copia de Sazón. ¿Reemplazarlo?"

    /** No se pudo copiar la base de datos antes de migrarla: la app no migra ni abre la base de datos. */
    const val UPDATE_COPY_FAILED = "No hay espacio para hacer la copia de seguridad antes de actualizar. Libera espacio y vuelve a abrir Sazón."

    /** La base de datos está dañada o no se puede leer: no se migra, no se abre y el archivo no se borra. */
    const val DATABASE_UNREADABLE = "No se pudo leer la base de datos de Sazón. No se ha borrado nada."

    /** La migración lanzó una excepción: la copia previa (`antes-de-actualizar-…db`) sigue en el teléfono. */
    const val MIGRATION_FAILED = "No se pudo actualizar la base de datos. Tus recetas siguen guardadas en la copia previa."

    /** Se restauró una copia de antes de actualizar: la app se reinicia (y vuelve a migrar). */
    const val DATABASE_RESTORED = "Copia restaurada. Sazón se reinicia."
}

/** El archivo elegido para Drive ya tiene una copia: «Ese archivo ya tiene una copia con 12 recetas; ahora tienes 40.» */
fun driveExistingCopyText(backupRecipes: Int, localRecipes: Int): String =
    "Ese archivo ya tiene una copia con ${recipeCountText(backupRecipes)}; ahora tienes $localRecipes."

/** Nombre de la app en los nombres de archivo (copias y exportación): sin tilde, para que ningún proveedor ni sistema lo estropee. */
const val FILE_PREFIX = "Sazon"

/** Nombre que se propone al guardar fuera la base de datos tal cual (cuando no se puede leer). */
const val DATABASE_FILE_NAME = "$FILE_PREFIX-base-de-datos.db"

/** Nombre que se propone al elegir el archivo de Google Drive de la copia automática. */
const val DRIVE_DOCUMENT_NAME = "$FILE_PREFIX-copia-automatica.zip"

/** «Copia importada: 3 recetas, 1 foto.», y si alguna foto no se pudo copiar, «2 fotos no se pudieron copiar.» */
fun importSummary(recipes: Int, images: Int, failedImages: Int = 0): String {
    val base = "Copia importada: ${recipeCountText(recipes)}, ${plural(images, "foto", "fotos")}."
    return when (failedImages) {
        0 -> base
        1 -> "$base 1 foto no se pudo copiar."
        else -> "$base $failedImages fotos no se pudieron copiar."
    }
}

fun recipeCountText(n: Int): String = plural(n, "receta", "recetas")

/** En la confirmación de «Reemplazar todo»: «La copia tiene 12 recetas; ahora tienes 40.» */
fun replaceComparison(backupRecipes: Int, localRecipes: Int): String =
    "La copia tiene ${recipeCountText(backupRecipes)}; ahora tienes $localRecipes."

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** Nombre que se propone al exportar: `Sazon-2026-11-13.zip`. */
fun exportFileName(date: LocalDate): String = "$FILE_PREFIX-$date.zip"

/** Copia automática que se guarda antes de «Reemplazar todo»: `antes-de-importar-2026-11-13-2140.zip`. */
fun safetyCopyFileName(dateTime: LocalDateTime): String = "antes-de-importar-${dateTime.format(STAMP)}.zip"

/** Copia automática semanal del teléfono: `Sazon-2026-11-15-0300.zip`. */
fun weeklyBackupFileName(dateTime: LocalDateTime): String = "$FILE_PREFIX-${dateTime.format(STAMP)}.zip"

/** Copia de la base de datos tal cual antes de migrarla: `antes-de-actualizar-v1-2026-11-20-0915.db`. */
fun beforeUpdateFileName(fromVersion: Int, dateTime: LocalDateTime): String =
    "$BEFORE_UPDATE_PREFIX-v$fromVersion-${dateTime.format(STAMP)}.db"

private const val BEFORE_UPDATE_PREFIX = "antes-de-actualizar"

private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
private val LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-ES"))

/**
 * Copias guardadas en el teléfono (`filesDir/backups/`): las previas a importar y las semanales (zip), y las de la
 * base de datos tal cual antes de migrarla (`.db`).
 */
enum class LocalBackupKind(val label: String) {
    BEFORE_IMPORT("Antes de importar"),
    AUTOMATIC("Copia automática"),
    BEFORE_UPDATE("Antes de actualizar"),
}

/**
 * Lo que dice el nombre de una copia del teléfono: de qué tipo es, cuándo se hizo y su número si hubo varias ese
 * minuto; en las de antes de actualizar, también la versión de la base de datos que se copió ([fromVersion]).
 */
data class LocalBackupName(val kind: LocalBackupKind, val dateTime: LocalDateTime, val sequence: Int, val fromVersion: Int? = null)

private val LOCAL_NAME = Regex("""^(antes-de-importar|$FILE_PREFIX)-(\d{4})-(\d{2})-(\d{2})-(\d{2})(\d{2})(?:-(\d{1,4}))?\.zip$""")
private val UPDATE_NAME = Regex("""^$BEFORE_UPDATE_PREFIX-v(\d{1,4})-(\d{4})-(\d{2})-(\d{2})-(\d{2})(\d{2})(?:-(\d{1,4}))?\.db$""")

fun parseLocalBackupName(name: String): LocalBackupName? {
    LOCAL_NAME.matchEntire(name)?.let { m ->
        val (prefix, y, mo, d, h, mi) = m.destructured
        val dateTime = dateTimeOf(y, mo, d, h, mi) ?: return null
        val kind = if (prefix == FILE_PREFIX) LocalBackupKind.AUTOMATIC else LocalBackupKind.BEFORE_IMPORT
        return LocalBackupName(kind, dateTime, m.groupValues[7].toIntOrNull() ?: 1)
    }
    val m = UPDATE_NAME.matchEntire(name) ?: return null
    val (version, y, mo, d, h, mi) = m.destructured
    val dateTime = dateTimeOf(y, mo, d, h, mi) ?: return null
    return LocalBackupName(LocalBackupKind.BEFORE_UPDATE, dateTime, m.groupValues[7].toIntOrNull() ?: 1, fromVersion = version.toInt())
}

/** Si [name] es una copia de la base de datos de antes de actualizar (un `.db`, no un zip de copia). */
fun isDatabaseCopy(name: String): Boolean = parseLocalBackupName(name)?.kind == LocalBackupKind.BEFORE_UPDATE

private fun dateTimeOf(y: String, mo: String, d: String, h: String, mi: String): LocalDateTime? = try {
    LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
} catch (_: Exception) {
    null
}

/** «Antes de importar · 13 de noviembre de 2026, 21:40»; si el nombre no es de los nuestros, el nombre tal cual. */
fun localBackupTitle(name: String): String =
    parseLocalBackupName(name)?.let { "${it.kind.label} · ${it.dateTime.format(LONG_DATE)}" } ?: name

/** Las más recientes primero según su nombre; las de nombre desconocido, al final. */
fun sortLocalBackupsNewestFirst(names: List<String>): List<String> {
    val (known, unknown) = names.partition { parseLocalBackupName(it) != null }
    val byDate = compareByDescending<String> { parseLocalBackupName(it)!!.dateTime }
        .thenByDescending { parseLocalBackupName(it)!!.sequence }
        .thenByDescending { it }
    return known.sortedWith(byDate) + unknown.sortedDescending()
}

fun lastExternalText(lastExternal: Instant?, zone: ZoneId): String =
    if (lastExternal == null) "Todavía no has guardado ninguna copia fuera del teléfono."
    else "Última copia guardada fuera del teléfono: ${lastExternal.atZone(zone).format(LONG_DATE)}"

/** «Última copia en el teléfono: 13 de noviembre de 2026, 21:40» (la semanal) o «…: todavía ninguna». */
fun lastLocalText(lastLocal: Instant?, zone: ZoneId): String = "Última copia en el teléfono: ${dateOrNone(lastLocal, zone)}"

/** «Última copia en Drive: …» o «…: todavía ninguna». */
fun lastDriveText(lastDrive: Instant?, zone: ZoneId): String = "Última copia en Drive: ${dateOrNone(lastDrive, zone)}"

private fun dateOrNone(at: Instant?, zone: ZoneId): String = at?.atZone(zone)?.format(LONG_DATE) ?: "todavía ninguna"
