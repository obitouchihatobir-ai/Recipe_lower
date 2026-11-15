package app.recetario.shopping

import app.recetario.recipes.quantity.MeasureUnit
import java.time.Instant

/** Secciones del súper, en el orden en que se muestran. */
enum class ShopSection(val label: String) {
    FRUITS_VEG("Frutas y verduras"),
    DAIRY("Lácteos"),
    MEAT_FISH("Carnes y pescados"),
    PANTRY("Despensa"),
    DRINKS("Bebidas"),
    OTHER("Otros"),
}

data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double?,
    val unit: MeasureUnit,
    val customUnit: String?,
    val section: ShopSection,
    val checked: Boolean,
    val sourceRecipeId: String?,
    val createdAt: Instant,
)
