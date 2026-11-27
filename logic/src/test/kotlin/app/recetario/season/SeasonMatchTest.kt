package app.recetario.season

import app.recetario.pairing.PairRole
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.RecipeSummary
import app.recetario.today.TodayRecipe
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import org.junit.Test

class SeasonMatchTest {
    private fun p(id: String, name: String, vararg synonyms: String, months: String = "BBBBBBBBBBBB") = Produce(
        id = id, name = name, emoji = "🌿", kind = ProduceKind.VEGETABLE, synonyms = synonyms.toList(),
        months = months.map { if (it == 'B') Availability.BEST else if (it == 'A') Availability.AVAILABLE else Availability.OUT },
    )

    private val elote = p("elote", "Elote", "mazorca")
    private val jitomate = p("jitomate", "Jitomate", "tomate", "tomate rojo")
    private val tomateVerde = p("tomate-verde", "Tomate verde", "tomatillo", "tomate de cáscara")
    private val calabaza = p("calabaza", "Calabaza", "calabacita")
    private val flor = p("flor-de-calabaza", "Flor de calabaza")
    private val cana = p("cana", "Caña", "caña de azúcar")
    private val poblano = p("chile-poblano", "Chile poblano", "ají poblano")
    private val small = listOf(elote, jitomate, tomateVerde, calabaza, flor, cana, poblano)

    private fun summary(id: String, vararg ingredients: String) = RecipeSummary(
        id, id, "cat", null, 30, FlavorProfile(), null, emptySet(), null,
        Difficulty.EASY, false, emptyList(), ingredients.toList(), Instant.EPOCH,
    )

    private fun today(vararg ingredients: String) = TodayRecipe(
        id = "r", title = "r", role = PairRole.FOOD, course = null, flavor = FlavorProfile(), serveTemp = null,
        moments = emptySet(), totalMinutes = null, favorite = false, alcoholic = null,
        ingredientNames = ingredients.toList(), coverPhoto = null,
    )

    @Test
    fun match_synonyms() {
        assertThat(producesIn(listOf("Mazorcas"), small)).containsExactly("elote")
        assertThat(producesIn(listOf("2 elotes tiernos"), small)).containsExactly("elote")
        assertThat(producesIn(listOf("Tomates maduros"), small)).containsExactly("jitomate")
        assertThat(producesIn(listOf("jitomate"), small)).containsExactly("jitomate")
        assertThat(producesIn(listOf("Chiles poblanos asados"), small)).containsExactly("chile-poblano")
        assertThat(producesIn(listOf("ají poblano"), small)).containsExactly("chile-poblano")
        assertThat(producesIn(listOf("calabacitas"), small)).containsExactly("calabaza")
    }

    @Test
    fun match_moreSpecificWins() {
        // «Tomate verde» es el tomate verde, no el jitomate; «flor de calabaza», la flor y no la calabaza.
        assertThat(producesIn(listOf("tomate verde"), small)).containsExactly("tomate-verde")
        assertThat(producesIn(listOf("Tomates de cáscara"), small)).containsExactly("tomate-verde")
        assertThat(producesIn(listOf("flores de calabaza"), small)).containsExactly("flor-de-calabaza")
        // Si la receta lleva los dos, salen los dos.
        assertThat(producesIn(listOf("tomate verde", "jitomate", "calabaza"), small))
            .containsExactly("tomate-verde", "jitomate", "calabaza")
    }

    @Test
    fun match_wholeWords() {
        assertThat(producesIn(listOf("cañada"), small)).isEmpty()
        assertThat(producesIn(listOf("caña"), small)).containsExactly("cana")
        assertThat(producesIn(listOf("maizena"), small)).isEmpty()
        assertThat(producesIn(listOf("chile"), small)).isEmpty()
        assertThat(producesIn(listOf("", "  "), small)).isEmpty()
    }

    @Test
    fun match_realCalendar() {
        val cal = SeasonCalendar.load()
        assertThat(producesIn(listOf("Elote desgranado", "Jitomate", "Tomate verde", "Chile jalapeño"), cal))
            .containsExactly("elote", "jitomate", "tomate-verde", "chile-jalapeno")
        assertThat(producesIn(listOf("Sal", "Aceite", "Harina"), cal)).isEmpty()
    }

