package app.recetario.settings

import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryFormTest {
    @Test
    fun lastGrapheme_keepsOnlyTheLastVisibleCharacter() {
        assertEquals("\uD83C\uDF55", lastGrapheme("\uD83C\uDF72\uD83C\uDF55"))
        // Emoji prellenado + el nuevo que se escribe: se queda el nuevo.
        assertEquals("\uD83C\uDF2E", lastGrapheme("\uD83C\uDF7D\uFE0F\uD83C\uDF2E"))
        // Un emoji combinado (hombre + cocinero) sigue entero.
        assertEquals("\uD83D\uDC68\u200D\uD83C\uDF73", lastGrapheme("\uD83C\uDF55\uD83D\uDC68\u200D\uD83C\uDF73"))
        assertEquals("e\u0301", lastGrapheme("xe\u0301"))
        assertEquals("", lastGrapheme(""))
        assertEquals("a", lastGrapheme("a"))
    }

    @Test
    fun defaultReassignTarget_matchesBaseType() {
        fun cat(id: String, type: BaseType) = Category(id, id, "x", "#000000", type, 0, id in DefaultCategories.ids)
        val others = listOf(
            cat(DefaultCategories.FOOD_ID, BaseType.FOOD),
            cat(DefaultCategories.DRINK_ID, BaseType.DRINK),
            cat(DefaultCategories.DESSERT_ID, BaseType.DESSERT),
        )
        assertEquals(DefaultCategories.DESSERT_ID, defaultReassignTarget(cat("a", BaseType.DESSERT), others)?.id)
        assertEquals(DefaultCategories.DRINK_ID, defaultReassignTarget(cat("a", BaseType.DRINK), others)?.id)
        assertEquals(DefaultCategories.FOOD_ID, defaultReassignTarget(cat("a", BaseType.OTHER), others)?.id)
    }
}
