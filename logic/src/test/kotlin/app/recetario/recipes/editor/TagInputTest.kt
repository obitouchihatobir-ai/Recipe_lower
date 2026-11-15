package app.recetario.recipes.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TagInputTest {
    @Test
    fun addsTrimmedTags_separatedByCommas() {
        assertThat(addTagsFromInput(listOf("Rápida"), " Verano , Fría ")).containsExactly("Rápida", "Verano", "Fría").inOrder()
    }

    @Test
    fun skipsBlankAndRepeated_ignoringAccentsAndCase() {
        assertThat(addTagsFromInput(listOf("Rápida"), "rapida, , VERANO, verano")).containsExactly("Rápida", "VERANO").inOrder()
    }

    @Test
    fun blankInput_keepsTheSameList() {
        val tags = listOf("Otoño")
        assertThat(addTagsFromInput(tags, "   ")).isSameInstanceAs(tags)
    }
}
