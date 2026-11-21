package app.recetario.today

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThermalTest {
    @Test
    fun thermal_bounds() {
        assertThat(thermalClass(14.9)).isEqualTo(Thermal.COLD)
        assertThat(thermalClass(15.0)).isEqualTo(Thermal.MILD)
        assertThat(thermalClass(24.0)).isEqualTo(Thermal.MILD)
        assertThat(thermalClass(24.1)).isEqualTo(Thermal.HOT)
    }

    @Test
    fun thermal_customThresholds() {
        assertThat(thermalClass(18.0, coldBelow = 20.0, hotAbove = 30.0)).isEqualTo(Thermal.COLD)
        assertThat(thermalClass(31.0, coldBelow = 20.0, hotAbove = 30.0)).isEqualTo(Thermal.HOT)
    }
}
