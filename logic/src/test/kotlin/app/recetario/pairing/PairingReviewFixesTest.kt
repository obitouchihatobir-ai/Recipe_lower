package app.recetario.pairing

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Arreglos de la revisión de la Task 2 (concordancia, frases, menú completo y valoraciones). */
class PairingReviewFixesTest {
    private fun contribution(a: PairingItem, b: PairingItem, p: Principle) =
        pairContributions(a, b).firstOrNull { it.principle == p }

    @Test
    fun reasons_genderNeutral_overWholeCatalog() {
        val catalog = Catalog.load()
        val recipes = listOf(
            item("ensalada", sour = 2, intensity = Intensity.LIGHT, name = "Ensalada verde", serveTemp = ServeTemp.COLD),
            item("sopa", intensity = Intensity.MEDIUM, name = "Sopa de lima", serveTemp = ServeTemp.HOT),
        )
        val all = recipes + catalog
        for (a in all) for (b in all) {
            if (a == b) continue
            for (r in pairContributions(a, b).mapNotNull { it.reason }) {
                assertThat(r).doesNotContainMatch("\\bfríos\\b")
                assertThat(r).doesNotContainMatch("\\bfrías\\b")
                assertThat(r).doesNotContainMatch("\\bninguno\\b")
                assertThat(r).doesNotContainMatch("\\bninguna\\b")
            }
        }
        val limonada = catalog.first { it.id == "cat-limonada" }
        assertThat(contribution(limonada, recipes[0], Principle.TEMPERATURE)!!.reason).contains("se sirven en frío")
        val same = contribution(item("a", intensity = Intensity.LIGHT), item("b", PairRole.DRINK, intensity = Intensity.LIGHT), Principle.INTENSITY)!!
        assertThat(same.reason).contains("sin que uno tape al otro")
    }

    @Test
    fun spicyWithSpicy_verbAgreesInPlural() {
        val r = contribution(item("tinga", spicy = 3), item("michelada", PairRole.DRINK, spicy = 2), Principle.SPICY_NEEDS_FRESH)!!.reason!!
        assertThat(r).contains("se suman")
        assertThat(r).doesNotContain("se suma ")
    }

    @Test
    fun dessertDrinkNegative_namesOnlyWhatTheDrinkHas() {
        val flan = item("flan", PairRole.DESSERT, sweet = 3)
        val sourOnly = contribution(flan, item("blanco", PairRole.DRINK, sour = 2), Principle.DESSERT_DRINK_SWEETER)!!.reason!!
        assertThat(sourOnly).contains("acidez")
        assertThat(sourOnly).doesNotContain("amargor")
        val bitterOnly = contribution(flan, item("oscura", PairRole.DRINK, bitter = 1), Principle.DESSERT_DRINK_SWEETER)!!.reason!!
        assertThat(bitterOnly).contains("amargor")
        assertThat(bitterOnly).doesNotContain("acidez")
        val neither = contribution(flan, item("agua", PairRole.DRINK), Principle.DESSERT_DRINK_SWEETER)!!.reason!!
        assertThat(neither).doesNotContain("amargor")
        assertThat(neither).doesNotContain("acidez")
        assertThat(neither).matches("[A-ZÁÉÍÓÚÑ].*\\.")
        val both = contribution(flan, item("tónica", PairRole.DRINK, sour = 1, bitter = 2), Principle.DESSERT_DRINK_SWEETER)!!.reason!!
        assertThat(both).contains("la acidez y el amargor")
    }

    @Test
    fun saltyDish_hotRichSweetDrink_isNotAContrast() {
        val catalog = Catalog.load()
        val pozole = item("pozole", salty = 2, intensity = Intensity.MEDIUM, serveTemp = ServeTemp.HOT, course = Course.MAIN, name = "Pozole")
        for (id in listOf("cat-chocolate-caliente", "cat-champurrado", "cat-atole")) {
            val drink = catalog.first { it.id == id }
            val c = contribution(pozole, drink, Principle.SALTY_LIKES_SOUR_SWEET)
            assertThat((c?.points ?: 0.0)).isAtMost(0.0)
        }
        // Una bebida fría y dulce sí hace el contraste.
        val refresco = item("refresco", PairRole.DRINK, sweet = 3, serveTemp = ServeTemp.COLD)
        assertThat(contribution(pozole, refresco, Principle.SALTY_LIKES_SOUR_SWEET)!!.points).isGreaterThan(0.0)
    }

