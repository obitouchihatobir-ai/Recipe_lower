package app.recetario.pairing

import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Reglas de maridaje (diseño general §8.7). Cada principio mira la pareja y, si tiene algo que decir, da unos puntos
 * (positivos si complementa o contrasta bien, negativos si choca) y una frase. La puntuación parte de 50.
 */

private const val BASE_SCORE = 50.0

/** «el agua de jamaica» (catálogo, con artículo) o «mole poblano» (receta propia, inicial en minúscula). */
internal fun ref(item: PairingItem): String {
    val name = item.name.trim()
    val lowered = if (name.length > 1 && name[1].isLowerCase()) name.replaceFirstChar { it.lowercaseChar() } else name
    return if (item.article.isNullOrBlank()) lowered else "${item.article} $lowered"
}

/** «del agua de jamaica», «de la michelada», «de mole poblano». */
internal fun de(item: PairingItem): String = ref(item).let { if (it.startsWith("el ")) "del " + it.removePrefix("el ") else "de $it" }

internal fun capitalized(text: String) = text.replaceFirstChar { it.uppercaseChar() }

private fun c(p: Principle, points: Int, reason: String) = Contribution(p, points.toDouble(), reason)

private fun intensity(a: PairingItem, b: PairingItem): Contribution {
    val (ia, ib) = a.flavor.intensity.ordinal to b.flavor.intensity.ordinal
    return when (abs(ia - ib)) {
        0 -> c(Principle.INTENSITY, 12, "${capitalized(ref(a))} y ${ref(b)} tienen una intensidad parecida: ninguno tapa al otro.")
        1 -> c(Principle.INTENSITY, 4, "${capitalized(ref(a))} y ${ref(b)} tienen intensidades cercanas.")
        else -> {
            val (strong, light) = if (ia > ib) a to b else b to a
            c(Principle.INTENSITY, -15, "La fuerza ${de(strong)} puede tapar lo delicado ${de(light)}.")
        }
    }
}

/**
 * La acidez equilibra lo rico o cremoso de un plato salado (no de una bebida ni de un postre: con el postre manda el
 * dulzor). Al revés choca: una bebida cremosa con un plato ácido. Dos cosas cremosas juntas pesan.
 */
private fun acidCutsRich(a: PairingItem, b: PairingItem): Contribution? {
    val cut = listOf(a to b, b to a)
        .filter { (rich, acid) -> rich.flavor.rich && rich.role != PairRole.DRINK && rich.role != PairRole.DESSERT && acid.flavor.sour >= 1 }
        .maxByOrNull { (_, acid) -> acid.flavor.sour }
    if (cut != null) {
        val (rich, acid) = cut
        return if (acid.flavor.sour >= 2) {
            c(Principle.ACID_CUTS_RICH, 12 + 3 * (acid.flavor.sour - 2), "La acidez ${de(acid)} equilibra lo cremoso ${de(rich)}.")
        } else {
            c(Principle.ACID_CUTS_RICH, 5, "Un toque ácido ${de(acid)} aligera lo cremoso ${de(rich)}.")
        }
    }
    val clash = listOf(a to b, b to a).firstOrNull { (drink, food) ->
        drink.role == PairRole.DRINK && drink.flavor.rich && food.role != PairRole.DRINK && !food.flavor.rich && food.flavor.sour >= 2
    }
    if (clash != null) {
        val (drink, food) = clash
        return c(Principle.ACID_CUTS_RICH, -12, "Lo cremoso ${de(drink)} no combina con lo ácido ${de(food)}.")
    }
    if (a.flavor.rich && b.flavor.rich) {
        return c(Principle.ACID_CUTS_RICH, -8, "Juntos, lo cremoso ${de(a)} y ${de(b)} puede resultar pesado.")
    }
    return null
}

private fun dessertDrink(a: PairingItem, b: PairingItem): Contribution? {
    val (dessert, drink) = when {
        a.role == PairRole.DESSERT && b.role == PairRole.DRINK -> a to b
        b.role == PairRole.DESSERT && a.role == PairRole.DRINK -> b to a
        else -> return null
    }
    if (dessert.flavor.sweet == 0) return null
    val diff = drink.flavor.sweet - dessert.flavor.sweet
    return if (diff >= 0) {
        c(Principle.DESSERT_DRINK_SWEETER, 10, "El dulzor ${de(drink)} está a la altura ${de(dessert)}.")
    } else {
        // Un amargo (café, té, cerveza oscura) con un postre es un contraste buscado: resta la mitad.
        val penalty = maxOf(-30, 10 * diff).let { if (drink.flavor.bitter >= 2) it / 2 else it }
        c(Principle.DESSERT_DRINK_SWEETER, penalty, "Lo dulce ${de(dessert)} resaltará la acidez y el amargor ${de(drink)}.")
    }
}

