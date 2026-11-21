package app.recetario.today

import app.recetario.diary.CookStats
import app.recetario.pairing.PairRole
import app.recetario.pairing.scorePair
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class TodayEngineTest {
    private val soup = recipe("sopa", serveTemp = ServeTemp.HOT, rich = true, salty = 2, minutes = 30)
    private val salad = recipe("ensalada", serveTemp = ServeTemp.COLD, sour = 2, intensity = Intensity.LIGHT, minutes = 15)

    /** Comprueba con 30 semillas: el azar no debe cambiar una preferencia clara. */
    private fun forSeeds(block: (Random) -> Unit) = repeat(30) { block(Random(it)) }

    private fun mainOf(c: TodayContext, i: TodayInputs, r: Random) = planToday(c, i, emptySet(), r).main?.id

    @Test
    fun cold_prefersHot() = forSeeds { r ->
        assertThat(mainOf(ctx(thermal = Thermal.COLD, apparentC = 9.0), inputs(salad, soup), r)).isEqualTo("sopa")
    }

    @Test
    fun hot_prefersCold() = forSeeds { r ->
        assertThat(mainOf(ctx(thermal = Thermal.HOT, apparentC = 31.0), inputs(soup, salad), r)).isEqualTo("ensalada")
    }

    @Test
    fun noWeather_rainySeason_prefersHot() = forSeeds { r ->
        assertThat(mainOf(ctx(season = Season.RAINY), inputs(salad, soup), r)).isEqualTo("sopa")
    }

    @Test
    fun weekday_prefersQuick_weekendAllowsLong() {
        val quick = recipe("tacos", minutes = 25)
        val long = recipe("mole", minutes = 180, favorite = true)
        forSeeds { r ->
            assertThat(mainOf(ctx(now = WEDNESDAY, moment = Moment.DINNER), inputs(long, quick), r)).isEqualTo("tacos")
            assertThat(mainOf(ctx(now = SATURDAY, moment = Moment.DINNER), inputs(quick, long), r)).isEqualTo("mole")
        }
    }

    @Test
    fun recentlyCooked_penalised() {
        val a = recipe("a", favorite = true)
        val b = recipe("b")
        val today = WEDNESDAY.toLocalDate()
        val i = inputs(a, b).copy(stats = mapOf("a" to CookStats(4, today.minusDays(1), 5.0)))
        forSeeds { r -> assertThat(mainOf(ctx(), i, r)).isEqualTo("b") }
    }

    @Test
    fun longAgo_boosted() {
        val a = recipe("a")
        val b = recipe("b")
        val today = WEDNESDAY.toLocalDate()
        val i = inputs(a, b).copy(stats = mapOf("b" to CookStats(2, today.minusDays(60), null)))
        forSeeds { r -> assertThat(mainOf(ctx(), i, r)).isEqualTo("b") }
    }

    @Test
    fun ratingsAndFavorites_add() {
        val today = WEDNESDAY.toLocalDate()
        val i = inputs(recipe("a"), recipe("b")).copy(stats = mapOf("b" to CookStats(1, today.minusDays(10), 5.0)))
        forSeeds { r -> assertThat(mainOf(ctx(), i, r)).isEqualTo("b") }
        forSeeds { r -> assertThat(mainOf(ctx(), inputs(recipe("a"), recipe("b", favorite = true)), r)).isEqualTo("b") }
    }

    @Test
    fun pantryCoverage_adds() {
        val a = recipe("a", ingredients = listOf("Pollo", "Chile ancho", "Sal"))
        val b = recipe("b", ingredients = listOf("Res", "Papas", "Sal"))
        val i = inputs(a, b).copy(pantry = listOf("pollo", "chiles anchos"), basics = listOf("sal"))
        forSeeds { r -> assertThat(mainOf(ctx(), i, r)).isEqualTo("a") }
    }

    @Test
    fun inSeasonBonus_adds() {
        val i = inputs(recipe("a"), recipe("b")).copy(inSeasonBonus = { if (it.id == "b") 30.0 else 0.0 })
        forSeeds { r -> assertThat(mainOf(ctx(), i, r)).isEqualTo("b") }
    }

    @Test
    fun moment_mustFit_orDeducedFromType() {
        val breakfastOnly = recipe("chilaquiles", moments = setOf(Moment.BREAKFAST), favorite = true)
        val untagged = recipe("guisado")
        val starter = recipe("sopa-entrada", course = Course.STARTER, favorite = true)
        forSeeds { r ->
            assertThat(mainOf(ctx(moment = Moment.LUNCH), inputs(breakfastOnly, untagged, starter), r)).isEqualTo("guisado")
            assertThat(mainOf(ctx(moment = Moment.BREAKFAST), inputs(breakfastOnly, untagged), r)).isEqualTo("chilaquiles")
        }
    }

    @Test
    fun moment_nothingFits_showsWhatThereIs() {
        val breakfastOnly = recipe("chilaquiles", moments = setOf(Moment.BREAKFAST))
        assertThat(mainOf(ctx(moment = Moment.DINNER), inputs(breakfastOnly), Random(1))).isEqualTo("chilaquiles")
    }

    @Test
    fun lunch_hasMainDrinkDessert_thatPair() {
        val mole = recipe("mole", course = Course.MAIN, rich = true, spicy = 2, intensity = Intensity.INTENSE, serveTemp = ServeTemp.HOT)
        val jamaica = recipe("jamaica", PairRole.DRINK, sour = 2, sweet = 2, serveTemp = ServeTemp.COLD, alcoholic = false)
        val mezcal = recipe("mezcal", PairRole.DRINK, bitter = 2, intensity = Intensity.INTENSE, alcoholic = true)
        val flan = recipe("flan", PairRole.DESSERT, sweet = 3, rich = true, serveTemp = ServeTemp.COLD)
        val menu = planToday(ctx(), inputs(jamaica, mezcal, flan, mole), emptySet(), Random(3))
        assertThat(menu.moment).isEqualTo(Moment.LUNCH)
        assertThat(menu.main).isEqualTo(mole)
        val best = listOf(jamaica, mezcal).maxBy { scorePair(it.asPairingItem(), mole.asPairingItem()).score }
        assertThat(menu.drink).isEqualTo(best)
        assertThat(menu.dessert).isEqualTo(flan)
        assertThat(menu.snack).isNull()
        assertThat(menu.reason).isNotEmpty()
    }

    @Test
    fun breakfast_plateAndDrink() {
        val eggs = recipe("huevos", moments = setOf(Moment.BREAKFAST), serveTemp = ServeTemp.HOT)
        val coffee = recipe("cafe", PairRole.DRINK, bitter = 2, serveTemp = ServeTemp.HOT, alcoholic = false)
        val flan = recipe("flan", PairRole.DESSERT, sweet = 3)
        val menu = planToday(ctx(moment = Moment.BREAKFAST), inputs(eggs, coffee, flan), emptySet(), Random(1))
        assertThat(menu.main).isEqualTo(eggs)
        assertThat(menu.drink).isEqualTo(coffee)
        assertThat(menu.dessert).isNull()
        assertThat(menu.snack).isNull()
    }

    @Test
    fun snackTime_plateAndDrink() {
        val bread = recipe("conchas", PairRole.DESSERT, sweet = 2)
        val chocolate = recipe("chocolate", PairRole.DRINK, sweet = 2, rich = true, serveTemp = ServeTemp.HOT)
        val menu = planToday(ctx(moment = Moment.SNACK), inputs(bread, chocolate), emptySet(), Random(1))
        assertThat(menu.main).isEqualTo(bread)
        assertThat(menu.drink).isEqualTo(chocolate)
        assertThat(menu.dessert).isNull()
    }

    @Test
    fun night_drinkAndSnack() {
        val wine = recipe("vino", PairRole.DRINK, sour = 2, alcoholic = true)
        val olives = recipe("aceitunas", PairRole.SIDE, salty = 2)
        val stew = recipe("guisado", course = Course.MAIN)
        val menu = planToday(ctx(moment = Moment.NIGHT), inputs(stew, olives, wine), emptySet(), Random(1))
        assertThat(menu.main).isNull()
        assertThat(menu.drink).isEqualTo(wine)
        assertThat(menu.snack).isEqualTo(olives)
        assertThat(menu.dessert).isNull()
    }

    @Test
    fun today_noDessert_leavesSlotEmpty() {
        val menu = planToday(ctx(), inputs(soup, recipe("agua", PairRole.DRINK)), emptySet(), Random(1))
        assertThat(menu.main).isEqualTo(soup)
        assertThat(menu.drink!!.id).isEqualTo("agua")
        assertThat(menu.dessert).isNull()
    }

    @Test
    fun today_onlyDrinks_drinkLeads() {
        val menu = planToday(ctx(), inputs(recipe("agua", PairRole.DRINK)), emptySet(), Random(1))
        assertThat(menu.main).isNull()
        assertThat(menu.drink!!.id).isEqualTo("agua")
        assertThat(menu.lead!!.id).isEqualTo("agua")
    }

    @Test
    fun today_noRecipes_allNull() {
        for (m in Moment.entries) {
            val menu = planToday(ctx(moment = m), inputs(), emptySet(), Random(1))
            assertThat(menu.moment).isEqualTo(m)
            assertThat(listOf(menu.main, menu.drink, menu.dessert, menu.snack)).containsExactly(null, null, null, null)
            assertThat(menu.reason).isNotEmpty()
            assertThat(menu.nextExclude(setOf("x"))).containsExactly("x")
        }
    }

    @Test
    fun anotherIdea_cyclesWithoutRepeats() {
        val i = inputs(recipe("a"), recipe("b"), recipe("c"), recipe("agua", PairRole.DRINK))
        val random = Random(7)
        var shown = emptySet<String>()
        val picks = (1..6).map {
            val menu = planToday(ctx(), i, shown, random)
            shown = menu.nextExclude(shown)
            menu.main!!.id
        }
        assertThat(picks.take(3)).containsExactly("a", "b", "c")
        assertThat(picks.drop(3)).containsExactly("a", "b", "c")
        assertThat(shown).hasSize(3)
    }

    @Test
    fun anotherIdea_nightCyclesTheDrink() {
        val i = inputs(recipe("vino", PairRole.DRINK), recipe("cerveza", PairRole.DRINK), recipe("cacahuates", PairRole.SIDE))
        var shown = emptySet<String>()
        val picks = (1..4).map {
            val menu = planToday(ctx(moment = Moment.NIGHT), i, shown, Random(it))
            shown = menu.nextExclude(shown)
            assertThat(menu.snack!!.id).isEqualTo("cacahuates")
            menu.drink!!.id
        }
        assertThat(picks.take(2)).containsExactly("vino", "cerveza")
        assertThat(picks.drop(2)).containsExactly("vino", "cerveza")
    }

    @Test
    fun deterministic_withSeed() {
        val i = inputs(*(1..8).map { recipe("r$it", minutes = it * 10) }.toTypedArray(), recipe("agua", PairRole.DRINK))
        val first = planToday(ctx(), i, emptySet(), Random(42))
        repeat(5) { assertThat(planToday(ctx(), i, emptySet(), Random(42))).isEqualTo(first) }
        val mains = (0 until 40).map { planToday(ctx(), i, emptySet(), Random(it)).main!!.id }.toSet()
        assertThat(mains.size).isGreaterThan(1)
    }
}