    @Test
    fun suggestion_ratableOnlyWithARecipe() {
        val catDrink = item("cat-jamaica", PairRole.DRINK, sour = 3, sweet = 2, serveTemp = ServeTemp.COLD, kind = "CATALOG")
        val catFood = item("cat-tacos", salty = 2, sour = 1, serveTemp = ServeTemp.HOT, kind = "CATALOG")
        val ownFood = item("tacos", salty = 2, sour = 1, serveTemp = ServeTemp.HOT)
        val result = suggestPairings(catDrink, listOf(catFood, ownFood), emptyMap(), 5)
        assertThat(result.first { it.item.id == "tacos" }.ratable).isTrue()
        assertThat(result.first { it.item.id == "cat-tacos" }.ratable).isFalse()
        assertThat(ratableKey(catDrink, catFood)).isNull()
        assertThat(ratableKey(catDrink, ownFood)).isEqualTo(pairKey(catDrink, ownFood))
    }

    private val main = item("mole", rich = true, spicy = 1, intensity = Intensity.INTENSE, course = Course.MAIN, serveTemp = ServeTemp.HOT)

    @Test
    fun fullMenu_allOwnDislikedWithMain_fallsBackToCatalog() {
        val drink = item("jamaica-casa", PairRole.DRINK, sour = 3, sweet = 2, serveTemp = ServeTemp.COLD, alcoholic = false)
        val starter = item("sopa", salty = 1, course = Course.STARTER, serveTemp = ServeTemp.HOT)
        val dessert = item("flan", PairRole.DESSERT, sweet = 3, rich = true, serveTemp = ServeTemp.COLD)
        val catalog = Catalog.load()
        val menu = fullMenu(
            listOf(main, drink, starter, dessert),
            catalog,
            mapOf(pairKey(main, drink) to false, pairKey(main, starter) to false),
        )!!
        assertThat(menu.drink!!.kind).isEqualTo("CATALOG")
        assertThat(menu.starter!!.kind).isEqualTo("CATALOG")
        // El postre propio con 👎 con la bebida elegida también se sustituye.
        val withDessertDisliked = fullMenu(
            listOf(main, drink, dessert),
            catalog,
            mapOf(pairKey(drink, dessert) to false),
        )!!
        assertThat(withDessertDisliked.drink).isEqualTo(drink)
        assertThat(withDessertDisliked.dessert!!.kind).isEqualTo("CATALOG")
    }

    @Test
    fun fullMenu_reason_isFirstPositive_withWeights() {
        // Bebida ligera con un principal intenso: solo frases negativas; la entrada sí tiene una positiva.
        val heavyMain = item("guiso", intensity = Intensity.INTENSE, course = Course.MAIN, name = "Guiso")
        val lightDrink = item("agua", PairRole.DRINK, intensity = Intensity.LIGHT, name = "Agua")
        val starter = item("caldo", intensity = Intensity.INTENSE, course = Course.STARTER, name = "Caldo")
        val menu = fullMenu(listOf(heavyMain, lightDrink, starter), emptyList(), emptyMap())!!
        assertThat(menu.drink).isEqualTo(lightDrink)
        assertThat(menu.reason).isEqualTo(scorePair(starter, heavyMain).reasons.first())
        assertThat(menu.reason).doesNotContain("tapar")

        // Con pesos, la frase es la que más pesa con esos pesos.
        val hotMain = item("pozole", intensity = Intensity.MEDIUM, course = Course.MAIN, serveTemp = ServeTemp.HOT, name = "Pozole")
        val hotDrink = item("café", PairRole.DRINK, intensity = Intensity.MEDIUM, serveTemp = ServeTemp.HOT, name = "Café")
        val weights = DEFAULT_WEIGHTS + (Principle.INTENSITY to 0.1)
        val weighted = fullMenu(listOf(hotMain, hotDrink), emptyList(), emptyMap(), weights)!!
        assertThat(weighted.reason).contains("se sirven")
    }
}
