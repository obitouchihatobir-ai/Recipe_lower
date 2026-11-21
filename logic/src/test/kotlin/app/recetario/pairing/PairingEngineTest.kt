package app.recetario.pairing

import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairingEngineTest {
    private val tacos = item("tacos", salty = 2, spicy = 2, intensity = Intensity.MEDIUM, serveTemp = ServeTemp.HOT)
    private val ownDrink = item("limonada-casa", PairRole.DRINK, sour = 2, sweet = 2, serveTemp = ServeTemp.COLD, alcoholic = false)
    private val catDrink = item("cat-jamaica", PairRole.DRINK, sour = 3, sweet = 2, serveTemp = ServeTemp.COLD, alcoholic = false, kind = "CATALOG")
    private val catBeer = item("cat-cerveza", PairRole.DRINK, bitter = 1, serveTemp = ServeTemp.COLD, alcoholic = true, kind = "CATALOG")

    @Test
    fun suggest_ownRecipesFirst() {
        val result = suggestPairings(tacos, listOf(catDrink, catBeer, ownDrink), emptyMap(), limit = 3)
        assertThat(result.map { it.item.id }.first()).isEqualTo("limonada-casa")
        assertThat(result.drop(1).map { it.item.kind }).containsExactly("CATALOG", "CATALOG")
        // Dentro del catálogo, de mayor a menor.
        assertThat(result[1].score).isAtLeast(result[2].score)
        assertThat(result.all { it.reasons.size in 1..2 }).isTrue()
        assertThat(suggestPairings(tacos, listOf(catDrink, catBeer, ownDrink), emptyMap(), limit = 1)).hasSize(1)
    }

    @Test
    fun suggest_dislikedExcluded() {
        val key = pairKey(tacos, catDrink)
        val result = suggestPairings(tacos, listOf(catDrink, catBeer), mapOf(key to false), limit = 5)
        assertThat(result.map { it.item.id }).doesNotContain("cat-jamaica")
        // En los dos sentidos: partiendo de la bebida tampoco sale el plato.
        assertThat(suggestPairings(catDrink, listOf(tacos), mapOf(key to false), limit = 5)).isEmpty()
    }

    @Test
    fun suggest_likedBoosted15() {
        val plain = suggestPairings(tacos, listOf(catBeer), emptyMap(), limit = 5).single()
        val liked = suggestPairings(tacos, listOf(catBeer), mapOf(pairKey(tacos, catBeer) to true), limit = 5).single()
        assertThat(liked.score).isEqualTo(minOf(100, plain.score + 15))
        assertThat(liked.liked).isTrue()
        assertThat(plain.liked).isNull()
        val great = suggestPairings(tacos, listOf(catDrink), mapOf(pairKey(tacos, catDrink) to true), limit = 5).single()
        assertThat(great.score).isAtMost(100)
    }

    @Test
    fun suggest_onlySensibleRoles() {
        val dessert = item("flan", PairRole.DESSERT, sweet = 3)
        val side = item("arroz", PairRole.SIDE)
        val otherFood = item("pozole")
        val all = listOf(tacos, ownDrink, catDrink, dessert, side, otherFood)
        // Plato → solo bebidas.
        assertThat(suggestPairings(tacos, all, emptyMap(), 10).map { it.item.role }.toSet()).containsExactly(PairRole.DRINK)
        // Postre → solo bebidas.
        assertThat(suggestPairings(dessert, all, emptyMap(), 10).map { it.item.role }.toSet()).containsExactly(PairRole.DRINK)
        // Bebida → comida, acompañamientos y postres, nunca otra bebida.
        val fromDrink = suggestPairings(ownDrink, all, emptyMap(), 10).map { it.item.role }.toSet()
        assertThat(fromDrink).doesNotContain(PairRole.DRINK)
        assertThat(fromDrink).contains(PairRole.FOOD)
        // Nunca consigo mismo.
        assertThat(suggestPairings(tacos, all, emptyMap(), 10).map { it.item.id }).doesNotContain("tacos")
    }

    @Test
    fun learnedWeights_boundedPoint8to1Point2() {
        val mole = item("mole", rich = true)
        val jamaica = item("jamaica", PairRole.DRINK, sour = 3, kind = "CATALOG")
        assertThat(scorePair(mole, jamaica).top).isEqualTo(Principle.ACID_CUTS_RICH)
        assertThat(learnedWeights(emptyList())).isEqualTo(DEFAULT_WEIGHTS)
        assertThat(DEFAULT_WEIGHTS.keys).containsExactlyElementsIn(Principle.entries)

        val oneLike = learnedWeights(listOf(RatedPair(mole, jamaica, liked = true)))
        assertThat(oneLike.getValue(Principle.ACID_CUTS_RICH)).isWithin(1e-9).of(1.02)
        assertThat(oneLike.getValue(Principle.INTENSITY)).isWithin(1e-9).of(1.0)

        val manyLikes = learnedWeights(List(50) { RatedPair(mole, jamaica, liked = true) })
        assertThat(manyLikes.getValue(Principle.ACID_CUTS_RICH)).isWithin(1e-9).of(1.2)
        val manyDislikes = learnedWeights(List(50) { RatedPair(jamaica, mole, liked = false) })
        assertThat(manyDislikes.getValue(Principle.ACID_CUTS_RICH)).isWithin(1e-9).of(0.8)
        val mixed = learnedWeights(listOf(true, true, false).map { RatedPair(mole, jamaica, it) })
        assertThat(mixed.getValue(Principle.ACID_CUTS_RICH)).isWithin(1e-9).of(1.02)
    }
}
