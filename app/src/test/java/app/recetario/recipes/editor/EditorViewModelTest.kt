package app.recetario.recipes.editor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.RecipeSummary
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class FakeRecipeRepository : RecipeRepository {
    val recipes = MutableStateFlow<Map<String, Recipe>>(emptyMap())
    override fun observeSummaries(): Flow<List<RecipeSummary>> = error("no se usa")
    override fun observeRecipe(id: String): Flow<Recipe?> = recipes.map { it[id] }
    override suspend fun save(recipe: Recipe) {
        recipes.value = recipes.value + (recipe.id to recipe)
    }
    override suspend fun delete(id: String) = error("no se usa")
    override suspend fun setFavorite(id: String, favorite: Boolean) = error("no se usa")
    override fun observeCategories(): Flow<List<Category>> =
        flowOf(listOf(Category(DefaultCategories.DRINK_ID, "Bebidas", "x", "#000000", BaseType.DRINK, 1, true)))
    override suspend fun saveCategory(category: Category) = error("no se usa")
    override suspend fun deleteCategory(id: String, reassignTo: String) = error("no se usa")
    override fun observeTags(): Flow<List<String>> = error("no se usa")
    override suspend fun renameTag(old: String, new: String) = error("no se usa")
    override suspend fun deleteTag(name: String) = error("no se usa")
    override suspend fun isPhotoReferenced(name: String): Boolean =
        recipes.value.values.any { r -> r.coverPhoto == name || r.steps.any { name in it.photos } }
}

