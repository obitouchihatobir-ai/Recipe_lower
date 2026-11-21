package app.recetario.pairing

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FullMenuTest {
    private val main = item("mole", rich = true, spicy = 1, intensity = Intensity.INTENSE, course = Course.MAIN, serveTemp = ServeTemp.HOT)
    private val starter = item("sopa", salty = 1, intensity = Intensity.LIGHT, course = Course.STARTER, serveTemp = ServeTemp.HOT)
    private val dessert = item("flan", PairRole.DESSERT, sweet = 3, rich = true, serveTemp = ServeTemp.COLD)
    private val drink = item("jamaica-casa", PairRole.DRINK, sour = 3, sweet = 2, serveTemp = ServeTemp.COLD, alcoholic = false)

    @Test
    fun fullMenu_prefersOwn_completesWithCatalog() {
        val catalog = Catalog.load()
        val own = fullMenu(listOf(main, starter, dessert, drink), catalog, emptyMap())!!
        assertThat(own.main).isEqualTo(main)
        assertThat(own.starter).isEqualTo(starter)
        assertThat(own.dessert).isEqualTo(dessert)
        assertThat(own.drink).isEqualTo(drink)
        assertThat(own.reason).matches("[A-ZÁÉÍÓÚÑ¿¡].*\\.")

        // Solo el principal es propio: el resto sale del catálogo.
        val completed = fullMenu(listOf(main), catalog, emptyMap())!!
        assertThat(completed.main).isEqualTo(main)
        assertThat(completed.starter!!.kind).isEqualTo("CATALOG")
        assertThat(completed.starter!!.course).isEqualTo(Course.STARTER)
        assertThat(completed.dessert!!.role).isEqualTo(PairRole.DESSERT)
        assertThat(completed.drink!!.role).isEqualTo(PairRole.DRINK)

        // Sin catálogo, lo que falta queda vacío.
        val bare = fullMenu(listOf(main), emptyList(), emptyMap())!!
        assertThat(bare.starter).isNull()
        assertThat(bare.dessert).isNull()
        assertThat(bare.drink).isNull()
    }

    @Test
    fun fullMenu_dislikedDrinkNotChosen() {
        val other = item("cerveza-casa", PairRole.DRINK, bitter = 1, serveTemp = ServeTemp.COLD, alcoholic = true)
        val menu = fullMenu(listOf(main, drink, other), emptyList(), mapOf(pairKey(main, drink) to false))!!
        assertThat(menu.drink).isEqualTo(other)
    }

    @Test
    fun fullMenu_noMain_null() {
        assertThat(fullMenu(listOf(starter, dessert, drink), Catalog.load(), emptyMap())).isNull()
        assertThat(fullMenu(emptyList(), Catalog.load(), emptyMap())).isNull()
    }
}
