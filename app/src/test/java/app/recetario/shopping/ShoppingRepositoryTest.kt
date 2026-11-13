package app.recetario.shopping

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.AppDatabase
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ShoppingRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ShoppingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomShoppingRepository(db, Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC), flowOf(240))
    }

    @After
    fun tearDown() = db.close()

    private fun all(): List<ShoppingItem> = runBlocking { repo.observe().first().values.flatten() }

    private val recipe = sampleRecipe(
        id = "r1", title = "Crema de calabaza",
        ingredients = listOf(
            ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
            ingredient("i2", "Cebolla", 1.0, MeasureUnit.PIECE),
            ingredient("i3", "Leche", 200.0, MeasureUnit.MILLILITER),
            ingredient("i4", "Caldo de verduras", 1.0, MeasureUnit.LITER),
            ingredient("i5", "Sal", null, MeasureUnit.TO_TASTE),
            ingredient("i6", "Aceite de oliva", 2.0, MeasureUnit.TABLESPOON),
            ingredient("i7", "Pimienta", null, MeasureUnit.PINCH),
            ingredient("i8", "Nata", 100.0, MeasureUnit.MILLILITER),
        ),
    )

    @Test
    fun addFromRecipe_scalesAndExcludes() = runBlocking<Unit> {
        // La receta es para 4; se pide para 6 y se excluye «Nata».
        val added = repo.addFromRecipe(recipe, servings = 6, excludeIngredientIds = setOf("i8"))
        assertThat(added).isEqualTo(7)
        val items = all()
        assertThat(items).hasSize(7)
        assertThat(items.map { it.name }).doesNotContain("Nata")
        assertThat(items.first { it.name == "Calabaza" }.quantity).isWithin(1e-9).of(1200.0)
        assertThat(items.first { it.name == "Cebolla" }.quantity).isWithin(1e-9).of(1.5)
        assertThat(items.first { it.name == "Sal" }.quantity).isNull()
        assertThat(items.first { it.name == "Calabaza" }.sourceRecipeId).isEqualTo("r1")
        assertThat(items.first { it.name == "Calabaza" }.section).isEqualTo(ShopSection.FRUITS_VEG)
        assertThat(items.first { it.name == "Leche" }.section).isEqualTo(ShopSection.DAIRY)
        assertThat(items.first { it.name == "Caldo de verduras" }.section).isEqualTo(ShopSection.PANTRY)
    }

    @Test
    fun addFromRecipe_twiceMergesLines_butReturnsIngredientsAdded() = runBlocking<Unit> {
        repo.addFromRecipe(recipe, 4, emptySet())
        val added = repo.addFromRecipe(recipe, 4, emptySet())
        assertThat(added).isEqualTo(8)
        val items = all()
        assertThat(items).hasSize(8)
        assertThat(items.first { it.name == "Calabaza" }.quantity).isWithin(1e-9).of(1600.0)
        assertThat(items.first { it.name == "Sal" }.quantity).isNull()
    }

    @Test
    fun observe_groupsBySectionInOrder() = runBlocking<Unit> {
        repo.addFromRecipe(recipe, 4, emptySet())
        val groups = repo.observe().first()
        assertThat(groups.keys.toList()).containsExactly(
            ShopSection.FRUITS_VEG, ShopSection.DAIRY, ShopSection.PANTRY,
        ).inOrder()
        assertThat(groups.getValue(ShopSection.FRUITS_VEG).map { it.name }).containsExactly("Calabaza", "Cebolla").inOrder()
    }

    @Test
    fun addManual_classifiesAndMerges() = runBlocking<Unit> {
        repo.addManual("Leche", 1.0, MeasureUnit.CUP)
        repo.addManual("leche", 200.0, MeasureUnit.MILLILITER)
        val items = all()
        assertThat(items).hasSize(1)
        assertThat(items[0].quantity).isWithin(1e-9).of(440.0)
        assertThat(items[0].section).isEqualTo(ShopSection.DAIRY)
    }

    @Test
    fun addManual_ignoresBlankName() = runBlocking<Unit> {
        repo.addManual("   ", 1.0, MeasureUnit.PIECE)
        assertThat(all()).isEmpty()
    }

    @Test
    fun toggle_marksAndUnmarks_checkedGoLastInSection() = runBlocking<Unit> {
        repo.addManual("Cebolla", 1.0, MeasureUnit.PIECE)
        repo.addManual("Calabaza", 1.0, MeasureUnit.PIECE)
        val first = all().first { it.name == "Cebolla" }
        repo.toggle(first.id)
        val groups = repo.observe().first()
        assertThat(groups.getValue(ShopSection.FRUITS_VEG).map { it.name }).containsExactly("Calabaza", "Cebolla").inOrder()
        assertThat(all().first { it.name == "Cebolla" }.checked).isTrue()
        repo.toggle(first.id)
        assertThat(all().first { it.name == "Cebolla" }.checked).isFalse()
    }

    @Test
    fun newAdditionNeverMergesIntoCheckedLine() = runBlocking<Unit> {
        repo.addManual("Leche", 1.0, MeasureUnit.LITER)
        repo.toggle(all().single().id)
        repo.addManual("Leche", 1.0, MeasureUnit.LITER)
        assertThat(all()).hasSize(2)
    }

    @Test
    fun delete_andRestore_bringTheItemBack() = runBlocking<Unit> {
        repo.addManual("Pan", null, MeasureUnit.PIECE)
        val item = all().single()
        repo.delete(item.id)
        assertThat(all()).isEmpty()
        repo.restore(listOf(item))
        assertThat(all()).containsExactly(item)
    }

    @Test
    fun clearChecked_removesOnlyChecked_andRestoreReturnsThem() = runBlocking<Unit> {
        repo.addManual("Pan", null, MeasureUnit.PIECE)
        repo.addManual("Leche", 1.0, MeasureUnit.LITER)
        val pan = all().first { it.name == "Pan" }
        repo.toggle(pan.id)
        val removed = repo.clearChecked()
        assertThat(removed.map { it.name }).containsExactly("Pan")
        assertThat(all().map { it.name }).containsExactly("Leche")
        repo.restore(removed)
        assertThat(all().map { it.name }).containsExactly("Pan", "Leche")
    }

    @Test
    fun cupSizeComesFromTheSetting() = runBlocking<Unit> {
        val big = RoomShoppingRepository(db, Clock.systemUTC(), flowOf(250))
        big.addManual("Leche", 1.0, MeasureUnit.CUP)
        big.addManual("Leche", 0.0, MeasureUnit.MILLILITER)
        assertThat(all().single().quantity).isWithin(1e-9).of(250.0)
    }

    @Test
    fun setSection_movesTheItemToAnotherGroup() = runBlocking<Unit> {
        repo.addManual("Pan", null, MeasureUnit.PIECE)
        val id = all().single().id
        repo.setSection(id, ShopSection.DRINKS)
        val groups = repo.observe().first()
        assertThat(groups.keys.toList()).containsExactly(ShopSection.DRINKS)
        assertThat(groups.getValue(ShopSection.DRINKS).single().name).isEqualTo("Pan")
    }

    @Test
    fun delete_returnsTheCurrentRow_andRestoreBringsItBackExactly() = runBlocking<Unit> {
        repo.addManual("Leche", 1.0, MeasureUnit.LITER)
        repo.addManual("Leche", 0.2, MeasureUnit.LITER)
        val id = all().single().id
        repo.toggle(id)
        val removed = repo.delete(id)
        assertThat(removed).isNotNull()
        assertThat(removed!!.checked).isTrue()
        assertThat(removed.quantity).isWithin(1e-9).of(1.2)
        assertThat(all()).isEmpty()
        repo.restore(listOf(removed))
        val back = all().single()
        assertThat(back.checked).isTrue()
        assertThat(back.quantity).isWithin(1e-9).of(1.2)
    }

    @Test
    fun delete_ofAMissingId_returnsNull() = runBlocking<Unit> {
        assertThat(repo.delete("no-existe")).isNull()
    }
}
