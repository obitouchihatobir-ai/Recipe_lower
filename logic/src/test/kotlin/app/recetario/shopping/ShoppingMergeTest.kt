package app.recetario.shopping

import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class ShoppingMergeTest {
    private fun item(
        name: String,
        q: Double?,
        unit: MeasureUnit,
        id: String = name + q,
        checked: Boolean = false,
        custom: String? = null,
    ) = ShoppingItem(
        id = id, name = name, quantity = q, unit = unit, customUnit = custom,
        section = SectionClassifier.classify(name), checked = checked, sourceRecipeId = null,
        createdAt = Instant.EPOCH,
    )

    @Test fun massesAddUpInGrams() {
        val out = mergeInto(
            listOf(item("Calabaza", 800.0, MeasureUnit.GRAM)),
            listOf(item("Calabaza", 1.0, MeasureUnit.KILOGRAM)),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].quantity).isWithin(1e-9).of(1800.0)
        assertThat(out[0].unit).isEqualTo(MeasureUnit.GRAM)
    }

    @Test fun pluralAndSingularMerge() {
        val out = mergeInto(
            listOf(item("Cebolla", 1.0, MeasureUnit.PIECE)),
            listOf(item("cebollas", 2.0, MeasureUnit.PIECE)),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].quantity).isEqualTo(3.0)
        assertThat(out[0].name).isEqualTo("Cebolla")
    }

    @Test fun limonAndLimonesMerge() {
        val out = mergeInto(
            listOf(item("Limón", 1.0, MeasureUnit.PIECE)),
            listOf(item("limones", 2.0, MeasureUnit.PIECE)),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].quantity).isEqualTo(3.0)
    }

    @Test fun volumesAddUpInMillilitersWithCup() {
        val out = mergeInto(
            listOf(item("Leche", 200.0, MeasureUnit.MILLILITER)),
            listOf(item("Leche", 1.0, MeasureUnit.CUP)),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].quantity).isWithin(1e-9).of(440.0)
        assertThat(out[0].unit).isEqualTo(MeasureUnit.MILLILITER)
    }

    @Test fun cupSizeIsConfigurable() {
        val out = mergeInto(
            listOf(item("Leche", 200.0, MeasureUnit.MILLILITER)),
            listOf(item("Leche", 1.0, MeasureUnit.CUP)),
            cupMl = 250,
        )
        assertThat(out[0].quantity).isWithin(1e-9).of(450.0)
    }

    @Test fun incompatibleUnitsStaySeparate() {
        val out = mergeInto(
            listOf(item("Ajo", 2.0, MeasureUnit.CLOVE)),
            listOf(item("Ajo", 100.0, MeasureUnit.GRAM)),
        )
        assertThat(out).hasSize(2)
    }

    @Test fun differentCountableUnitsStaySeparate() {
        val out = mergeInto(
            listOf(item("Ajo", 2.0, MeasureUnit.CLOVE)),
            listOf(item("Ajo", 1.0, MeasureUnit.PIECE)),
        )
        assertThat(out).hasSize(2)
    }

    @Test fun toTasteTwiceIsOneLineWithoutQuantity() {
        val out = mergeInto(
            listOf(item("Sal", null, MeasureUnit.TO_TASTE)),
            listOf(item("Sal", null, MeasureUnit.TO_TASTE)),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].quantity).isNull()
    }

    @Test fun pinchAndToTasteShareOneLine() {
        val out = mergeInto(
            listOf(item("Sal", null, MeasureUnit.PINCH)),
            listOf(item("Sal", null, MeasureUnit.TO_TASTE)),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].quantity).isNull()
    }

    @Test fun checkedExistingLineIsNotReused() {
        val out = mergeInto(
            listOf(item("Leche", 200.0, MeasureUnit.MILLILITER, checked = true)),
            listOf(item("Leche", 100.0, MeasureUnit.MILLILITER, id = "nueva")),
        )
        assertThat(out).hasSize(2)
        assertThat(out[0].checked).isTrue()
        assertThat(out[0].quantity).isEqualTo(200.0)
        assertThat(out[1].id).isEqualTo("nueva")
        assertThat(out[1].checked).isFalse()
    }

    @Test fun incomingDuplicatesMergeAmongThemselves() {
        val out = mergeInto(
            emptyList(),
            listOf(item("Harina", 100.0, MeasureUnit.GRAM, id = "a"), item("harina", 50.0, MeasureUnit.GRAM, id = "b")),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].id).isEqualTo("a")
        assertThat(out[0].quantity).isEqualTo(150.0)
    }

    @Test fun customUnitsMergeOnlyWhenTheTextMatches() {
        val out = mergeInto(
            listOf(item("Pan", 1.0, MeasureUnit.CUSTOM, custom = "barra")),
            listOf(
                item("Pan", 1.0, MeasureUnit.CUSTOM, custom = "Barra", id = "x"),
                item("Pan", 1.0, MeasureUnit.CUSTOM, custom = "rebanada", id = "y"),
            ),
        )
        assertThat(out).hasSize(2)
        assertThat(out[0].quantity).isEqualTo(2.0)
    }

    @Test fun missingQuantityKeepsTheOtherOne() {
        val out = mergeInto(
            listOf(item("Cebolla", null, MeasureUnit.PIECE)),
            listOf(item("Cebolla", 2.0, MeasureUnit.PIECE)),
        )
        assertThat(out).hasSize(1)
        assertThat(out[0].quantity).isEqualTo(2.0)
    }

    @Test fun sameNameWordsEndingInSAreNotOverMerged() {
        val out = mergeInto(
            listOf(item("Arroz", 100.0, MeasureUnit.GRAM)),
            listOf(item("Arro", 100.0, MeasureUnit.GRAM)),
        )
        assertThat(out).hasSize(2)
    }

    @Test fun multiWordNamesMatchWordByWord() {
        val out = mergeInto(
            listOf(item("Tomate cherry", 100.0, MeasureUnit.GRAM)),
            listOf(item("Tomates cherry", 100.0, MeasureUnit.GRAM)),
        )
        assertThat(out).hasSize(1)
    }

    @Test fun sameUnitIsKept() {
        val spoons = mergeInto(
            listOf(item("Azúcar", 2.0, MeasureUnit.TABLESPOON)),
            listOf(item("Azúcar", 2.0, MeasureUnit.TABLESPOON)),
        )
        assertThat(spoons).hasSize(1)
        assertThat(spoons[0].quantity).isEqualTo(4.0)
        assertThat(spoons[0].unit).isEqualTo(MeasureUnit.TABLESPOON)
        val tsp = mergeInto(
            listOf(item("Canela", 1.0, MeasureUnit.TEASPOON)),
            listOf(item("Canela", 1.0, MeasureUnit.TEASPOON)),
        )
        assertThat(tsp[0].quantity).isEqualTo(2.0)
        assertThat(tsp[0].unit).isEqualTo(MeasureUnit.TEASPOON)
        val kg = mergeInto(
            listOf(item("Patata", 1.0, MeasureUnit.KILOGRAM)),
            listOf(item("Patata", 2.0, MeasureUnit.KILOGRAM)),
        )
        assertThat(kg[0].unit).isEqualTo(MeasureUnit.KILOGRAM)
        assertThat(kg[0].quantity).isEqualTo(3.0)
    }

    @Test fun differentUnitsStillConvert() {
        val out = mergeInto(
            listOf(item("Azúcar", 2.0, MeasureUnit.TABLESPOON)),
            listOf(item("Azúcar", 1.0, MeasureUnit.TEASPOON)),
        )
        assertThat(out[0].unit).isEqualTo(MeasureUnit.MILLILITER)
        assertThat(out[0].quantity).isWithin(1e-9).of(35.0)
    }
}
