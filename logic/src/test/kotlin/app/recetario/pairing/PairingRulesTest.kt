package app.recetario.pairing

import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairingRulesTest {
    private fun contribution(a: PairingItem, b: PairingItem, p: Principle) =
        pairContributions(a, b).firstOrNull { it.principle == p }?.points ?: 0.0

    @Test
    fun intensity_similarScoresHigher() {
        val dish = item("guiso", intensity = Intensity.INTENSE)
        val strong = item("tinto", PairRole.DRINK, intensity = Intensity.INTENSE)
        val light = item("agua", PairRole.DRINK, intensity = Intensity.LIGHT)
        assertThat(contribution(dish, strong, Principle.INTENSITY)).isGreaterThan(0.0)
        assertThat(contribution(dish, light, Principle.INTENSITY)).isLessThan(0.0)
        assertThat(scorePair(dish, strong).score).isGreaterThan(scorePair(dish, light).score)
    }

    @Test
    fun acid_cutsRich() {
        val mole = item("mole", rich = true, name = "Mole poblano")
        val jamaica = item("jamaica", PairRole.DRINK, sour = 3, name = "Agua de jamaica", article = "el", kind = "CATALOG")
        val flat = item("leche", PairRole.DRINK, rich = true)
        assertThat(contribution(mole, jamaica, Principle.ACID_CUTS_RICH)).isGreaterThan(0.0)
        assertThat(contribution(mole, flat, Principle.ACID_CUTS_RICH)).isLessThan(0.0)
        val score = scorePair(jamaica, mole)
        assertThat(score.reasons).contains("La acidez del agua de jamaica equilibra lo cremoso de mole poblano.")
        assertThat(score.top).isEqualTo(Principle.ACID_CUTS_RICH)
    }

    @Test
    fun acid_cutsDishRichness_notCreamyDrinkOrDessert() {
        val ceviche = item("ceviche", sour = 3, salty = 2, serveTemp = ServeTemp.COLD)
        val rompope = item("rompope", PairRole.DRINK, sweet = 3, rich = true, serveTemp = ServeTemp.COLD)
        val beer = item("cerveza", PairRole.DRINK, bitter = 1, serveTemp = ServeTemp.COLD)
        // Lo ácido del plato no «equilibra» una bebida cremosa: chocan.
        assertThat(contribution(ceviche, rompope, Principle.ACID_CUTS_RICH)).isLessThan(0.0)
        assertThat(scorePair(ceviche, beer).score).isGreaterThan(scorePair(ceviche, rompope).score)
        // Con un postre manda el dulzor: una bebida ácida y seca no se salva por su acidez.
        val flan = item("flan", PairRole.DESSERT, sweet = 3, rich = true, serveTemp = ServeTemp.COLD)
        val michelada = item("michelada", PairRole.DRINK, sour = 3, salty = 2, serveTemp = ServeTemp.COLD)
        assertThat(contribution(flan, michelada, Principle.ACID_CUTS_RICH)).isEqualTo(0.0)
        assertThat(scorePair(flan, michelada).score).isLessThan(MIN_SUGGESTION_SCORE)
    }

    @Test
    fun dessertDrink_mustBeAsSweet() {
        val flan = item("flan", PairRole.DESSERT, sweet = 3)
        val atole = item("atole", PairRole.DRINK, sweet = 3)
        val dry = item("blanco seco", PairRole.DRINK, sweet = 0, sour = 2)
        assertThat(contribution(flan, atole, Principle.DESSERT_DRINK_SWEETER)).isGreaterThan(0.0)
        assertThat(contribution(flan, dry, Principle.DESSERT_DRINK_SWEETER)).isLessThan(0.0)
        // Un café amargo con un postre es un contraste buscado: resta menos y el amargo-dulce lo compensa.
        val coffee = item("café", PairRole.DRINK, bitter = 2, serveTemp = ServeTemp.HOT)
        assertThat(contribution(flan, coffee, Principle.DESSERT_DRINK_SWEETER)).isGreaterThan(contribution(flan, dry, Principle.DESSERT_DRINK_SWEETER))
        assertThat(scorePair(flan, coffee).score).isAtLeast(MIN_SUGGESTION_SCORE)
        // Solo entre postre y bebida.
        assertThat(contribution(item("plato", sweet = 3), dry, Principle.DESSERT_DRINK_SWEETER)).isEqualTo(0.0)
    }

    @Test
    fun spicy_avoidsStrongAlcohol() {
        val tinga = item("tinga", spicy = 3)
        val mezcal = item("mezcal", PairRole.DRINK, alcoholic = true, intensity = Intensity.INTENSE, bitter = 1)
        val horchata = item("horchata", PairRole.DRINK, sweet = 2, alcoholic = false, serveTemp = ServeTemp.COLD)
        assertThat(contribution(tinga, mezcal, Principle.SPICY_NEEDS_FRESH)).isLessThan(0.0)
        assertThat(contribution(tinga, horchata, Principle.SPICY_NEEDS_FRESH)).isGreaterThan(0.0)
        assertThat(scorePair(tinga, horchata).score).isGreaterThan(scorePair(tinga, mezcal).score)
        // Sin picante, el principio no cuenta.
        assertThat(contribution(item("arroz"), mezcal, Principle.SPICY_NEEDS_FRESH)).isEqualTo(0.0)
    }

    @Test
    fun bitter_withBitter_penalised_withSweet_balanced() {
        val coffee = item("café", PairRole.DRINK, bitter = 3)
        val cacao = item("cacao", PairRole.DESSERT, bitter = 3)
        val pan = item("pan dulce", PairRole.DESSERT, sweet = 3)
        assertThat(contribution(coffee, cacao, Principle.BITTER_BALANCE)).isLessThan(0.0)
        assertThat(contribution(coffee, pan, Principle.BITTER_BALANCE)).isGreaterThan(0.0)
    }

    @Test
    fun salty_likesSourOrSweet() {
        val chicharron = item("chicharrón", salty = 3)
        val michelada = item("michelada", PairRole.DRINK, sour = 3, salty = 1)
        val refresco = item("refresco", PairRole.DRINK, sweet = 3)
        val plain = item("agua", PairRole.DRINK)
        assertThat(contribution(chicharron, michelada, Principle.SALTY_LIKES_SOUR_SWEET)).isGreaterThan(0.0)
        assertThat(contribution(chicharron, refresco, Principle.SALTY_LIKES_SOUR_SWEET)).isGreaterThan(0.0)
        assertThat(contribution(chicharron, plain, Principle.SALTY_LIKES_SOUR_SWEET)).isEqualTo(0.0)
        // Lo salado es el del plato, no el escarchado de una bebida.
        assertThat(contribution(item("flan", PairRole.DESSERT, sweet = 3), michelada.copy(flavor = michelada.flavor.copy(salty = 3)), Principle.SALTY_LIKES_SOUR_SWEET)).isEqualTo(0.0)
    }

    @Test
    fun temperature_matchesContext() {
        val ceviche = item("ceviche", serveTemp = ServeTemp.COLD)
        val cold = item("cerveza", PairRole.DRINK, serveTemp = ServeTemp.COLD)
        val hot = item("café de olla", PairRole.DRINK, serveTemp = ServeTemp.HOT)
        assertThat(contribution(ceviche, cold, Principle.TEMPERATURE)).isGreaterThan(0.0)
        assertThat(contribution(ceviche, hot, Principle.TEMPERATURE)).isLessThan(0.0)
        val pozole = item("pozole", serveTemp = ServeTemp.HOT)
        assertThat(contribution(pozole, cold, Principle.TEMPERATURE)).isGreaterThan(0.0)
        assertThat(contribution(pozole, item("sin dato", PairRole.DRINK), Principle.TEMPERATURE)).isEqualTo(0.0)
    }

    @Test
    fun score_inRange0to100() {
        val extremes = listOf(
            item("a", spicy = 3, salty = 3, bitter = 3, intensity = Intensity.INTENSE, rich = true, serveTemp = ServeTemp.COLD),
            item("b", PairRole.DRINK, bitter = 3, intensity = Intensity.LIGHT, alcoholic = true, serveTemp = ServeTemp.HOT, rich = true),
            item("c", PairRole.DESSERT, sweet = 3, rich = true, serveTemp = ServeTemp.COLD),
            item("d", PairRole.DRINK, sweet = 3, sour = 3, serveTemp = ServeTemp.COLD, intensity = Intensity.MEDIUM),
            item("e"),
        )
        for (a in extremes) for (b in extremes) {
            val s = scorePair(a, b).score
            assertThat(s).isAtLeast(0)
            assertThat(s).isAtMost(100)
        }
        // Con pesos al máximo tampoco se sale.
        val heavy = Principle.entries.associateWith { 1.2 }
        assertThat(scorePair(extremes[2], extremes[3], heavy).score).isAtMost(100)
        assertThat(scorePair(extremes[0], extremes[1], heavy).score).isAtLeast(0)
    }

    @Test
    fun reasons_oneOrTwo_spanish() {
        val catalog = Catalog.load()
        val recipes = listOf(
            item("mole", rich = true, sweet = 1, spicy = 2, intensity = Intensity.INTENSE, name = "Mole poblano"),
            item("flan", PairRole.DESSERT, sweet = 3, rich = true, name = "Flan napolitano", serveTemp = ServeTemp.COLD),
            item("ensalada", sour = 2, intensity = Intensity.LIGHT, name = "Ensalada de nopales", serveTemp = ServeTemp.COLD),
        )
        for (a in recipes + catalog) for (b in recipes + catalog) {
            if (a == b) continue
            val score = scorePair(a, b)
            assertThat(score.reasons.size).isIn(1..2)
            for (r in score.reasons) {
                assertThat(r).matches("[A-ZÁÉÍÓÚÑ¿¡].*\\.")
                assertThat(r).doesNotContain("{")
                assertThat(r).doesNotContain("  ")
                assertThat(r).doesNotContainMatch("\\b(de|a) el ")
                assertThat(r.length).isLessThan(160)
            }
        }
        assertThat(scorePair(recipes[0], recipes[1]).reasons.joinToString(" "))
            .containsMatch("mole poblano|flan napolitano")
    }
}
