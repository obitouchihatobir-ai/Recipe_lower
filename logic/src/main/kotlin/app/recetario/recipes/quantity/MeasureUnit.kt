package app.recetario.recipes.quantity

enum class UnitKind { MASS, VOLUME, COUNT, NONE }

enum class MeasureUnit(val kind: UnitKind, val singular: String, val plural: String) {
    GRAM(UnitKind.MASS, "g", "g"),
    KILOGRAM(UnitKind.MASS, "kg", "kg"),
    MILLILITER(UnitKind.VOLUME, "ml", "ml"),
    LITER(UnitKind.VOLUME, "l", "l"),
    CUP(UnitKind.VOLUME, "taza", "tazas"),
    TABLESPOON(UnitKind.VOLUME, "cucharada", "cucharadas"),
    TEASPOON(UnitKind.VOLUME, "cucharadita", "cucharaditas"),
    PIECE(UnitKind.COUNT, "", ""),
    CLOVE(UnitKind.COUNT, "diente", "dientes"),
    PINCH(UnitKind.COUNT, "pizca", "pizca"),
    TO_TASTE(UnitKind.NONE, "al gusto", "al gusto"),
    OUNCE(UnitKind.MASS, "oz", "oz"),
    POUND(UnitKind.MASS, "lb", "lb"),
    FLUID_OUNCE(UnitKind.VOLUME, "fl oz", "fl oz"),
    CUSTOM(UnitKind.NONE, "", ""),
}
