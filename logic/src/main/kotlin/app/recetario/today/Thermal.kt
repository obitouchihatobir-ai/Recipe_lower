package app.recetario.today

enum class Thermal { COLD, MILD, HOT }

/** Frío por debajo de [coldBelow], calor por encima de [hotAbove]; los umbrales mismos son templado. */
fun thermalClass(apparentC: Double, coldBelow: Double = 15.0, hotAbove: Double = 24.0): Thermal = when {
    apparentC < coldBelow -> Thermal.COLD
    apparentC > hotAbove -> Thermal.HOT
    else -> Thermal.MILD
}

/** Umbrales en grados enteros (se cambian de 1 en 1): frío por debajo de [coldBelow], calor por encima de [hotAbove]. */
data class ThermalThresholds(val coldBelow: Int = 15, val hotAbove: Int = 24) {
    val isValid: Boolean get() = coldBelow < hotAbove && coldBelow in RANGE && hotAbove in RANGE

    companion object {
        /** Lo que se puede elegir en Ajustes. */
        val RANGE = -10..45
    }
}

fun thermalClass(apparentC: Double, thresholds: ThermalThresholds): Thermal =
    thermalClass(apparentC, thresholds.coldBelow.toDouble(), thresholds.hotAbove.toDouble())
