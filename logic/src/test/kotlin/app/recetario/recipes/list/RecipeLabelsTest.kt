package app.recetario.recipes.list

import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.recipes.search.RecipeFilter
import app.recetario.recipes.search.SortOrder
import app.recetario.recipes.search.Taste
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeLabelsTest {
    @Test
    fun minutes_inMinutesAndHours() {
        assertThat(formatMinutes(10)).isEqualTo("10 min")
        assertThat(formatMinutes(60)).isEqualTo("1 h")
        assertThat(formatMinutes(90)).isEqualTo("1 h 30 min")
        assertThat(formatMinutes(125)).isEqualTo("2 h 5 min")
    }

    @Test
    fun duration_forTimerChips() {
        assertThat(formatDurationSeconds(1200)).isEqualTo("20 min")
        assertThat(formatDurationSeconds(5400)).isEqualTo("1 h 30 min")
        assertThat(formatDurationSeconds(45)).isEqualTo("45 s")
        assertThat(formatDurationSeconds(90)).isEqualTo("1 min 30 s")
        assertThat(formatDurationSeconds(3600)).isEqualTo("1 h")
    }

    @Test
    fun tasteWord_sweetSaltyThenStrongestOther() {
        assertThat(tasteWord(FlavorProfile(sweet = 3, salty = 1))).isEqualTo("dulce")
        assertThat(tasteWord(FlavorProfile(sweet = 1, salty = 2))).isEqualTo("salada")
        assertThat(tasteWord(FlavorProfile(sweet = 1, sour = 3))).isEqualTo("ácida")
        assertThat(tasteWord(FlavorProfile(spicy = 2, bitter = 3))).isEqualTo("amarga")
        assertThat(tasteWord(FlavorProfile(spicy = 2))).isEqualTo("picante")
        assertThat(tasteWord(FlavorProfile(sweet = 1, sour = 1))).isNull()
    }

    @Test
    fun meta_joinsKindTasteAndTime() {
        assertThat(recipeMeta("Bebida", FlavorProfile(sour = 3), 10)).isEqualTo("Bebida · ácida · 10 min")
        assertThat(recipeMeta("Comida", FlavorProfile(), null)).isEqualTo("Comida")
        assertThat(recipeMeta(null, FlavorProfile(salty = 2), 95)).isEqualTo("salada · 1 h 35 min")
    }

    @Test
    fun unitLabels_forThePicker() {
        assertThat(unitLabel(MeasureUnit.GRAM)).isEqualTo("g")
        assertThat(unitLabel(MeasureUnit.PIECE)).isEqualTo("unidad")
        assertThat(unitLabel(MeasureUnit.TO_TASTE)).isEqualTo("al gusto")
        assertThat(unitLabel(MeasureUnit.CUSTOM)).isEqualTo("Otra…")
        assertThat(MeasureUnit.entries.map(::unitLabel).toSet()).hasSize(MeasureUnit.entries.size)
    }

    @Test
    fun results_singularPlural() {
        assertThat(resultsLabel(1)).isEqualTo("1 receta")
        assertThat(resultsLabel(0)).isEqualTo("0 recetas")
        assertThat(resultsLabel(12)).isEqualTo("12 recetas")
    }

    @Test
    fun sheetFilterCount_ignoresQueryCategoriesAndSort() {
        assertThat(RecipeFilter(query = "x", categoryIds = setOf("c"), sort = SortOrder.ALPHABETIC).sheetFilterCount()).isEqualTo(0)
        val all = RecipeFilter(
            tags = setOf("a", "b"), taste = Taste.SWEET, alcoholic = false, serveTemp = ServeTemp.COLD,
            maxMinutes = 30, moment = Moment.DINNER, difficulty = Difficulty.EASY, favoritesOnly = true,
        )
        assertThat(all.sheetFilterCount()).isEqualTo(8)
    }

    @Test
    fun clearedSheetFilters_keepQueryAndCategories() {
        val f = RecipeFilter(query = "té", categoryIds = setOf("c"), taste = Taste.SALTY, favoritesOnly = true, sort = SortOrder.ALPHABETIC)
        assertThat(f.withoutSheetFilters()).isEqualTo(RecipeFilter(query = "té", categoryIds = setOf("c"), sort = SortOrder.ALPHABETIC))
    }
}
