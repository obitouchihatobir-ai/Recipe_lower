package app.recetario.backup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/** Los archivos que el usuario elige con el selector del sistema (Drive, Descargas…). Las pruebas usan uno falso. */
interface BackupDocuments {
    fun openInput(uri: String): InputStream

    /**
     * Abre para escribir. Con «wt» (lo normal) el documento se vacía antes; algunos proveedores no admiten «wt» y
     * solo «w», que en algunos no vacía el archivo (por eso, tras escribir en Drive se comprueba el tamaño).
     */
    fun openOutput(uri: String, mode: String = "wt"): OutputStream

    /** Borra un documento que se quedó a medio escribir, si el proveedor lo permite. No falla nunca. */
    fun delete(uri: String)

    /** Guarda el permiso de lectura y escritura para siempre (también tras reiniciar); false si el proveedor no lo da. */
    fun takePersistableAccess(uri: String): Boolean

    /** Suelta el permiso guardado (al elegir otro archivo). No falla nunca. */
    fun releasePersistableAccess(uri: String)

    /** Si aún se tiene el permiso guardado de escritura (se pierde, por ejemplo, si se borran los datos de Drive). */
    fun hasPersistedAccess(uri: String): Boolean
}

class ContentResolverDocuments(private val resolver: ContentResolver) : BackupDocuments {
    override fun openInput(uri: String): InputStream =
        resolver.openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException(uri)

    override fun openOutput(uri: String, mode: String): OutputStream =
        resolver.openOutputStream(Uri.parse(uri), mode) ?: throw FileNotFoundException(uri)

    override fun delete(uri: String) {
        try {
            DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo borrar la copia incompleta", e)
        }
    }

    override fun takePersistableAccess(uri: String): Boolean = try {
        resolver.takePersistableUriPermission(Uri.parse(uri), READ_WRITE)
        hasPersistedAccess(uri)
    } catch (e: Exception) {
        // SecurityException: el proveedor no concede permisos persistentes para ese documento.
        Log.w(TAG, "No se pudo guardar el permiso del archivo", e)
        false
    }

    override fun releasePersistableAccess(uri: String) {
        try {
            resolver.releasePersistableUriPermission(Uri.parse(uri), READ_WRITE)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo soltar el permiso del archivo", e)
        }
    }

    override fun hasPersistedAccess(uri: String): Boolean = try {
        val target = Uri.parse(uri)
        resolver.persistedUriPermissions.any { it.uri == target && it.isWritePermission }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudieron leer los permisos guardados", e)
        false
    }

    private companion object {
        const val TAG = "BackupDocuments"
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/**
 * Borra el documento [uri] solo si está vacío (lo acaba de crear el selector y no se llegó a escribir). Si tiene
 * algo (el usuario eligió sobrescribir un archivo que ya existía) o no se puede leer, no se toca. No falla nunca.
 */
fun deleteDocumentIfEmpty(documents: BackupDocuments, uri: String) {
    val empty = try {
        documents.openInput(uri).use { it.read() == -1 }
    } catch (e: Exception) {
        Log.w("BackupDocuments", "No se pudo leer el documento", e)
        false
    }
    if (empty) documents.delete(uri)
}
