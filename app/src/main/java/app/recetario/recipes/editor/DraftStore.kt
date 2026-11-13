package app.recetario.recipes.editor

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Un borrador por destino de edición: `new.json` para una receta nueva y `<recipeId>.json` al editar. */
class DraftStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(key: String): File {
        require(key.isNotEmpty() && '/' !in key && '\\' !in key && key != "." && key != "..") { "Clave de borrador no válida" }
        return File(dir, "$key.json")
    }

    fun save(key: String, state: EditorState) {
        dir.mkdirs()
        val target = file(key)
        val tmp = File(dir, "$key.json.tmp")
        tmp.writeText(json.encodeToString(EditorState.serializer(), state.copy(errors = emptyMap())))
        // rename sustituye el destino de forma atómica; si falla, el borrador anterior se conserva.
        if (!tmp.renameTo(target)) throw IOException("No se pudo guardar el borrador")
    }

    /** Null si no hay borrador o está dañado (en ese caso se aparta como `<key>.bad`, nunca se borra). */
    fun load(key: String): EditorState? {
        val f = file(key)
        if (!f.isFile) return null
        return try {
            json.decodeFromString(EditorState.serializer(), f.readText())
        } catch (_: Exception) {
            val bad = File(dir, "$key.bad")
            bad.delete()
            f.renameTo(bad)
            null
        }
    }

    fun clear(key: String) {
        file(key).delete()
    }

    companion object {
        const val NEW_KEY = "new"
    }
}
