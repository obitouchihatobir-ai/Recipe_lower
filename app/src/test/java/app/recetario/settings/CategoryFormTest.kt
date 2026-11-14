package app.recetario.settings

import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryFormTest {
    @Test
    fun firstGrapheme_keepsOnlyTheFirstVisibleCharacter() {
        assertEquals("🍲", firstGrapheme("🍲🍕"))
        assertEquals("é", firstGrapheme("éx"))
        assertEquals("", firstGrapheme(""))
        assertEquals("a", firstGrapheme("a"))
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