private fun spicyNeedsFresh(a: PairingItem, b: PairingItem): Contribution? {
    val hotA = a.flavor.spicy >= 2
    val hotB = b.flavor.spicy >= 2
    if (hotA && hotB) {
        return c(Principle.SPICY_NEEDS_FRESH, -6, "Juntos, el picante ${de(a)} y el ${de(b)} se suma y puede resultar excesivo.")
    }
    val (hot, other) = when {
        hotA -> a to b
        hotB -> b to a
        else -> return null
    }
    val f = other.flavor
    if (other.alcoholic == true && f.intensity == Intensity.INTENSE) {
        return c(Principle.SPICY_NEEDS_FRESH, -15, "El alcohol fuerte ${de(other)} aviva el picante ${de(hot)}.")
    }
    if (f.bitter >= 2) return c(Principle.SPICY_NEEDS_FRESH, -8, "Lo amargo ${de(other)} choca con el picante ${de(hot)}.")
    val cold = other.serveTemp == ServeTemp.COLD
    val points = minOf(
        15,
        (if (f.sweet >= 2) 6 else 0) + (if (f.sour >= 2) 5 else 0) + (if (f.rich) 5 else 0) + (if (cold) 4 else 0),
    )
    if (points == 0) return null
    val reason = when {
        f.sweet >= 2 -> "El dulzor ${de(other)} calma el picante ${de(hot)}."
        f.rich -> "Lo cremoso ${de(other)} calma el picante ${de(hot)}."
        f.sour >= 2 -> "Lo fresco y ácido ${de(other)} refresca el picante ${de(hot)}."
        else -> "Lo frío ${de(other)} refresca el picante ${de(hot)}."
    }
    return c(Principle.SPICY_NEEDS_FRESH, points, reason)
}

private fun bitterBalance(a: PairingItem, b: PairingItem): Contribution? {
    if (a.flavor.bitter >= 2 && b.flavor.bitter >= 2) {
        return c(Principle.BITTER_BALANCE, -12, "El amargor ${de(a)} y el ${de(b)} se potencian y pueden resultar ásperos.")
    }
    val (bitter, sweet) = when {
        a.flavor.bitter >= 2 && b.flavor.sweet >= 2 -> a to b
        b.flavor.bitter >= 2 && a.flavor.sweet >= 2 -> b to a
        else -> return null
    }
    return c(Principle.BITTER_BALANCE, 12, "Lo dulce ${de(sweet)} suaviza el amargor ${de(bitter)}.")
}

private fun saltyLikesSourSweet(a: PairingItem, b: PairingItem): Contribution? {
    // Lo salado es el de un plato (no el escarchado de una bebida).
    val options = listOf(a to b, b to a).filter { (salty, _) -> salty.flavor.salty >= 2 && salty.role != PairRole.DRINK }
    if (options.isEmpty()) return null
    val best = options.mapNotNull { (salty, other) ->
        when {
            other.flavor.sour >= 2 -> c(
                Principle.SALTY_LIKES_SOUR_SWEET,
                10 + 2 * (other.flavor.sour - 2),
                "Lo ácido ${de(other)} realza lo salado ${de(salty)}.",
            )
            other.flavor.sweet >= 2 -> c(
                Principle.SALTY_LIKES_SOUR_SWEET,
                8,
                "El contraste dulce ${de(other)} va bien con lo salado ${de(salty)}.",
            )
            else -> null
        }
    }.maxByOrNull { it.points }
    if (best != null) return best
    if (options.size == 2) return c(Principle.SALTY_LIKES_SOUR_SWEET, -6, "Juntos, lo salado ${de(a)} y ${de(b)} puede cansar.")
    return null
}

private fun tempPlural(t: ServeTemp) = when (t) {
    ServeTemp.HOT -> "calientes"
    ServeTemp.COLD -> "fríos"
    ServeTemp.ROOM -> "a temperatura ambiente"
}

private fun temperature(a: PairingItem, b: PairingItem): Contribution? {
    val ta = a.serveTemp ?: return null
    val tb = b.serveTemp ?: return null
    if (ta == tb) {
        return c(Principle.TEMPERATURE, 6, "${capitalized(ref(a))} y ${ref(b)} se sirven ${tempPlural(ta)}: van a juego.")
    }
    val (drink, food) = when {
        a.role == PairRole.DRINK && b.role != PairRole.DRINK -> a to b
        b.role == PairRole.DRINK && a.role != PairRole.DRINK -> b to a
        else -> return null
    }
    return when {
        drink.serveTemp == ServeTemp.COLD && food.serveTemp == ServeTemp.HOT ->
            c(Principle.TEMPERATURE, 4, "Lo frío ${de(drink)} refresca lo caliente ${de(food)}.")
        drink.serveTemp == ServeTemp.HOT && food.serveTemp == ServeTemp.COLD ->
            c(Principle.TEMPERATURE, -6, "Lo caliente ${de(drink)} desentona con lo frío ${de(food)}.")
        else -> null
    }
}

/** Lo que dice cada principio de la pareja, sin pesos; los que no tienen nada que decir no aparecen. */
internal fun pairContributions(a: PairingItem, b: PairingItem): List<Contribution> = listOfNotNull(
    intensity(a, b),
    acidCutsRich(a, b),
    dessertDrink(a, b),
    spicyNeedsFresh(a, b),
    bitterBalance(a, b),
    saltyLikesSourSweet(a, b),
    temperature(a, b),
)

/**
 * Puntuación de 0 a 100 (50 más los puntos de cada principio por su peso) y 1 o 2 frases: las de los principios que
 * más suman; si ninguno suma, la del que más resta. [PairScore.top] es el principio de la primera frase.
 */
fun scorePair(a: PairingItem, b: PairingItem, weights: Map<Principle, Double> = DEFAULT_WEIGHTS): PairScore {
    val weighted = pairContributions(a, b).map { it to it.points * (weights[it.principle] ?: 1.0) }
    val total = BASE_SCORE + weighted.sumOf { it.second }
    val positives = weighted.filter { it.second > 0 && it.first.reason != null }.sortedByDescending { it.second }.take(2)
    val chosen = positives.ifEmpty { listOfNotNull(weighted.filter { it.first.reason != null }.minByOrNull { it.second }) }
    return PairScore(
        score = total.roundToInt().coerceIn(0, 100),
        reasons = chosen.map { it.first.reason!! },
        top = chosen.first().first.principle,
    )
}