    @Test
    fun match_looseWords_realCalendar() {
        val cal = SeasonCalendar.load()
        // El maíz seco, la harina o las tortillas no son elote.
        for (i in listOf("Harina de maíz", "Tortillas de maíz", "maíz pozolero", "Fécula de maíz")) {
            assertWithMessage(i).that(producesIn(listOf(i), cal)).isEmpty()
        }
        // Productos que no son el fresco de temporada.
        for (i in listOf(
            "Leche de coco", "Crema de coco", "Uvas pasas", "pasas", "Moras azules", "mora azul", "Col morada",
            "Coles de Bruselas", "Pimienta", "pimienta negra molida", "Pimienta gorda",
        )) assertWithMessage(i).that(producesIn(listOf(i), cal)).isEmpty()
        // Lo fresco sigue contando.
        assertThat(producesIn(listOf("Coco rallado"), cal)).containsExactly("coco")
        assertThat(producesIn(listOf("Uvas verdes"), cal)).containsExactly("uva")
        assertThat(producesIn(listOf("Moras"), cal)).containsExactly("zarzamora")
        assertThat(producesIn(listOf("Col blanca"), cal)).containsExactly("col")
        assertThat(producesIn(listOf("Pimiento rojo"), cal)).containsExactly("pimiento-morron")
        assertThat(producesIn(listOf("Elote"), cal)).containsExactly("elote")
    }

    @Test
    fun match_excludesPerIngredient() {
        val coco = Produce("coco", "Coco", "🥥", ProduceKind.FRUIT, emptyList(), List(12) { Availability.BEST }, listOf("leche de coco"))
        assertThat(producesIn(listOf("leche de coco"), listOf(coco))).isEmpty()
        // La exclusión vale para ese ingrediente, no para la receta entera.
        assertThat(producesIn(listOf("leche de coco", "coco fresco"), listOf(coco))).containsExactly("coco")
    }

    @Test
    fun recipesUsing_countsRecipes() {
        val recipes = listOf(
            summary("1", "Elote", "Mazorca"), summary("2", "Granos de elote"), summary("3", "Arroz"),
            summary("4", "Maizena"),
        )
        assertThat(recipesUsing(elote, recipes, small)).isEqualTo(2)
        assertThat(recipesUsing(calabaza, listOf(summary("5", "Flor de calabaza")), small)).isEqualTo(0)
        assertThat(recipesUsing(flor, listOf(summary("5", "Flor de calabaza")), small)).isEqualTo(1)
    }

    @Test
    fun bonus_bestAndAvailable() {
        val cal = listOf(
            p("a", "Guayaba", months = "AAAAAAAAAABA"),
            p("b", "Mandarina", months = "BBBBBBBBBBAB"),
            p("c", "Nopal", months = "OOOOOOOOOOOO"),
        )
        assertThat(inSeasonBonus(today("guayabas"), 11, cal)).isEqualTo(4.0)
        assertThat(inSeasonBonus(today("mandarina"), 11, cal)).isEqualTo(1.0)
        assertThat(inSeasonBonus(today("guayaba", "mandarina", "nopal"), 11, cal)).isEqualTo(5.0)
        assertThat(inSeasonBonus(today("nopal"), 11, cal)).isEqualTo(0.0)
        assertThat(inSeasonBonus(today("arroz"), 11, cal)).isEqualTo(0.0)
        // El mismo producto dos veces cuenta una.
        assertThat(inSeasonBonus(today("guayaba", "guayabas en almíbar"), 11, cal)).isEqualTo(4.0)
    }

    @Test
    fun bonus_capped8() {
        val cal = listOf(p("a", "Guayaba"), p("b", "Mandarina"), p("c", "Calabaza"))
        assertThat(inSeasonBonus(today("guayaba", "mandarina", "calabaza"), 3, cal)).isEqualTo(8.0)
    }

    @Test
    fun recipeCounts_sameAsRecipesUsing() {
        val recipes = listOf(
            summary("crema", "calabacitas", "elote"),
            summary("quesadillas", "flores de calabaza", "queso"),
            summary("salsa", "tomate verde", "chile poblano"),
            summary("arroz", "arroz", "jitomate"),
        )
        val counts = seasonRecipeCounts(recipes, small)
        for (produce in small) {
            assertWithMessage(produce.id).that(counts[produce.id] ?: 0).isEqualTo(recipesUsing(produce, recipes, small))
        }
        assertThat(counts["calabaza"]).isEqualTo(1)
        assertThat(counts).doesNotContainKey("cana")
    }
}
