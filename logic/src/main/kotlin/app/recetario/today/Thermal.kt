package app.recetario.today

enum class Thermal { COLD, MILD, HOT }

/** Frío por debajo de [coldBelow], calor por encima de [hotAbove]; los umbrales mismos son templado. */
fun thermalClass(apparentC: Double, coldBelow: Double = 15.0, hotAbove: Double = 24.0): Thermal = when {
    apparentC < coldBelow -> Thermal.COLD
    apparentC > hotAbove -> Thermal.HOT
    else -> Thermal.MILD
}
