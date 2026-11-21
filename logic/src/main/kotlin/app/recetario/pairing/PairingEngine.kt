package app.recetario.pairing

import app.recetario.core.text.normalize

/** Puntos que suma una pareja con 👍. */
const val LIKED_BONUS = 15

/** Por debajo de esta puntuación una pareja no se sugiere (salvo que tenga 👍). */
const val MIN_SUGGESTION_SCORE = 40

private const val WEIGHT_STEP = 0.02
private const val MIN_FACTOR = 0.8
private const val MAX_FACTOR = 1.2

/**
 * Pesos aprendidos: cada principio parte de un factor 1; cada 👍 de una pareja donde fue el que más pesó
 * (`top` con los pesos de partida) suma 0,02 y cada 👎 resta 0,02. El factor final se acota entre 0,8 y 1,2.
 */
fun learnedWeights(ratings: List<RatedPair>): Map<Principle, Double> {
    val net = ratings.groupingBy { scorePair(it.a, it.b).top }.fold(0) { acc, r -> acc + if (r.liked) 1 else -1 }
    return DEFAULT_WEIGHTS.mapValues { (p, w) -> w * (1 + WEIGHT_STEP * (net[p] ?: 0)).coerceIn(MIN_FACTOR, MAX_FACTOR) }
}

/** Solo cruces con sentido: bebida con comida, acompañamiento o postre, y al revés. */
internal fun sensiblePair(subject: PairingItem, other: PairingItem): Boolean =
    (subject.role == PairRole.DRINK) != (other.role == PairRole.DRINK)

internal fun sameItem(a: PairingItem, b: PairingItem) = a.kind == b.kind && a.id == b.id

/**
 * Hasta [limit] parejas para [subject]: primero las recetas propias y después el catálogo; dentro de cada grupo, de
 * mayor a menor puntuación. Las parejas con 👎 no salen; las de 👍 suman [LIKED_BONUS] (hasta 100). Las que no llegan
 * a [MIN_SUGGESTION_SCORE] tampoco salen, salvo con 👍. Catálogo con catálogo sale sin valoración posible.
 */
fun suggestPairings(
    subject: PairingItem,
    candidates: List<PairingItem>,
    ratings: Map<String, Boolean>,
    limit: Int,
    weights: Map<Principle, Double> = DEFAULT_WEIGHTS,
): List<Suggestion> = candidates
    .filter { sensiblePair(subject, it) && !sameItem(subject, it) }
    .mapNotNull { candidate ->
        val liked = ratableKey(subject, candidate)?.let { ratings[it] }
        if (liked == false) return@mapNotNull null
        val score = scorePair(subject, candidate, weights)
        val points = if (liked == true) minOf(100, score.score + LIKED_BONUS) else score.score
        if (liked != true && points < MIN_SUGGESTION_SCORE) return@mapNotNull null
        Suggestion(candidate, points, score.reasons, liked)
    }
    .sortedWith(
        compareBy<Suggestion> { if (it.item.kind == RECIPE) 0 else 1 }
            .thenByDescending { it.score }
            .thenBy { normalize(it.item.name) }
            .thenBy { it.item.id },
    )
    .take(limit.coerceAtLeast(0))