private class TestClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneOffset = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
    override fun instant(): Instant = now
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-11-13T10:00:00Z")
    private val clock = TestClock(now)
    private lateinit var repo: FakeRecipeRepository
    private lateinit var photos: PhotoStore
    private lateinit var draftsDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRecipeRepository()
        photos = PhotoStore(tmp.newFolder("photos"))
        draftsDir = File(tmp.root, "drafts")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newVm(recipeId: String? = null, categoryId: String? = null): EditorViewModel {
        val args = buildMap<String, Any?> {
            recipeId?.let { put("id", it) }
            categoryId?.let { put("categoryId", it) }
        }
        val handle = SavedStateHandle(args)
        val vm = EditorViewModel(handle, repo, photos, DraftStore(draftsDir), clock, ioDispatcher = dispatcher)
        runCurrent()
        return vm
    }

    private fun draftStore() = DraftStore(draftsDir)

    private fun draftFile(key: String) = File(draftsDir, "$key.json")

    private fun EditorViewModel.fillValid() {
        onEvent(EditorEvent.SetTitle("Tarta"))
        onEvent(EditorEvent.SetCategory(DefaultCategories.DESSERT_ID, BaseType.DESSERT))
    }

    @Test
    fun save_withoutTitle_setsError_andReturnsNull() = runTest(dispatcher) {
        val vm = newVm()
        assertThat(vm.save()).isNull()
        assertThat(vm.state.value.errors[Field.TITLE]).isEqualTo("Ponle un nombre a la receta")
        assertThat(vm.state.value.errors[Field.CATEGORY]).isEqualTo("Elige una categoría")
        assertThat(repo.recipes.value).isEmpty()
        vm.onEvent(EditorEvent.SetTitle("Algo"))
        assertThat(vm.state.value.errors).doesNotContainKey(Field.TITLE)
    }

    @Test
    fun save_valid_persistsRecipe() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        vm.onEvent(EditorEvent.SetCookingMinutes("45"))
        vm.onEvent(EditorEvent.AddIngredient)
        val ing = vm.state.value.ingredients.single()
        vm.onEvent(EditorEvent.UpdateIngredient(ing.copy(name = " Harina ", quantityText = "1 1/2", unit = MeasureUnit.CUP)))
        vm.onEvent(EditorEvent.AddIngredient) // vacío: no se guarda
        vm.onEvent(EditorEvent.AddStep)
        val step = vm.state.value.steps.single()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Cuece 20 minutos"))
        vm.onEvent(EditorEvent.SetStepTimer(step.id, 1200))
        vm.onEvent(EditorEvent.SetStepIngredients(step.id, setOf(ing.id, "fantasma")))

        val id = vm.save()

        assertThat(id).isNotNull()
        val saved = repo.recipes.value.getValue(id!!)
        assertThat(saved.title).isEqualTo("Tarta")
        assertThat(saved.categoryId).isEqualTo(DefaultCategories.DESSERT_ID)
        assertThat(saved.totalMinutes).isEqualTo(45)
        assertThat(saved.createdAt).isEqualTo(now)
        assertThat(saved.ingredients).hasSize(1)
        assertThat(saved.ingredients[0].name).isEqualTo("Harina")
        assertThat(saved.ingredients[0].quantity).isEqualTo(1.5)
        assertThat(saved.steps.single().timerSeconds).isEqualTo(1200)
        assertThat(saved.steps.single().ingredientIds).containsExactly(ing.id)
    }

    @Test
    fun stepText_doesNotAutoFillTimer() = runTest(dispatcher) {
        val vm = newVm()
        vm.onEvent(EditorEvent.AddStep)
        val step = vm.state.value.steps.single()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Cuece 20 minutos"))
        assertThat(vm.state.value.steps.single().timerSeconds).isNull()
    }

    @Test
    fun save_unparseableQuantity_keepsTextAndReturnsError() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        vm.onEvent(EditorEvent.AddIngredient)
        val ing = vm.state.value.ingredients.single()
        vm.onEvent(EditorEvent.UpdateIngredient(ing.copy(name = "Harina", quantityText = "2-3")))
        assertThat(vm.save()).isNull()
        assertThat(vm.state.value.ingredients.single().quantityText).isEqualTo("2-3")
        assertThat(vm.state.value.rowErrors[ingredientQuantityErrorKey(ing.id)])
            .isEqualTo("No entiendo esta cantidad. Escribe, por ejemplo, 1,5 o ½, o déjala vacía y explícalo en la nota.")
        assertThat(repo.recipes.value).isEmpty()
        // Al corregirla, el error desaparece y se guarda.
        vm.onEvent(EditorEvent.UpdateIngredient(ing.copy(name = "Harina", quantityText = "2")))
        assertThat(vm.state.value.rowErrors).isEmpty()
        assertThat(vm.save()).isNotNull()
    }

    @Test
    fun save_minutesText_acceptsDurationsAndRejectsGarbage() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        vm.onEvent(EditorEvent.SetCookingMinutes("unos 40"))
        assertThat(vm.save()).isNull()
        assertThat(vm.state.value.cookingMinutesText).isEqualTo("unos 40")
        assertThat(vm.state.value.errors[Field.MINUTES]).isEqualTo("Escribe el tiempo en minutos, por ejemplo 35.")
        vm.onEvent(EditorEvent.SetCookingMinutes("1 h 30 min"))
        val id = vm.save()!!
        assertThat(repo.recipes.value.getValue(id).totalMinutes).isEqualTo(90)
    }

    @Test
    fun save_blankNameIngredientWithNote_orStepWithOnlyTimer_returnError_andEmptyRowsAreDropped() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        repeat(2) { vm.onEvent(EditorEvent.AddIngredient) }
        repeat(2) { vm.onEvent(EditorEvent.AddStep) }
        val (i1, _) = vm.state.value.ingredients
        val (s1, _) = vm.state.value.steps
        vm.onEvent(EditorEvent.UpdateIngredient(i1.copy(note = "al gusto")))
        vm.onEvent(EditorEvent.SetStepTimer(s1.id, 60))
        assertThat(vm.save()).isNull()
        assertThat(vm.state.value.rowErrors[ingredientNameErrorKey(i1.id)]).isEqualTo("Ponle nombre a este ingrediente.")
        assertThat(vm.state.value.rowErrors[stepTextErrorKey(s1.id)]).isEqualTo("Escribe qué hay que hacer en este paso.")
        assertThat(vm.state.value.rowErrors).hasSize(2)

        vm.onEvent(EditorEvent.UpdateIngredient(i1.copy(name = "Sal", note = "al gusto")))
        vm.onEvent(EditorEvent.SetStepText(s1.id, "Mezcla"))
        val id = vm.save()!!
        val saved = repo.recipes.value.getValue(id)
        assertThat(saved.ingredients).hasSize(1)
        assertThat(saved.steps).hasSize(1)
    }

    @Test
    fun pendingDraft_blocksEditsAndSave() = runTest(dispatcher) {
        val first = newVm()
        first.fillValid()
        advanceTimeBy(600)
        val vm = newVm()
        assertThat(vm.pendingDraft.value).isNotNull()
        vm.onEvent(EditorEvent.SetTitle("Otra"))
        assertThat(vm.state.value.title).isEmpty()
        assertThat(vm.save()).isNull()
        assertThat(repo.recipes.value).isEmpty()
        advanceTimeBy(600)
        assertThat(draftFile("new").exists()).isTrue()
        vm.recoverDraft()
        vm.onEvent(EditorEvent.SetTitle("Otra"))
        assertThat(vm.state.value.title).isEqualTo("Otra")
    }

    @Test
    fun discardPendingDraft_neverDeletesReferencedPhoto() = runTest(dispatcher) {
        val first = newVm()
        first.fillValid()
        photos.file("foto.jpg").also { it.parentFile!!.mkdirs() }.writeText("x")
        first.attachImportedPhoto("foto.jpg", PhotoTarget.Cover)
        advanceTimeBy(600)
        val id = first.save()!!
        // Un borrador de «nueva receta» que menciona la misma foto, ya guardada en otra receta.
        draftStore().save("new", first.state.value.copy(recipeId = "otra"))
        val vm = newVm()
        assertThat(vm.pendingDraft.value).isNotNull()
        vm.discardPendingDraft()
        assertThat(photos.file("foto.jpg").exists()).isTrue()
        assertThat(repo.recipes.value.getValue(id).coverPhoto).isEqualTo("foto.jpg")
    }

    @Test
    fun secondSave_keepsCreatedAt_andUsesRecipeDraftKey() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        val id = vm.save()!!
        clock.now = now.plusSeconds(3600)
        vm.onEvent(EditorEvent.SetTitle("Tarta 2"))
        advanceTimeBy(600)
        assertThat(draftFile(id).exists()).isTrue()
        assertThat(draftFile("new").exists()).isFalse()
        assertThat(vm.save()).isEqualTo(id)
        assertThat(repo.recipes.value.getValue(id).createdAt).isEqualTo(now)
        assertThat(repo.recipes.value.getValue(id).title).isEqualTo("Tarta 2")
        assertThat(draftFile(id).exists()).isFalse()
    }

    @Test
    fun routeArgs_idLoadsRecipe_categoryIdPreselectsForNew() = runTest(dispatcher) {
        val first = newVm()
        first.fillValid()
        val id = first.save()!!
        val editing = newVm(recipeId = id)
        assertThat(editing.state.value.recipeId).isEqualTo(id)
        assertThat(editing.state.value.title).isEqualTo("Tarta")

        val fresh = newVm(categoryId = DefaultCategories.DRINK_ID)
        assertThat(fresh.state.value.categoryId).isEqualTo(DefaultCategories.DRINK_ID)
        assertThat(fresh.state.value.serveTemp).isEqualTo(ServeTemp.COLD)
    }

    @Test
    fun reorderIngredients_keepsIds() = runTest(dispatcher) {
        val vm = newVm()
        repeat(3) { vm.onEvent(EditorEvent.AddIngredient) }
        val before = vm.state.value.ingredients.map { it.id }
        vm.onEvent(EditorEvent.MoveIngredient(0, 2))
        assertThat(vm.state.value.ingredients.map { it.id }).containsExactly(before[1], before[2], before[0]).inOrder()
    }

    @Test
    fun leavingWithChanges_savesDraft_andNextOpenOffersIt() = runTest(dispatcher) {
        val vm = newVm()
        assertThat(vm.pendingDraft.value).isNull()
        vm.onEvent(EditorEvent.SetTitle("Sopa de calabaza"))
        advanceTimeBy(499)
        assertThat(draftFile("new").exists()).isFalse()
        advanceTimeBy(2)
        assertThat(draftFile("new").exists()).isTrue()

        val reopened = newVm()
        assertThat(reopened.pendingDraft.value?.title).isEqualTo("Sopa de calabaza")
        assertThat(reopened.state.value.title).isEmpty()
        reopened.recoverDraft()
        assertThat(reopened.state.value.title).isEqualTo("Sopa de calabaza")
        assertThat(reopened.pendingDraft.value).isNull()

        val again = newVm()
        again.discardPendingDraft()
        assertThat(again.pendingDraft.value).isNull()
        assertThat(draftFile("new").exists()).isFalse()
    }

    @Test
    fun returningToTheInitialState_removesTheDraft() = runTest(dispatcher) {
        val vm = newVm()
        vm.onEvent(EditorEvent.SetTitle("A"))
        advanceTimeBy(600)
        assertThat(draftFile("new").exists()).isTrue()
        vm.onEvent(EditorEvent.SetTitle(""))
        advanceTimeBy(600)
        assertThat(draftFile("new").exists()).isFalse()
    }

    @Test
    fun successfulSave_clearsDraft() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        advanceTimeBy(600)
        assertThat(draftFile("new").exists()).isTrue()
        assertThat(vm.save()).isNotNull()
        advanceTimeBy(600)
        assertThat(draftFile("new").exists()).isFalse()
        assertThat(newVm().pendingDraft.value).isNull()
    }

    @Test
    fun chooseDrinkCategory_prefillsMomentsAndCold() = runTest(dispatcher) {
        val vm = newVm()
        vm.onEvent(EditorEvent.SetCategory(DefaultCategories.DRINK_ID, BaseType.DRINK))
        val s = vm.state.value
        assertThat(s.moments).containsExactly(Moment.LUNCH, Moment.SNACK, Moment.DINNER)
        assertThat(s.serveTemp).isEqualTo(ServeTemp.COLD)
        assertThat(s.defaultsSuggested).isTrue()

        // Siguen siendo editables; tras tocarlos ya no se sobrescriben al cambiar de categoría.
        vm.onEvent(EditorEvent.ToggleMoment(Moment.SNACK))
        vm.onEvent(EditorEvent.SetCategory(DefaultCategories.FOOD_ID, BaseType.FOOD))
        assertThat(vm.state.value.moments).containsExactly(Moment.LUNCH, Moment.DINNER)
        assertThat(vm.state.value.serveTemp).isEqualTo(ServeTemp.COLD)
    }

    @Test
    fun discard_deletesSessionPhotosAndDraft() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        photos.file("nueva.jpg").also { it.parentFile!!.mkdirs() }.writeText("x")
        vm.attachImportedPhoto("nueva.jpg", PhotoTarget.Cover)
        advanceTimeBy(600)
        assertThat(draftFile("new").exists()).isTrue()

        vm.discard()

        assertThat(photos.file("nueva.jpg").exists()).isFalse()
        assertThat(draftFile("new").exists()).isFalse()
        assertThat(vm.state.value.title).isEmpty()
        assertThat(vm.state.value.coverPhoto).isNull()
    }

    @Test
    fun editingExisting_loadsRecipe_andUsesItsOwnDraft() = runTest(dispatcher) {
        val first = newVm()
        first.fillValid()
        first.onEvent(EditorEvent.AddIngredient)
        val ing = first.state.value.ingredients.single()
        first.onEvent(EditorEvent.UpdateIngredient(ing.copy(name = "Azúcar", quantityText = "⅓", unit = MeasureUnit.CUP)))
        val id = first.save()!!

        val vm = newVm(id)
        assertThat(vm.state.value.title).isEqualTo("Tarta")
        assertThat(vm.state.value.ingredients.single().quantityText).isEqualTo("1/3")
        assertThat(vm.pendingDraft.value).isNull()

        vm.onEvent(EditorEvent.SetTitle("Tarta de queso"))
        advanceTimeBy(600)
        assertThat(draftFile(id).exists()).isTrue()
        assertThat(draftFile("new").exists()).isFalse()
        assertThat(newVm(id).pendingDraft.value?.title).isEqualTo("Tarta de queso")
    }

    @Test
    fun quantityText_roundTripsThroughParser() {
        for (v in listOf(2.0, 0.5, 1.5, 0.25, 2.75, 1.0 / 3, 0.125, 0.4)) {
            val parsed = app.recetario.recipes.quantity.parseQuantity(quantityToText(v))!!
            assertThat(parsed).isWithin(0.001).of(v)
        }
    }

    @Test
    fun save_partiallyUnderstoodMinutes_isAnError() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        vm.onEvent(EditorEvent.SetCookingMinutes("1 hora y media"))
        assertThat(vm.save()).isNull()
        assertThat(vm.state.value.errors[Field.MINUTES]).isEqualTo("Escribe el tiempo en minutos, por ejemplo 35.")
        assertThat(vm.state.value.cookingMinutesText).isEqualTo("1 hora y media")
    }

    @Test
    fun save_blankNameIngredientWithOnlySectionOrCustomUnit_returnsError() = runTest(dispatcher) {
        val vm = newVm()
        vm.fillValid()
        repeat(2) { vm.onEvent(EditorEvent.AddIngredient) }
        val (a, b) = vm.state.value.ingredients
        vm.onEvent(EditorEvent.UpdateIngredient(a.copy(section = "Masa")))
        vm.onEvent(EditorEvent.UpdateIngredient(b.copy(unit = MeasureUnit.CUSTOM, customUnit = "puñado")))
        assertThat(vm.save()).isNull()
        assertThat(vm.state.value.rowErrors.keys).containsExactly(ingredientNameErrorKey(a.id), ingredientNameErrorKey(b.id))
    }

    @Test
    fun pendingDraft_blocksDiscard() = runTest(dispatcher) {
        val first = newVm()
        first.fillValid()
        advanceTimeBy(600)
        val vm = newVm()
        assertThat(vm.pendingDraft.value).isNotNull()
        vm.discard()
        assertThat(draftFile("new").exists()).isTrue()
        assertThat(vm.pendingDraft.value).isNotNull()
    }

    @Test
    fun unknownCategoryIdArg_isNotPreselected() = runTest(dispatcher) {
        val vm = newVm(categoryId = "no-existe")
        assertThat(vm.state.value.categoryId).isNull()
    }

    /** Editor con esos ingredientes (ya con nombre) y un paso vacío. */
    private fun TestScope.vmWithIngredients(vararg names: String): EditorViewModel {
        val vm = newVm()
        repeat(names.size) { vm.onEvent(EditorEvent.AddIngredient) }
        vm.state.value.ingredients.zip(names) { ing, name -> vm.onEvent(EditorEvent.UpdateIngredient(ing.copy(name = name))) }
        vm.onEvent(EditorEvent.AddStep)
        return vm
    }

    @Test
    fun stepText_preselectsSuggestedIngredients() = runTest(dispatcher) {
        val vm = vmWithIngredients("Calabaza", "Caldo de verduras", "Crema")
        val (calabaza, caldo, _) = vm.state.value.ingredients
        val step = vm.state.value.steps.single()

        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza"))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(calabaza.id)

        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza y el caldo"))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(calabaza.id, caldo.id)
    }

    @Test
    fun removedSuggestion_isNotReAddedOnFurtherTyping_andIsKeptInTheDraft() = runTest(dispatcher) {
        val vm = vmWithIngredients("Calabaza", "Caldo de verduras")
        val (calabaza, caldo) = vm.state.value.ingredients
        val step = vm.state.value.steps.single()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza"))

        vm.onEvent(EditorEvent.SetStepIngredients(step.id, emptySet())) // la persona la quita
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza y remueve"))
        assertThat(vm.state.value.steps.single().ingredientIds).isEmpty()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade el caldo"))
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade el caldo y la calabaza"))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(caldo.id)
        assertThat(vm.state.value.steps.single().dismissedIngredientIds).containsExactly(calabaza.id)

        advanceTimeBy(600)
        val reopened = newVm()
        assertThat(reopened.pendingDraft.value!!.steps.single().dismissedIngredientIds).containsExactly(calabaza.id)

        // Volver a marcarla a mano la acepta de nuevo.
        vm.onEvent(EditorEvent.SetStepIngredients(step.id, setOf(caldo.id, calabaza.id)))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(caldo.id, calabaza.id)
        assertThat(vm.state.value.steps.single().dismissedIngredientIds).isEmpty()
    }

    @Test
    fun undoRemoveIngredient_restoresItsPositionAndStepLinks() = runTest(dispatcher) {
        val vm = vmWithIngredients("Azúcar", "Harina", "Leche")
        val (azucar, harina, leche) = vm.state.value.ingredients
        val step = vm.state.value.steps.single()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Tamiza la harina"))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(harina.id)

        val removal = vm.state.value.removalOfIngredient(harina.id)!!
        vm.onEvent(EditorEvent.RemoveIngredient(harina.id))
        assertThat(vm.state.value.ingredients.map { it.id }).containsExactly(azucar.id, leche.id).inOrder()
        assertThat(vm.state.value.steps.single().ingredientIds).isEmpty()

        vm.onEvent(EditorEvent.UndoRemoval(removal))
        assertThat(vm.state.value.ingredients).containsExactly(azucar, harina, leche).inOrder()
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(harina.id)
        // Deshacer dos veces no lo duplica.
        vm.onEvent(EditorEvent.UndoRemoval(removal))
        assertThat(vm.state.value.ingredients).hasSize(3)
    }

    @Test
    fun undoRemoveStep_restoresItsPositionPhotosAndTimer() = runTest(dispatcher) {
        val vm = newVm()
        repeat(3) { vm.onEvent(EditorEvent.AddStep) }
        val (s1, s2, s3) = vm.state.value.steps
        vm.onEvent(EditorEvent.SetStepText(s2.id, "Hornea 5 minutos"))
        vm.onEvent(EditorEvent.SetStepTimer(s2.id, 300))
        vm.attachImportedPhoto("paso.jpg", PhotoTarget.StepPhoto(s2.id))
        val before = vm.state.value.steps[1]

        val removal = vm.state.value.removalOfStep(s2.id)!!
        vm.onEvent(EditorEvent.RemoveStep(s2.id))
        assertThat(vm.state.value.steps.map { it.id }).containsExactly(s1.id, s3.id).inOrder()

        vm.onEvent(EditorEvent.UndoRemoval(removal))
        assertThat(vm.state.value.steps.map { it.id }).containsExactly(s1.id, s2.id, s3.id).inOrder()
        assertThat(vm.state.value.steps[1]).isEqualTo(before)
        assertThat(vm.state.value.steps[1].photos).containsExactly("paso.jpg")
        assertThat(vm.state.value.steps[1].timerSeconds).isEqualTo(300)
    }

    // Ronda 2: los enlaces del paso se derivan siempre del texto, de los ingredientes y de lo que tocó la persona.

    @Test
    fun halfTypedWord_doesNotLeaveAnIngredientSelected() = runTest(dispatcher) {
        val vm = vmWithIngredients("Sal")
        val step = vm.state.value.steps.single()
        for (text in listOf("S", "Sa", "Sal", "Salt", "Saltea")) vm.onEvent(EditorEvent.SetStepText(step.id, text))
        assertThat(vm.state.value.steps.single().ingredientIds).isEmpty()
    }

    @Test
    fun deletingTheWord_unselectsTheIngredient() = runTest(dispatcher) {
        val vm = vmWithIngredients("Calabaza")
        val step = vm.state.value.steps.single()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza"))
        assertThat(vm.state.value.steps.single().ingredientIds).hasSize(1)
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la "))
        assertThat(vm.state.value.steps.single().ingredientIds).isEmpty()
    }

    @Test
    fun ingredientAddedAfterTheText_isSelected() = runTest(dispatcher) {
        val vm = newVm()
        vm.onEvent(EditorEvent.AddStep)
        val step = vm.state.value.steps.single()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade el caldo"))
        vm.onEvent(EditorEvent.AddIngredient)
        val caldo = vm.state.value.ingredients.single()
        vm.onEvent(EditorEvent.UpdateIngredient(caldo.copy(name = "Caldo")))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(caldo.id)
    }

    @Test
    fun dismissedSuggestionStaysOff_andManualLinkSurvivesTextChanges() = runTest(dispatcher) {
        val vm = vmWithIngredients("Calabaza", "Nata")
        val (calabaza, nata) = vm.state.value.ingredients
        val step = vm.state.value.steps.single()
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza"))
        vm.onEvent(EditorEvent.SetStepIngredients(step.id, emptySet()))
        vm.onEvent(EditorEvent.SetStepIngredients(step.id, setOf(nata.id))) // a mano, sin sugerencia
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza troceada y remueve"))
        val s = vm.state.value.steps.single()
        assertThat(s.ingredientIds).containsExactly(nata.id)
        assertThat(s.manualIngredientIds).containsExactly(nata.id)
        assertThat(s.dismissedIngredientIds).containsExactly(calabaza.id)
    }

    @Test
    fun loadedRecipe_keepsItsExactLinks() = runTest(dispatcher) {
        val first = vmWithIngredients("Calabaza", "Caldo de verduras")
        first.fillValid()
        val (calabaza, caldo) = first.state.value.ingredients
        val step = first.state.value.steps.single()
        first.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza y el caldo"))
        first.onEvent(EditorEvent.SetStepIngredients(step.id, setOf(calabaza.id))) // quita el caldo
        val id = first.save()!!
        assertThat(repo.recipes.value.getValue(id).steps.single().ingredientIds).containsExactly(calabaza.id)

        val vm = newVm(recipeId = id)
        val loaded = vm.state.value.steps.single()
        assertThat(loaded.ingredientIds).containsExactly(calabaza.id)
        assertThat(loaded.manualIngredientIds).containsExactly(calabaza.id)
        // Seguir escribiendo no enlaza el caldo, que la receta guardada no usaba.
        vm.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza y el caldo, y cuece"))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(calabaza.id)
        assertThat(caldo.id).isNotIn(vm.state.value.steps.single().ingredientIds)
    }

    @Test
    fun oldDraftWithoutBookkeeping_loadsItsLinksAsManual() = runTest(dispatcher) {
        draftsDir.mkdirs()
        draftFile("new").writeText(
            """{"recipeId":"viejo","title":"Gazpacho","ingredients":[{"id":"i1","name":"Tomate"}],""" +
                """"steps":[{"id":"s1","text":"Tritura todo","ingredientIds":["i1"]}]}""",
        )
        val vm = newVm()
        val step = vm.pendingDraft.value!!.steps.single()
        assertThat(step.ingredientIds).containsExactly("i1")
        assertThat(step.manualIngredientIds).containsExactly("i1")
        assertThat(step.dismissedIngredientIds).isEmpty()
    }

    @Test
    fun suggestionToggledOffAndOn_doesNotWriteAPhantomDraft() = runTest(dispatcher) {
        val first = vmWithIngredients("Calabaza")
        first.fillValid()
        val step = first.state.value.steps.single()
        first.onEvent(EditorEvent.SetStepText(step.id, "Añade la calabaza"))
        val id = first.save()!!

        val vm = newVm(recipeId = id)
        val calabaza = vm.state.value.ingredients.single()
        vm.onEvent(EditorEvent.SetStepIngredients(step.id, emptySet()))
        vm.onEvent(EditorEvent.SetStepIngredients(step.id, setOf(calabaza.id)))
        assertThat(vm.state.value.steps.single().ingredientIds).containsExactly(calabaza.id)
        advanceTimeBy(600)

        assertThat(draftFile(id).exists()).isFalse()
        assertThat(newVm(recipeId = id).pendingDraft.value).isNull()
    }

    @Test
    fun undoAfterASaveStarted_isIgnored_butWorksAgainAfterAFailedSave() = runTest(dispatcher) {
        val vm = vmWithIngredients("Harina")
        val harina = vm.state.value.ingredients.single()
        val removal = vm.state.value.removalOfIngredient(harina.id)!!
        vm.onEvent(EditorEvent.RemoveIngredient(harina.id))
        assertThat(vm.save()).isNull() // sin título ni categoría: no se guarda
        vm.onEvent(EditorEvent.UndoRemoval(removal))
        assertThat(vm.state.value.ingredients).containsExactly(harina)

        val again = vm.state.value.removalOfIngredient(harina.id)!!
        vm.onEvent(EditorEvent.RemoveIngredient(harina.id))
        vm.fillValid()
        val id = vm.save()!!
        vm.onEvent(EditorEvent.UndoRemoval(again))
        assertThat(vm.state.value.ingredients).isEmpty()
        assertThat(repo.recipes.value.getValue(id).ingredients).isEmpty()
        advanceTimeBy(600)
        assertThat(draftFile(id).exists()).isFalse()
    }
}
