package app.recetario.pairing

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Catálogo incluido (`pairing/catalog.json` del classpath): bebidas con y sin alcohol, acompañamientos, entradas y
 * postres genéricos con su perfil. Sus ids (`cat-…`) son estables: las valoraciones los guardan.
 */
object Catalog {
    private val items: List<PairingItem> by lazy {
        readResource("pairing/catalog.json", ListSerializer(CatalogEntry.serializer()))
            .map { it.toItem() }
    }

    fun load(): List<PairingItem> = items
}

/** Lector estricto de los recursos de maridaje (una clave desconocida es un error del contenido). */
private val pairingResourceJson = Json { ignoreUnknownKeys = false }

/** Lee un recurso JSON del jar de `logic` con el cargador de esta clase (también desde `:app`), en UTF-8. */
internal fun <T> readResource(path: String, deserializer: DeserializationStrategy<T>): T {
    val stream = requireNotNull(Catalog::class.java.classLoader.getResourceAsStream(path)) { "Falta el recurso $path" }
    return pairingResourceJson.decodeFromString(deserializer, stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
}

@Serializable
private data class CatalogEntry(
    val id: String,
    val name: String,
    val article: String,
    val role: PairRole,
    val course: Course? = null,
    val alcoholic: Boolean,
    val serveTemp: ServeTemp,
    val sweet: Int = 0,
    val salty: Int = 0,
    val sour: Int = 0,
    val bitter: Int = 0,
    val spicy: Int = 0,
    val intensity: Intensity,
    val rich: Boolean = false,
) {
    fun toItem() = PairingItem(
        kind = CATALOG,
        id = id,
        name = name,
        role = role,
        course = course,
        alcoholic = alcoholic,
        flavor = FlavorProfile(sweet, salty, sour, bitter, spicy, intensity, rich),
        serveTemp = serveTemp,
        article = article,
    )
}
