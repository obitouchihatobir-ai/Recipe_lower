package app.recetario.shopping

import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class ShoppingShareTest {
    private fun item(name: String, q: Double?, unit: MeasureUnit, section: ShopSection, checked: Boolean = false) =
        ShoppingItem("id-$name", name, q, unit, null, section, checked, null, Instant.EPOCH)

    @Test fun formatsSectionsInOrderSkippingChecked() {
        val text = asShareText(
            listOf(
                item("Leche", 1.0, MeasureUnit.LITER, ShopSection.DAIRY),
                item("Calabaza", 1800.0, MeasureUnit.GRAM, ShopSection.FRUITS_VEG),
                item("Sal", null, MeasureUnit.TO_TASTE, ShopSection.PANTRY),
                item("Cebolla", 2.0, MeasureUnit.PIECE, ShopSection.FRUITS_VEG),
                item("Pan", null, MeasureUnit.PIECE, ShopSection.OTHER),
                item("Pollo", 500.0, MeasureUnit.GRAM, ShopSection.MEAT_FISH, checked = true),
            ),
        )
        assertThat(text).isEqualTo(
            """
            Lista de compra

            Frutas y verduras
            • Calabaza — 1,8 kg
            • Cebolla — 2

            Lácteos
            • Leche — 1 l

            Despensa
            • Sal — al gusto

            Otros
            • Pan
            """.trimIndent(),
        )
    }

    @Test fun emptyListIsJustTheTitle() {
        assertThat(asShareText(emptyList())).isEqualTo("Lista de compra")
    }
}
