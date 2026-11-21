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

    // Cada hueco: las recetas propias que se pueden juntar; si todas tienen 👎 (o no hay), el catálogo.
    fun pool(fits: (PairingItem) -> Boolean, ok: (PairingItem) -> Boolean) =
        recipes.filter { fits(it) && ok(it) }.ifEmpty { catalog.filter { fits(it) && ok(it) } }
    val isDrink = { it: PairingItem -> it.role == PairRole.DRINK }
    val isStarter = { it: PairingItem -> it.role != PairRole.DRINK && it.role != PairRole.DESSERT && it.course == Course.STARTER }
    val isDessert = { it: PairingItem -> it.role == PairRole.DESSERT }

    val order = compareBy<Pair<PairingItem, Int>> { -it.second }.thenBy { normalize(it.first.name) }.thenBy { it.first.id }
    fun best(options: List<PairingItem>, value: (PairingItem) -> Int): Pair<PairingItem, Int>? =
        options.map { o -> o to value(o) }.sortedWith(order).firstOrNull()

    val menus = mains.map { main ->
        val drink = best(pool(isDrink) { allowed(it, main) }) { points(it, main) }
        val d = drink?.first
        val starter = best(pool(isStarter) { !sameItem(it, main) && allowed(it, main) && (d == null || allowed(d, it)) }) { s ->
            points(s, main) + (d?.let { points(it, s) } ?: 0)
        }
        val dessert = best(pool(isDessert) { if (d != null) allowed(d, it) else allowed(it, main) }) { x ->
            if (d != null) points(d, x) else points(x, main)
        }
        val total = listOfNotNull(drink, starter, dessert).sumOf { it.second }
        FullMenu(starter?.first, main, dessert?.first, d, menuReason(main, d, starter?.first, dessert?.first, weights)) to total
    }
    return menus.sortedWith(compareBy<Pair<FullMenu, Int>> { -it.second }.thenBy { normalize(it.first.main.name) })
        .first().first
}

/** La primera frase positiva (con [weights]) de bebida–principal, entrada–principal o postre–bebida. */
private fun menuReason(
    main: PairingItem,
    drink: PairingItem?,
    starter: PairingItem?,
    dessert: PairingItem?,
    weights: Map<Principle, Double>,
): String = listOfNotNull(
    drink?.let { it to main },
    starter?.let { it to main },
    if (dessert != null && drink != null) dessert to drink else null,
).firstNotNullOfOrNull { (a, b) -> positiveReasons(a, b, weights).firstOrNull() }
    ?: "Un menú alrededor ${de(main)}."
