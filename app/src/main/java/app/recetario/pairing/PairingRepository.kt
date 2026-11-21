package app.recetario.pairing

import app.recetario.core.data.AppDatabase
import app.recetario.pairing.data.PairingRatingEntity
import app.recetario.recipes.data.RecipeRepository
import app.recetario.today.asPairingItem
import app.recetario.today.toTodayRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

/** Una pareja valorada, con sus dos lados ya resueltos (receta propia o elemento del catálogo). */
data class RatedPairView(
    val pairKey: String,
    val a: PairingItem,
    val b: PairingItem,
    val liked: Boolean,
    val ratedAt: Instant,
)

/**
 * Todo lo que necesitan las pantallas de maridaje: las recetas propias y el catálogo como [PairingItem], las
 * valoraciones por `pairKey`, el historial y los pesos aprendidos de las valoraciones.
 */
data class PairingData(
    val recipes: List<PairingItem>,
    val catalog: List<PairingItem>,
    val ratings: Map<String, Boolean>,
    val history: List<RatedPairView>,
    val weights: Map<Principle, Double>,
) {
    val all: List<PairingItem> get() = recipes + catalog

    /** El elemento de [kind] e [id], o null si ya no existe. */
    fun find(kind: String, id: String): PairingItem? = (if (kind == "RECIPE") recipes else catalog).firstOrNull { it.id == id }
}

/** Valoraciones de maridaje (👍/👎), en `pairing_ratings`. */
interface PairingRepository {
    /** `pairKey` → true (👍) o false (👎). */
    fun observeRatings(): Flow<Map<String, Boolean>>

    /**
     * Valora la pareja ([liked] null la borra). Escribe `pairKey` y los lados ordenados, con la hora del reloj.
     * Una pareja de dos elementos del catálogo no se puede valorar: lanza [IllegalArgumentException] sin escribir nada.
     */
    suspend fun rate(a: PairingItem, b: PairingItem, liked: Boolean?)

    /** Las parejas valoradas, de la más reciente a la más antigua (las que tienen un lado que ya no existe no salen). */
    fun observeHistory(): Flow<List<RatedPairView>>

    /** Recetas, catálogo, valoraciones, historial y pesos, calculados fuera del hilo principal. */
    fun observeData(): Flow<PairingData>
}

class RoomPairingRepository(
    db: AppDatabase,
    private val recipes: RecipeRepository,
    private val clock: Clock,
    private val catalog: () -> List<PairingItem> = Catalog::load,
) : PairingRepository {
    private val dao = db.pairingRatingDao()

    private fun recipeItems(): Flow<List<PairingItem>> =
        combine(recipes.observeSummaries(), recipes.observeCategories()) { summaries, categories ->
            val byId = categories.associateBy { it.id }
            summaries.map { it.toTodayRecipe(byId[it.categoryId]?.baseType).asPairingItem() }
        }

    override fun observeRatings(): Flow<Map<String, Boolean>> =
        dao.observeAll().map { rows -> rows.associate { it.pairKey to it.liked } }

    override suspend fun rate(a: PairingItem, b: PairingItem, liked: Boolean?) {
        val key = ratableKey(a, b) ?: throw IllegalArgumentException("Una pareja de dos elementos del catálogo no se valora")
        if (liked == null) {
            dao.delete(key)
            return
        }
        val (first, second) = listOf(a, b).sortedBy { "${it.kind}:${it.id}" }
        dao.upsert(
            listOf(
                PairingRatingEntity(
                    pairKey = key,
                    aKind = first.kind,
                    aId = first.id,
                    bKind = second.kind,
                    bId = second.id,
                    liked = liked,
                    ratedAt = clock.instant(),
                ),
            ),
        )
    }

    override fun observeHistory(): Flow<List<RatedPairView>> = observeData().map { it.history }

    override fun observeData(): Flow<PairingData> =
        combine(recipeItems(), dao.observeAll()) { own, rows ->
            val cat = catalog()
            val byKey = (own + cat).associateBy { "${it.kind}:${it.id}" }
            val history = rows.mapNotNull { row ->
                val a = byKey["${row.aKind}:${row.aId}"] ?: return@mapNotNull null
                val b = byKey["${row.bKind}:${row.bId}"] ?: return@mapNotNull null
                RatedPairView(row.pairKey, a, b, row.liked, row.ratedAt)
            }.asReversed() // la consulta va de la más antigua a la más reciente (ratedAt, rowid)
            PairingData(
                recipes = own,
                catalog = cat,
                ratings = rows.associate { it.pairKey to it.liked },
                history = history,
                weights = learnedWeights(history.map { RatedPair(it.a, it.b, it.liked) }),
            )
        }.flowOn(Dispatchers.Default)
}
