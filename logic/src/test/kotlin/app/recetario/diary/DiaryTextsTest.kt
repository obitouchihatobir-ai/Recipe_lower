package app.recetario.diary

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class DiaryTextsTest {
    private val today = LocalDate.of(2026, 11, 19)

    private fun e(id: String, date: LocalDate, createdAt: Long = 0) = CookLogEntry(
        id, "r", "Título", date, null, "", emptyList(), Instant.ofEpochSecond(createdAt), Instant.ofEpochSecond(createdAt),
    )

    @Test
    fun monthTitle_isSpanishLowercase() {
        assertThat(monthTitle(YearMonth.of(2026, 11))).isEqualTo("noviembre de 2026")
        assertThat(monthTitle(YearMonth.of(2025, 1))).isEqualTo("enero de 2025")
    }

    @Test
    fun groupByMonth_newestFirst_entriesNewestFirstInsideEachMonth() {
        val groups = groupByMonth(
            listOf(
                e("oct", LocalDate.of(2026, 10, 30)),
                e("nov-1", LocalDate.of(2026, 11, 1)),
                e("nov-15-a", LocalDate.of(2026, 11, 15), createdAt = 1),
                e("nov-15-b", LocalDate.of(2026, 11, 15), createdAt = 2),
                e("2025", LocalDate.of(2025, 12, 31)),
            ),
        )
        assertThat(groups.map { it.first }).containsExactly(
            YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2025, 12),
        ).inOrder()
        assertThat(groups.first().second.map { it.id }).containsExactly("nov-15-b", "nov-15-a", "nov-1").inOrder()
    }

    @Test
    fun ratingDescription() {
        assertThat(ratingDescription(null)).isEqualTo("Sin valoración")
        assertThat(ratingDescription(1)).isEqualTo("1 de 5 estrellas")
        assertThat(ratingDescription(4)).isEqualTo("4 de 5 estrellas")
    }

    @Test
    fun statsDescription_readsTheStarsAsWords() {
        val stats = CookStats(count = 3, lastDate = today.minusDays(14), averageRating = 3.4)
        assertThat(cookStatsLine(stats, today)).isEqualTo("La hiciste 3 veces · última hace 2 semanas · ★★★⯪☆")
        assertThat(cookStatsDescription(stats, today))
            .isEqualTo("La hiciste 3 veces · última hace 2 semanas · valoración media: 3,5 de 5 estrellas")
        val four = CookStats(count = 1, lastDate = today, averageRating = 4.2)
        assertThat(cookStatsDescription(four, today)).isEqualTo("La hiciste 1 vez · hoy · valoración media: 4 de 5 estrellas")
        val unrated = CookStats(count = 2, lastDate = today.minusDays(1), averageRating = null)
        assertThat(cookStatsDescription(unrated, today)).isEqualTo(cookStatsLine(unrated, today))
        assertThat(cookStatsDescription(null, today)).isEqualTo("Aún no la has hecho")
    }

    @Test
    fun logDateLabel_todayYesterdayOrFullDate() {
        assertThat(logDateLabel(today, today)).isEqualTo("Hoy")
        assertThat(logDateLabel(today.minusDays(1), today)).isEqualTo("Ayer")
        assertThat(logDateLabel(LocalDate.of(2026, 3, 5), today)).isEqualTo("5 de marzo de 2026")
    }

    @Test
    fun entryDateLabel_dayAndMonth() {
        assertThat(entryDateLabel(LocalDate.of(2026, 11, 3))).isEqualTo("3 de noviembre")
    }

    @Test
    fun ratingStars_filledAndEmpty() {
        assertThat(ratingStars(4)).isEqualTo("★★★★☆")
        assertThat(ratingStars(null)).isEqualTo("")
    }
}
