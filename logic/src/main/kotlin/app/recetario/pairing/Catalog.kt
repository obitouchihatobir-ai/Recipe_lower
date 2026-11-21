package app.recetario.pairing

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Catálogo incluido (`pairing/catalog.json` del classpath): bebidas con y sin alcohol, acompañamientos, entradas y
 * postres genéricos con su perfil. Sus ids (`cat-…`) son estables: las valoraciones los guardan.
 */
object Catalog {
    private val items: List<PairingItem> by lazy {
        readResource("pairing/catalog.json") { json.decodeFromString(ListSerializer(CatalogEntry.serializer()), it) }
            .map { it.toItem() }
    }

    fun load(): List<PairingItem> = items
}

internal val json = Json { ignoreUnknownKeys = false }

/** Lee un recurso del jar de `logic` con el cargador de esta clase (también desde `:app`), en UTF-8. */
internal fun <T> readResource(path: String, parse: (String) -> T): T {
    val stream = requireNotNull(Catalog::class.java.classLoader.getResourceAsStream(path)) { "Falta el recurso $path" }
    return parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
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
