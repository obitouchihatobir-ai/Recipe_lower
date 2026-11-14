package app.recetario.backup

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos de la pantalla «Copias de seguridad». */
object BackupMessages {
    const val NOT_A_BACKUP = "Este archivo no es una copia del Recetario."
    const val NEWER_BACKUP = "Esta copia es de una versión más nueva de la app. Actualiza la app e inténtalo de nuevo."
    const val SAFETY_COPY_FAILED = "No se pudo hacer una copia de seguridad previa. No se ha cambiado nada."
    const val IMPORT_FAILED = "No se pudo importar la copia. No se ha cambiado nada."
    const val OPEN_FAILED = "No se pudo abrir el archivo. No se ha cambiado nada."
    const val EXPORTED = "Copia guardada."
    const val EXPORT_FAILED = "No se pudo guardar la copia."
    const val NO_PICKER = "No hay ninguna app para elegir el archivo."
}

/** «Copia importada: 3 recetas, 1 foto.» */
fun importSummary(recipes: Int, images: Int): String =
    "Copia importada: ${plural(recipes, "receta", "recetas")}, ${plural(images, "foto", "fotos")}."

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** Nombre que se propone al exportar: `Recetario-2026-11-13.zip`. */
fun exportFileName(date: LocalDate): String = "Recetario-$date.zip"

/** Copia automática que se guarda antes de «Reemplazar todo»: `antes-de-importar-2026-11-13-2140.zip`. */
fun safetyCopyFileName(dateTime: LocalDateTime): String = "antes-de-importar-${dateTime.format(STAMP)}.zip"

private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
private val LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-ES"))

fun lastExternalText(lastExternal: Instant?, zone: ZoneId): String =
    if (lastExternal == null) "Todavía no has guardado ninguna copia fuera del teléfono."
    else "Última copia guardada fuera del teléfono: ${lastExternal.atZone(zone).format(LONG_DATE)}"
