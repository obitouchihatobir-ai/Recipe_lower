package app.recetario.season

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeasonTextsTest {
    private fun p(id: String, name: String, months: String) = Produce(
        id = id, name = name, emoji = "🍈", kind = ProduceKind.FRUIT, synonyms = emptyList(),
        months = months.map { if (it == 'B') Availability.BEST else if (it == 'A') Availability.AVAILABLE else Availability.OUT },
    )

    @Test
    fun teaser_text() {
        assertThat(seasonTeaser(11, SeasonCalendar.load()))
            .isEqualTo("De temporada en noviembre: guayaba, mandarina, calabaza…")
    }

    @Test
    fun teaser_fewProducts_noEllipsis_availableFillIn() {
        val cal = listOf(
            p("a", "Chile poblano", "BOOOOOOOOOOO"),
            p("b", "Tejocote", "AOOOOOOOOOOO"),
            p("c", "Nopal", "OBOOOOOOOOOO"),
        )
        assertThat(seasonTeaser(1, cal)).isEqualTo("De temporada en enero: chile poblano, tejocote")
        assertThat(seasonTeaser(2, cal)).isEqualTo("De temporada en febrero: nopal")
        assertThat(seasonTeaser(3, cal)).isEqualTo("De temporada en marzo")
    }

    @Test
    fun monthName_spanish() {
        assertThat((1..12).map { seasonMonthName(it) }).containsExactly(
            "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
            "noviembre", "diciembre",
        ).inOrder()
    }
}
