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

class ThermalThresholdsTest {
    @Test
    fun defaults_andValidity() {
        assertThat(ThermalThresholds()).isEqualTo(ThermalThresholds(coldBelow = 15, hotAbove = 24))
        assertThat(ThermalThresholds().isValid).isTrue()
        assertThat(ThermalThresholds(20, 20).isValid).isFalse()
        assertThat(ThermalThresholds(25, 20).isValid).isFalse()
        assertThat(ThermalThresholds(-30, 20).isValid).isFalse()
        assertThat(ThermalThresholds(10, 50).isValid).isFalse()
    }

    @Test
    fun thermalClass_withThresholds() {
        val t = ThermalThresholds(coldBelow = 20, hotAbove = 30)
        assertThat(thermalClass(19.5, t)).isEqualTo(Thermal.COLD)
        assertThat(thermalClass(25.0, t)).isEqualTo(Thermal.MILD)
        assertThat(thermalClass(30.5, t)).isEqualTo(Thermal.HOT)
    }
}
