package app.recetario.season

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Cómo está un producto en un mes: en su mejor momento, se encuentra o no es temporada. */
enum class Availability { BEST, AVAILABLE, OUT }

enum class ProduceKind { FRUIT, VEGETABLE, CHILE, HERB }

/**
 * Un producto del calendario: [months] tiene 12 valores, de enero a diciembre. [excludes]: frases que, si están en un
 * ingrediente, hacen que ese ingrediente no cuente para el producto («leche de coco», «uva pasa»).
 */
data class Produce(
    val id: String,
    val name: String,
    val emoji: String,
    val kind: ProduceKind,
    val synonyms: List<String>,
    val months: List<Availability>,
    val excludes: List<String> = emptyList(),
) {
    init {
        require(months.size == 12) { "$id: hacen falta 12 meses" }
    }

    fun availabilityIn(month: Int): Availability {
        require(month in 1..12) { "Mes fuera de rango: $month" }
        return months[month - 1]
    }
}

/**
 * Calendario de temporada de los mercados del centro y el occidente de México (`season/mexico.json` del classpath),
 * en el orden del archivo. **El orden importa:** lo primero de cada mes es lo que nombra la tarjeta de Inicio
 * ([seasonTeaser]); por eso guayaba, mandarina y calabaza van al principio. Los meses se escriben como 12 letras: `B` en su mejor momento, `A` hay, `-` no es temporada.
 */
object SeasonCalendar {
    private val items: List<Produce> by lazy {
        val stream = requireNotNull(SeasonCalendar::class.java.classLoader.getResourceAsStream(RESOURCE)) {
            "Falta el recurso $RESOURCE"
        }
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString(ListSerializer(ProduceEntry.serializer()), text).map { it.toProduce() }
    }

    fun load(): List<Produce> = items

    /** Del mes [month] (1–12): primero lo que está en su mejor momento y después lo que también hay. */
    fun forMonth(month: Int): Pair<List<Produce>, List<Produce>> = forMonth(month, load())

    internal fun forMonth(month: Int, calendar: List<Produce>): Pair<List<Produce>, List<Produce>> {
        require(month in 1..12) { "Mes fuera de rango: $month" }
        return calendar.filter { it.months[month - 1] == Availability.BEST } to
            calendar.filter { it.months[month - 1] == Availability.AVAILABLE }
    }

    private const val RESOURCE = "season/mexico.json"
    private val json = Json { ignoreUnknownKeys = false }
}

@Serializable
private data class ProduceEntry(
    val id: String,
    val name: String,
    val emoji: String,
    val kind: ProduceKind,
    val synonyms: List<String> = emptyList(),
    val months: String,
    val excludes: List<String> = emptyList(),
) {
    fun toProduce(): Produce {
        require(months.length == 12) { "$id: los meses deben ser 12 letras" }
        return Produce(id, name, emoji, kind, synonyms, excludes = excludes, months = months.map {
            when (it) {
                'B' -> Availability.BEST
                'A' -> Availability.AVAILABLE
                '-' -> Availability.OUT
                else -> throw IllegalArgumentException("$id: mes «$it» desconocido")
            }
        })
    }
}
