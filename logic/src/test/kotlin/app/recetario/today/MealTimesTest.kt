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
