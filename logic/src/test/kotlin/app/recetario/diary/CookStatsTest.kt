package app.recetario.diary

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CookStatsTest {
    private val today = LocalDate.of(2026, 11, 19)

    private fun e(id: String, recipeId: String?, date: LocalDate, rating: Int? = null) = CookLogEntry(
        id, recipeId, "Título", date, rating, "", emptyList(), Instant.EPOCH, Instant.EPOCH,
    )

    @Test
    fun stats_countLastAndAverage() {
        val entries = listOf(
            e("1", "a", today.minusDays(10), 5),
            e("2", "a", today.minusDays(3), 4),
            e("3", "a", today.minusDays(20), null),
        )
        val s = statsByRecipe(entries).getValue("a")
        assertThat(s.count).isEqualTo(3)
        assertThat(s.lastDate).isEqualTo(today.minusDays(3))
        assertThat(s.averageRating).isEqualTo(4.5)
    }

    @Test
    fun stats_noRatingsMeansNullAverage() {
        val s = statsByRecipe(listOf(e("1", "a", today))).getValue("a")
        assertThat(s.averageRating).isNull()
    }

    @Test
    fun stats_ignoresOrphanEntries() {
        val map = statsByRecipe(listOf(e("1", null, today, 5), e("2", "a", today)))
        assertThat(map.keys).containsExactly("a")
    }

    @Test
    fun roundToHalfStar_cuts() {
        assertThat(roundToHalfStar(3.74)).isEqualTo(3.5)
        assertThat(roundToHalfStar(3.75)).isEqualTo(4.0)
    }

    @Test
    fun line_noEntries() {
        assertThat(cookStatsLine(null, today)).isEqualTo("Aún no la has hecho")
        assertThat(cookStatsLine(CookStats(0, null, null), today)).isEqualTo("Aún no la has hecho")
    }

    @Test
    fun line_oneToday() {
        assertThat(cookStatsLine(CookStats(1, today, null), today)).isEqualTo("La hiciste 1 vez · hoy")
    }

    @Test
    fun line_threeWithRating() {
        val s = CookStats(3, today.minusDays(14), 4.2)
        assertThat(cookStatsLine(s, today)).isEqualTo("La hiciste 3 veces · última hace 2 semanas · ★★★★☆")
    }

    @Test
    fun line_withoutRatingEndsAfterDate() {
        val s = CookStats(2, today.minusDays(1), null)
        assertThat(cookStatsLine(s, today)).isEqualTo("La hiciste 2 veces · última ayer")
    }

    @Test
    fun line_halfStar() {
        assertThat(cookStatsLine(CookStats(2, today, 3.75), today)).endsWith("★★★★☆")
        assertThat(cookStatsLine(CookStats(2, today, 3.4), today)).endsWith("★★★⯪☆")
    }

    @Test
    fun relativeDates() {
        val table = listOf(
            0 to "hoy", 1 to "ayer", 2 to "hace 2 días", 13 to "hace 13 días",
            14 to "hace 2 semanas", 62 to "hace 8 semanas",
            63 to "hace 2 meses", 364 to "hace 11 meses",
            365 to "hace más de un año", 1000 to "hace más de un año",
        )
        for ((days, expected) in table) {
            assertThat(relativeDate(today.minusDays(days.toLong()), today)).isEqualTo(expected)
        }
    }

    @Test
    fun longTimeNoCook_onlyCookedOlderThan30_oldestFirst_max6() {
        val stats = buildMap {
            put("fresh", CookStats(1, today.minusDays(29), null))
            put("edge", CookStats(1, today.minusDays(30), null))
            put("never", CookStats(0, null, null))
            for (i in 1..7) put("old$i", CookStats(1, today.minusDays(40L + i), null))
        }
        val result = longTimeNoCook(stats, today)
        assertThat(result).hasSize(6)
        assertThat(result.first()).isEqualTo("old7")
        assertThat(result).doesNotContain("fresh")
        assertThat(result).doesNotContain("never")
        val all = longTimeNoCook(stats, today, limit = 20)
        assertThat(all).contains("edge")
        assertThat(all).doesNotContain("fresh")
        assertThat(all).doesNotContain("never")
        assertThat(all).hasSize(8)
    }
}
