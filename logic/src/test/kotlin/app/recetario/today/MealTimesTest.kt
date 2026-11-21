package app.recetario.today

import app.recetario.recipes.model.Moment
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test

class MealTimesTest {
    private val t = MealTimes()

    @Test
    fun moment_defaults() {
        assertThat(t.momentAt(LocalTime.of(7, 0))).isEqualTo(Moment.BREAKFAST)
        assertThat(t.momentAt(LocalTime.of(12, 0))).isEqualTo(Moment.LUNCH)
        assertThat(t.momentAt(LocalTime.of(17, 30))).isEqualTo(Moment.SNACK)
        assertThat(t.momentAt(LocalTime.of(22, 59))).isEqualTo(Moment.DINNER)
        assertThat(t.momentAt(LocalTime.of(23, 0))).isEqualTo(Moment.NIGHT)
        assertThat(t.momentAt(LocalTime.of(3, 0))).isEqualTo(Moment.NIGHT)
    }

    @Test
    fun moment_edges() {
        assertThat(t.momentAt(LocalTime.of(6, 59))).isEqualTo(Moment.NIGHT)
        assertThat(t.momentAt(LocalTime.of(10, 59))).isEqualTo(Moment.BREAKFAST)
        assertThat(t.momentAt(LocalTime.of(11, 0))).isEqualTo(Moment.LUNCH)
        assertThat(t.momentAt(LocalTime.of(13, 0))).isEqualTo(Moment.LUNCH)
        assertThat(t.momentAt(LocalTime.of(15, 59))).isEqualTo(Moment.LUNCH)
        assertThat(t.momentAt(LocalTime.of(16, 0))).isEqualTo(Moment.SNACK)
        assertThat(t.momentAt(LocalTime.of(19, 0))).isEqualTo(Moment.DINNER)
    }

    @Test
    fun moment_customTimes() {
        val c = MealTimes(breakfast = TimeRange(LocalTime.of(6, 0), LocalTime.of(10, 0)))
        assertThat(c.momentAt(LocalTime.of(6, 30))).isEqualTo(Moment.BREAKFAST)
        assertThat(c.momentAt(LocalTime.of(10, 30))).isEqualTo(Moment.LUNCH)
    }
}

class MealTimesEditingTest {
    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun defaults_areValid_withTheirGaps() {
        val t = MealTimes()
        assertThat(t.problem()).isNull()
        // 11:00–13:00 cuenta como comida; entre comida y merienda no hay hueco.
        assertThat(t.gaps()).containsExactly(MealGap(TimeRange(at(11), at(13)), Moment.LUNCH))
        assertThat(t.gaps().map { gapLine(it) }).containsExactly("De 11:00 a 13:00 cuenta como comida.")
    }

    @Test
    fun overlap_rejected() {
        val t = MealTimes(breakfast = TimeRange(at(7), at(13, 30)))
        assertThat(t.problem()).isEqualTo("El desayuno y la comida se solapan.")
        val u = MealTimes(snack = TimeRange(at(16), at(19, 30)))
        assertThat(u.problem()).isEqualTo("La merienda y la cena se solapan.")
    }

    @Test
    fun touching_isFine() {
        assertThat(MealTimes(breakfast = TimeRange(at(7), at(13))).problem()).isNull()
    }

    @Test
    fun crossingMidnight_rejected() {
        val t = MealTimes(dinner = TimeRange(at(19), at(0)))
        assertThat(t.problem()).isEqualTo("La cena no puede pasar de la medianoche.")
    }

    @Test
    fun empty_rejected() {
        val t = MealTimes(snack = TimeRange(at(16), at(16)))
        assertThat(t.problem()).isEqualTo("La merienda no puede quedar vacía.")
    }

    @Test
    fun outOfOrder_rejected() {
        // La comida antes que el desayuno.
        val t = MealTimes(breakfast = TimeRange(at(14), at(15)), lunch = TimeRange(at(12), at(13)))
        assertThat(t.problem()).isEqualTo("El desayuno y la comida se solapan.")
    }

    @Test
    fun gapsAfterLunch_countAsNight() {
        val t = MealTimes(snack = TimeRange(at(17), at(19)))
        assertThat(t.gaps()).containsExactly(
            MealGap(TimeRange(at(11), at(13)), Moment.LUNCH),
            MealGap(TimeRange(at(16), at(17)), Moment.NIGHT),
        ).inOrder()
        assertThat(gapLine(t.gaps()[1])).isEqualTo("De 16:00 a 17:00 cuenta como noche.")
    }
}
