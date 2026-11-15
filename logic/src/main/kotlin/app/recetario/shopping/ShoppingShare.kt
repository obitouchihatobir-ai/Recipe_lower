package app.recetario.shopping

import app.recetario.recipes.quantity.formatQuantity

private const val TITLE = "Lista de compra"

/** Texto para compartir: título y, por sección, líneas «• nombre — cantidad». Omite lo ya marcado. */
fun asShareText(items: List<ShoppingItem>): String {
    val pending = items.filter { !it.checked }.groupBy { it.section }
    val blocks = ShopSection.entries.mapNotNull { section ->
        val list = pending[section].orEmpty()
        if (list.isEmpty()) return@mapNotNull null
        buildString {
            append(section.label)
            for (item in list) {
                val qty = formatQuantity(item.quantity, item.unit, item.customUnit)
                append("\n• ").append(item.name)
                if (qty.isNotEmpty()) append(" — ").append(qty)
            }
        }
    }
    return (listOf(TITLE) + blocks).joinToString("\n\n")
}
