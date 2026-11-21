package app.recetario.pairing

import app.recetario.core.text.normalize
import app.recetario.recipes.model.Course

/**
 * Menú completo: entrada, principal, postre y bebida que combinan entre sí. El principal es siempre una receta propia
 * (con curso «Principal»; si no hay ninguna, una comida sin curso). El resto sale de las recetas propias si las hay y,
 * si falta algo, del [catalog]. Las parejas con 👎 no se juntan y las de 👍 cuentan [LIKED_BONUS] más.
 * Null si no hay ningún plato principal.
 */
fun fullMenu(
    recipes: List<PairingItem>,
    catalog: List<PairingItem>,
    ratings: Map<String, Boolean>,
    weights: Map<Principle, Double> = DEFAULT_WEIGHTS,
): FullMenu? {
    val mains = recipes.filter { it.role == PairRole.FOOD && it.course == Course.MAIN }
        .ifEmpty { recipes.filter { it.role == PairRole.FOOD && it.course == null } }
    if (mains.isEmpty()) return null

    fun liked(a: PairingItem, b: PairingItem) = ratableKey(a, b)?.let { ratings[it] }
    fun allowed(a: PairingItem, b: PairingItem) = liked(a, b) != false
    fun points(a: PairingItem, b: PairingItem) =
        scorePair(a, b, weights).score + if (liked(a, b) == true) LIKED_BONUS else 0

    fun pool(own: (PairingItem) -> Boolean) = recipes.filter(own).ifEmpty { catalog.filter(own) }
    val drinks = pool { it.role == PairRole.DRINK }
    val starters = pool { it.role != PairRole.DRINK && it.role != PairRole.DESSERT && it.course == Course.STARTER }
    val desserts = pool { it.role == PairRole.DESSERT }

    val order = compareBy<Pair<PairingItem, Int>> { -it.second }.thenBy { normalize(it.first.name) }.thenBy { it.first.id }
    fun best(options: List<PairingItem>, value: (PairingItem) -> Int?): Pair<PairingItem, Int>? =
        options.mapNotNull { o -> value(o)?.let { o to it } }.sortedWith(order).firstOrNull()

    val menus = mains.map { main ->
        val drink = best(drinks) { d -> if (allowed(d, main)) points(d, main) else null }
        val d = drink?.first
        val starter = best(starters.filter { !sameItem(it, main) }) { s ->
            if (!allowed(s, main) || (d != null && !allowed(d, s))) null
            else points(s, main) + (d?.let { points(it, s) } ?: 0)
        }
        val dessert = best(desserts) { x ->
            if (d != null) (if (allowed(d, x)) points(d, x) else null) else points(x, main)
        }
        val total = listOfNotNull(drink, starter, dessert).sumOf { it.second }
        FullMenu(starter?.first, main, dessert?.first, d, menuReason(main, d, starter?.first)) to total
    }
    return menus.sortedWith(compareBy<Pair<FullMenu, Int>> { -it.second }.thenBy { normalize(it.first.main.name) })
        .first().first
}

private fun menuReason(main: PairingItem, drink: PairingItem?, starter: PairingItem?): String = when {
    drink != null -> scorePair(drink, main).reasons.first()
    starter != null -> scorePair(starter, main).reasons.first()
    else -> "Un menú alrededor ${de(main)}."
}
