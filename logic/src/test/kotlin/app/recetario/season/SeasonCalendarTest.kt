package app.recetario.season

import app.recetario.core.text.normalize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeasonCalendarTest {
    private val calendar = SeasonCalendar.load()

    @Test
    fun calendar_atLeast70_uniqueIds_12months() {
        assertThat(calendar.size).isAtLeast(70)
        assertThat(calendar.map { it.id }.toSet()).hasSize(calendar.size)
        assertThat(calendar.map { normalize(it.name) }.toSet()).hasSize(calendar.size)
        assertThat(calendar.all { it.id.matches(Regex("[a-z0-9-]+")) }).isTrue()
        assertThat(calendar.all { it.months.size == 12 }).isTrue()
        // Cada producto se encuentra en el mercado al menos un mes y tiene su mejor momento.
        assertThat(calendar.filter { Availability.BEST !in it.months }.map { it.id }).isEmpty()
        assertThat(calendar.all { it.emoji.isNotBlank() && it.name.isNotBlank() }).isTrue()
        assertThat(ProduceKind.entries.all { k -> calendar.any { it.kind == k } }).isTrue()
    }

    @Test
    fun calendar_includesMexicanStaples() {
        val names = calendar.map { normalize(it.name) }
        for (wanted in listOf(
            "mango", "guayaba", "pitahaya", "elote", "nopal", "chile poblano", "chile jalapeno", "jitomate",
            "tomate verde", "aguacate", "calabaza", "epazote", "cilantro",
        )) assertThat(names).contains(wanted)
    }

    @Test
    fun calendar_emojiExistOnAndroid8() {
        // Emoji 5.0 o anteriores: Android 8.0 los dibuja sin EmojiCompat (nada de 🥭, 🥬, 🧄, 🧅, 🫑…; los que no tienen el suyo usan uno genérico).
        val allowed = setOf(
            "🍇", "🍈", "🍉", "🍊", "🍋", "🍌", "🍍", "🍎", "🍏", "🍐", "🍑", "🍒", "🍓", "🥝", "🍅", "🥥",
            "🥑", "🍆", "🥔", "🥕", "🌽", "🌶️", "🥒", "🥦", "🍄", "🥜", "🌰", "🌿", "🌱", "🍃", "🌵", "🌸",
            "🌼", "🍠", "🥗", "🌾", "🍀", "🎃", "🌺",
        )
        assertThat(calendar.map { it.emoji }.filter { it !in allowed }).isEmpty()
    }

    @Test
    fun forMonth_bestThenAvailable() {
        val (best, available) = SeasonCalendar.forMonth(11)
        assertThat(best).isNotEmpty()
        assertThat(available).isNotEmpty()
        assertThat(best.all { it.months[10] == Availability.BEST }).isTrue()
        assertThat(available.all { it.months[10] == Availability.AVAILABLE }).isTrue()
        assertThat(best.size + available.size).isEqualTo(calendar.count { it.months[10] != Availability.OUT })
        assertThat(best.map { it.id }).containsAtLeast("guayaba", "mandarina", "calabaza")
        // El orden del calendario se respeta.
        assertThat(best).isEqualTo(calendar.filter { it.months[10] == Availability.BEST })
        // Abril: el mango ya está en su mejor momento; en noviembre, no.
        assertThat(SeasonCalendar.forMonth(5).first.map { it.id }).contains("mango")
        assertThat(best.map { it.id }).doesNotContain("mango")
    }

    @Test
    fun calendar_fileOrder_pinsNovemberTeaser() {
        // El orden del archivo decide la tarjeta de Inicio: guayaba, mandarina y calabaza van primero.
        assertThat(calendar.take(3).map { it.id }).containsExactly("guayaba", "mandarina", "calabaza").inOrder()
        assertThat(SeasonCalendar.forMonth(11).first.take(3).map { it.id })
            .containsExactly("guayaba", "mandarina", "calabaza").inOrder()
    }

    @Test
    fun forMonth_outOfRange_throws() {
        for (m in listOf(0, 13)) {
            val thrown = runCatching { SeasonCalendar.forMonth(m) }.exceptionOrNull()
            assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
