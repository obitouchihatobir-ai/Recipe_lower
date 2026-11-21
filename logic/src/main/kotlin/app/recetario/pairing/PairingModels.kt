package app.recetario.pairing

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.ServeTemp

/** Papel de un elemento en una pareja. */
enum class PairRole { DRINK, FOOD, DESSERT, SIDE }

/**
 * Una receta propia ([kind] `RECIPE`) o un elemento del catálogo ([kind] `CATALOG`), con lo que el motor necesita.
 * [article] («el», «la», «los», «las») solo lo tienen los del catálogo: sirve para escribir «del agua de jamaica»
 * en las frases. Sin artículo, el nombre va tal cual, con la inicial en minúscula («de mole poblano»).
 */
data class PairingItem(
    val kind: String,
    val id: String,
    val name: String,
    val role: PairRole,
    val course: Course?,
    val alcoholic: Boolean?,
    val flavor: FlavorProfile,
    val serveTemp: ServeTemp?,
    val article: String? = null,
)

/** Los 7 principios del diseño general §8.7. */
enum class Principle {
    INTENSITY,
    ACID_CUTS_RICH,
    DESSERT_DRINK_SWEETER,
    SPICY_NEEDS_FRESH,
    BITTER_BALANCE,
    SALTY_LIKES_SOUR_SWEET,
    TEMPERATURE,
}

/** Lo que un principio aporta a una pareja (sin pesos) y la frase que lo explica. */
data class Contribution(val principle: Principle, val points: Double, val reason: String?)

/** [score] de 0 a 100; [reasons], 1 o 2 frases; [top], el principio de la primera frase (el que más pesó). */
data class PairScore(val score: Int, val reasons: List<String>, val top: Principle)

/** Una pareja valorada con 👍 ([liked] true) o 👎. */
data class RatedPair(val a: PairingItem, val b: PairingItem, val liked: Boolean)

/**
 * [liked] es true si la pareja tiene 👍 (las 👎 no se sugieren). [ratable] es false si los dos lados son del catálogo:
 * esa pareja no se puede valorar (la interfaz no muestra 👍/👎).
 */
data class Suggestion(val item: PairingItem, val score: Int, val reasons: List<String>, val liked: Boolean?, val ratable: Boolean)

data class FullMenu(
    val starter: PairingItem?,
    val main: PairingItem,
    val dessert: PairingItem?,
    val drink: PairingItem?,
    val reason: String,
)

/** Pesos de partida: todos los principios cuentan igual. */
val DEFAULT_WEIGHTS: Map<Principle, Double> = Principle.entries.associateWith { 1.0 }
