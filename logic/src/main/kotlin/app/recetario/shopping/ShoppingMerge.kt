package app.recetario.shopping

import app.recetario.converter.Conversion
import app.recetario.converter.UnitConverter
import app.recetario.core.text.normalize
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.quantity.UnitKind

/**
 * Añade [incoming] a [existing] fundiendo cada línea con una línea sin marcar del mismo nombre
 * (sin tildes, mayúsculas ni plural) y unidad compatible. Las masas se suman en g y los volúmenes en ml
 * (la taza mide [cupMl]); lo contable, solo con la misma unidad; «pizca» y «al gusto» quedan en una línea
 * sin cantidad. Lo que no se puede fundir va como línea nueva. Conserva el nombre de la primera línea.
 */
fun mergeInto(existing: List<ShoppingItem>, incoming: List<ShoppingItem>, cupMl: Int = 240): List<ShoppingItem> {
    val converter = UnitConverter(cupMl)
    val result = existing.toMutableList()
    for (item in incoming) {
        var merged = false
        for (i in result.indices) {
            val target = result[i]
            if (target.checked || !sameName(target.name, item.name)) continue
            val combined = combine(target, item, converter) ?: continue
            result[i] = combined
            merged = true
            break
        }
        if (!merged) result += item
    }
    return result
}

private val vague = setOf(MeasureUnit.PINCH, MeasureUnit.TO_TASTE)

private fun combine(a: ShoppingItem, b: ShoppingItem, converter: UnitConverter): ShoppingItem? {
    if (a.unit in vague && b.unit in vague) return a.copy(quantity = null)
    if (a.unit in vague || b.unit in vague) return null
    val kind = a.unit.kind
    if (kind != b.unit.kind) return null
    // Misma unidad: se conserva (2 cucharadas + 2 cucharadas = 4 cucharadas); solo se convierte si difieren.
    if (a.unit == b.unit && (kind == UnitKind.MASS || kind == UnitKind.VOLUME)) {
        return a.copy(quantity = sum(a.quantity, b.quantity))
    }
    return when (kind) {
        UnitKind.MASS -> sumIn(a, b, MeasureUnit.GRAM, converter)
        UnitKind.VOLUME -> sumIn(a, b, MeasureUnit.MILLILITER, converter)
        UnitKind.COUNT -> if (a.unit == b.unit) a.copy(quantity = sum(a.quantity, b.quantity)) else null
        UnitKind.NONE ->
            if (a.unit == MeasureUnit.CUSTOM && b.unit == MeasureUnit.CUSTOM &&
                normalize(a.customUnit.orEmpty()) == normalize(b.customUnit.orEmpty())
            ) a.copy(quantity = sum(a.quantity, b.quantity)) else null
    }
}

private fun sumIn(a: ShoppingItem, b: ShoppingItem, target: MeasureUnit, converter: UnitConverter): ShoppingItem? {
    val x = a.quantity?.let { (converter.convert(it, a.unit, target) as? Conversion.Ok)?.value ?: return null }
    val y = b.quantity?.let { (converter.convert(it, b.unit, target) as? Conversion.Ok)?.value ?: return null }
    return a.copy(quantity = sum(x, y), unit = target, customUnit = null)
}

private fun sum(x: Double?, y: Double?): Double? = if (x == null) y else if (y == null) x else x + y

/** Mismo nombre normalizado, palabra a palabra, aceptando una «s» o «es» de plural en un solo lado. */
private fun sameName(a: String, b: String): Boolean {
    val wa = normalize(a).split(' ')
    val wb = normalize(b).split(' ')
    return wa.size == wb.size && wa.indices.all { sameWord(wa[it], wb[it]) }
}

private fun sameWord(x: String, y: String): Boolean =
    x == y || pluralOf(x, y) || pluralOf(y, x)

/** True si [plural] es [singular] con «s» o «es» al final. */
private fun pluralOf(singular: String, plural: String): Boolean =
    singular.length >= 3 && (plural == singular + "s" || plural == singular + "es")
