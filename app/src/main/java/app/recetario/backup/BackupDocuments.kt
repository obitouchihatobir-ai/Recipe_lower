package app.recetario.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/** Los archivos que el usuario elige con el selector del sistema (Drive, Descargas…). Las pruebas usan uno falso. */
interface BackupDocuments {
    fun openInput(uri: String): InputStream
    fun openOutput(uri: String): OutputStream

    /** Borra un documento que se quedó a medio escribir, si el proveedor lo permite. No falla nunca. */
    fun delete(uri: String)
}

class ContentResolverDocuments(private val resolver: ContentResolver) : BackupDocuments {
    override fun openInput(uri: String): InputStream =
        resolver.openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException(uri)

    // «wt»: si el proveedor reutiliza el archivo, se vacía antes de escribir.
    override fun openOutput(uri: String): OutputStream =
        resolver.openOutputStream(Uri.parse(uri), "wt") ?: throw FileNotFoundException(uri)

    override fun delete(uri: String) {
        try {
            DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
        } catch (e: Exception) {
            Log.w("BackupDocuments", "No se pudo borrar la copia incompleta", e)
        }
    }
}
