package app.recetario.converter

import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.UnitKind

/** [approximate] es true cuando el paso entre volumen y peso usa una densidad de la tabla. */
data class Equivalence(val unit: MeasureUnit, val value: Double, val approximate: Boolean)

private val massTargets = listOf(MeasureUnit.GRAM, MeasureUnit.KILOGRAM, MeasureUnit.OUNCE, MeasureUnit.POUND)
private val massToVolumeTargets = listOf(MeasureUnit.MILLILITER, MeasureUnit.CUP, MeasureUnit.TABLESPOON)
private val volumeTargets = listOf(
    MeasureUnit.MILLILITER, MeasureUnit.LITER, MeasureUnit.CUP,
    MeasureUnit.TABLESPOON, MeasureUnit.TEASPOON, MeasureUnit.FLUID_OUNCE,
)

/**
 * Equivalencias de [quantity] [unit] de [ingredient] para la hoja del detalle: de masa a g, kg, oz y lb (y, si hay
 * densidad, a ml, taza y cucharada); de volumen a ml, l, taza, cucharada, cucharadita y fl oz (y, si hay densidad, a g).
 * Se omite la propia unidad; las cantidades sin masa ni volumen no tienen equivalencias.
 */
fun equivalencesFor(quantity: Double, unit: MeasureUnit, ingredient: String, cupMl: Int): List<Equivalence> {
    val converter = UnitConverter(cupMl)
    fun to(targets: List<MeasureUnit>, approximate: Boolean) = targets.filter { it != unit }.mapNotNull { t ->
        (converter.convert(quantity, unit, t, ingredient) as? Conversion.Ok)?.let { Equivalence(t, it.value, approximate) }
    }
    val dense = Densities.gramsPerMl(ingredient) != null
    return when (unit.kind) {
        UnitKind.MASS -> to(massTargets, false) + if (dense) to(massToVolumeTargets, true) else emptyList()
        UnitKind.VOLUME -> to(volumeTargets, false) + if (dense) to(listOf(MeasureUnit.GRAM), true) else emptyList()
        else -> emptyList()
    }
}
