package app.recetario.converter

import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.UnitKind

sealed interface Conversion {
    data class Ok(val value: Double, val unit: MeasureUnit) : Conversion
    data object NeedsDensity : Conversion
    data object Incompatible : Conversion
}

class UnitConverter(private val cupMl: Int = 240) {
    private fun gramsOf(u: MeasureUnit): Double = when (u) {
        MeasureUnit.GRAM -> 1.0
        MeasureUnit.KILOGRAM -> 1000.0
        MeasureUnit.OUNCE -> 28.3495
        MeasureUnit.POUND -> 453.592
        else -> error("no es masa: $u")
    }

    private fun mlOf(u: MeasureUnit): Double = when (u) {
        MeasureUnit.MILLILITER -> 1.0
        MeasureUnit.LITER -> 1000.0
        MeasureUnit.CUP -> cupMl.toDouble()
        MeasureUnit.TABLESPOON -> 15.0
        MeasureUnit.TEASPOON -> 5.0
        MeasureUnit.FLUID_OUNCE -> 29.5735
        else -> error("no es volumen: $u")
    }

    fun convert(value: Double, from: MeasureUnit, to: MeasureUnit, ingredient: String? = null): Conversion {
        if (from == to) return Conversion.Ok(value, to)
        val fk = from.kind
        val tk = to.kind
        return when {
            fk == UnitKind.MASS && tk == UnitKind.MASS -> Conversion.Ok(value * gramsOf(from) / gramsOf(to), to)
            fk == UnitKind.VOLUME && tk == UnitKind.VOLUME -> Conversion.Ok(value * mlOf(from) / mlOf(to), to)
            fk == UnitKind.VOLUME && tk == UnitKind.MASS -> {
                val d = ingredient?.let { Densities.gramsPerMl(it) } ?: return Conversion.NeedsDensity
                Conversion.Ok(value * mlOf(from) * d / gramsOf(to), to)
            }
            fk == UnitKind.MASS && tk == UnitKind.VOLUME -> {
                val d = ingredient?.let { Densities.gramsPerMl(it) } ?: return Conversion.NeedsDensity
                Conversion.Ok(value * gramsOf(from) / d / mlOf(to), to)
            }
            else -> Conversion.Incompatible
        }
    }
}
