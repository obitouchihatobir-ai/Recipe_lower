package app.recetario.pairing

import app.recetario.core.text.normalize
import app.recetario.recipes.model.Course
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogTest {
    private val catalog = Catalog.load()

    @Test
    fun catalog_atLeast70_uniqueIds_includesMexican() {
        assertThat(catalog.size).isAtLeast(70)
        assertThat(catalog.map { it.id }.toSet()).hasSize(catalog.size)
        assertThat(catalog.map { normalize(it.name) }.toSet()).hasSize(catalog.size)
        assertThat(catalog.all { it.id.matches(Regex("cat-[a-z0-9-]+")) }).isTrue()
        assertThat(catalog.all { it.kind == "CATALOG" }).isTrue()
        val names = catalog.map { normalize(it.name) }
        for (wanted in listOf(
            "agua de jamaica", "agua de horchata", "agua de tamarindo", "agua de limon con chia", "tejuino",
            "cafe de olla", "atole", "champurrado", "chocolate caliente", "tequila blanco", "tequila reposado",
            "mezcal", "raicilla", "michelada", "pulque", "tepache",
        )) assertThat(names).contains(wanted)
        for (generic in listOf("tinto", "blanco seco", "cerveza clara", "cerveza oscura", "te verde", "cafe", "agua mineral", "cola")) {
            assertThat(names.any { it.contains(generic) }).isTrue()
        }
    }

    @Test
    fun catalog_everyItemIsComplete() {
        for (it in catalog) {
            assertThat(it.name).isNotEmpty()
            assertThat(it.article).isIn(listOf("el", "la", "los", "las"))
            assertThat(it.alcoholic).isNotNull()
            assertThat(it.serveTemp).isNotNull()
            assertThat(it.flavor.hasTastes() || it.flavor.rich).isTrue()
            for (v in listOf(it.flavor.sweet, it.flavor.salty, it.flavor.sour, it.flavor.bitter, it.flavor.spicy)) {
                assertThat(v).isIn(0..3)
            }
            if (it.role != PairRole.DRINK) assertThat(it.alcoholic).isFalse()
        }
        val byRole = catalog.groupBy { it.role }
        assertThat(byRole.getValue(PairRole.DRINK).count { it.alcoholic == true }).isAtLeast(15)
        assertThat(byRole.getValue(PairRole.DRINK).count { it.alcoholic == false }).isAtLeast(15)
        assertThat(byRole.getValue(PairRole.DESSERT).size).isAtLeast(5)
        assertThat(catalog.count { it.course == Course.STARTER }).isAtLeast(3)
        assertThat(byRole.getValue(PairRole.SIDE).size).isAtLeast(5)
    }

    @Test
    fun catalog_loadIsCached() {
        assertThat(Catalog.load()).isSameInstanceAs(catalog)
    }
}
