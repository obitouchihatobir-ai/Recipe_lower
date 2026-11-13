package app.recetario.recipes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.core.data.AppDatabase
import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RecipeRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-11-13T10:00:00Z")
    private lateinit var db: AppDatabase
    private lateinit var photos: PhotoStore
    private lateinit var repo: RecipeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultCategories.callback)
            .allowMainThreadQueries()
            .build()
        photos = PhotoStore(tmp.newFolder("photos"))
        repo = RoomRecipeRepository(db, photos, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = db.close()

    private fun ingredient(n: Int) = Ingredient(
        id = "ing-$n", section = if (n == 1) "Masa" else null, name = "Ingrediente $n",
        quantity = n * 1.5, unit = MeasureUnit.GRAM, customUnit = null, note = "nota $n",
    )

    private fun recipe(
        id: String = "r1",
        ingredients: List<Ingredient> = listOf(ingredient(1), ingredient(2), ingredient(3)),
        steps: List<Step> = listOf(
            Step("s1", "Mezclar", listOf("a.jpg", "b.jpg"), 90, setOf("ing-2")),
            Step("s2", "Hornear", emptyList(), null, emptySet()),
        ),
        tags: List<String> = listOf("Rápido", "Cena"),
        categoryId: String = DefaultCategories.FOOD_ID,
        cover: String? = "cover.jpg",
    ) = Recipe(
        id = id, title = "Tarta", categoryId = categoryId, coverPhoto = cover, description = "Rica",
        totalMinutes = 45, servings = 4, difficulty = Difficulty.MEDIUM,
        flavor = FlavorProfile(sweet = 3, salty = 1, sour = 2, bitter = 0, spicy = 1, intensity = Intensity.INTENSE, rich = true),
        serveTemp = ServeTemp.HOT, moments = setOf(Moment.LUNCH, Moment.DINNER), alcoholic = false,
        course = Course.MAIN, ingredients = ingredients, steps = steps, tags = tags, notes = "Notas",
        favorite = false, sourceUrl = "https://example.com", aiGenerated = true,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"), updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun defaultCategories_existOnCreate() = runBlocking {
        val categories = repo.observeCategories().first().associateBy { it.id }
        assertThat(categories.keys).containsExactly("cat-comidas", "cat-bebidas", "cat-postres")
        assertThat(categories.getValue("cat-comidas").baseType).isEqualTo(BaseType.FOOD)
        assertThat(categories.getValue("cat-bebidas").baseType).isEqualTo(BaseType.DRINK)
        assertThat(categories.getValue("cat-postres").baseType).isEqualTo(BaseType.DESSERT)
        assertThat(categories.values.all { it.isDefault }).isTrue()
    }

    @Test
    fun save_thenObserve_roundTripsWholeAggregate() = runBlocking {
        val original = recipe()
        repo.save(original)

        val loaded = repo.observeRecipe("r1").first()

        // save() fija updatedAt con el reloj; todo lo demás vuelve igual y en el mismo orden.
        assertThat(loaded).isEqualTo(original.copy(updatedAt = now))
        assertThat(loaded!!.ingredients.map { it.id }).containsExactly("ing-1", "ing-2", "ing-3").inOrder()
        assertThat(loaded.steps[0].photos).containsExactly("a.jpg", "b.jpg").inOrder()
        assertThat(loaded.tags).containsExactly("Rápido", "Cena").inOrder()
    }

    @Test
    fun observeSummaries_listsIngredientNamesAndTags() = runBlocking {
        repo.save(recipe())
        val summary = repo.observeSummaries().first().single()
        assertThat(summary.id).isEqualTo("r1")
        assertThat(summary.ingredientNames).containsExactly("Ingrediente 1", "Ingrediente 2", "Ingrediente 3").inOrder()
        assertThat(summary.tags).containsExactly("Rápido", "Cena").inOrder()
        assertThat(summary.moments).containsExactly(Moment.LUNCH, Moment.DINNER)
    }

    @Test
    fun save_update_replacesChildren() = runBlocking {
        repo.save(recipe())
        repo.save(recipe(ingredients = listOf(ingredient(1)), steps = listOf(Step("s9", "Servir", emptyList(), null, setOf("ing-1")))))

        val loaded = repo.observeRecipe("r1").first()!!
        assertThat(loaded.ingredients.map { it.id }).containsExactly("ing-1")
        assertThat(loaded.steps.map { it.id }).containsExactly("s9")
        assertThat(count("ingredients")).isEqualTo(1)
        assertThat(count("steps")).isEqualTo(1)
        assertThat(count("step_photos")).isEqualTo(0)
        assertThat(count("step_ingredients")).isEqualTo(1)
    }

    @Test
    fun save_ignoresLinksToIngredientsOfOtherRecipes() = runBlocking {
        repo.save(recipe(steps = listOf(Step("s1", "Mezclar", emptyList(), null, setOf("ing-1", "no-existe")))))
        assertThat(repo.observeRecipe("r1").first()!!.steps.single().ingredientIds).containsExactly("ing-1")
    }

    @Test
    fun delete_cascades_and_deletesPhotoFiles() = runBlocking {
        listOf("cover.jpg", "a.jpg", "b.jpg", "keep.jpg").forEach { photos.file(it).writeText("x") }
        repo.save(recipe())
        repo.save(recipe(id = "r2", ingredients = emptyList(), steps = listOf(Step("s-other", "Otro", listOf("keep.jpg"), null, emptySet())), tags = emptyList(), cover = null))

        repo.delete("r1")

        assertThat(repo.observeRecipe("r1").first()).isNull()
        assertThat(photos.file("cover.jpg").exists()).isFalse()
        assertThat(photos.file("a.jpg").exists()).isFalse()
        assertThat(photos.file("b.jpg").exists()).isFalse()
        assertThat(photos.file("keep.jpg").exists()).isTrue()
        assertThat(count("ingredients")).isEqualTo(0)
        assertThat(count("steps")).isEqualTo(1)
        assertThat(count("step_photos")).isEqualTo(1)
        assertThat(count("recipe_tags")).isEqualTo(0)
        assertThat(repo.observeTags().first()).isEmpty()
    }

    @Test
    fun deleteCategory_reassignsRecipes() = runBlocking {
        repo.saveCategory(Category("cat-salsas", "Salsas", "🥫", "#AA3322", BaseType.FOOD, 3, false))
        repo.save(recipe(categoryId = "cat-salsas"))

        repo.deleteCategory("cat-salsas", reassignTo = DefaultCategories.FOOD_ID)

        assertThat(repo.observeRecipe("r1").first()!!.categoryId).isEqualTo("cat-comidas")
        assertThat(repo.observeCategories().first().map { it.id }).doesNotContain("cat-salsas")
    }

    @Test
    fun deleteDefaultCategory_throws() = runBlocking<Unit> {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.deleteCategory(DefaultCategories.DRINK_ID, reassignTo = DefaultCategories.FOOD_ID) }
        }
        assertThat(repo.observeCategories().first().map { it.id }).contains("cat-bebidas")
    }

    @Test
    fun tags_areDedupedByNormalizedName() = runBlocking {
        repo.save(recipe(id = "r1", tags = listOf("Rápido", "rapido", " RÁPIDO ")))
        repo.save(recipe(id = "r2", tags = listOf("rapido")))

        assertThat(repo.observeTags().first()).containsExactly("Rápido")
        assertThat(repo.observeRecipe("r1").first()!!.tags).containsExactly("Rápido")
        assertThat(repo.observeRecipe("r2").first()!!.tags).containsExactly("Rápido")
    }

    @Test
    fun renameTag_mergesWithExistingTag() = runBlocking {
        repo.save(recipe(id = "r1", tags = listOf("Fácil")))
        repo.save(recipe(id = "r2", tags = listOf("Sencillo")))

        repo.renameTag("Fácil", "sencillo")

        assertThat(repo.observeTags().first()).containsExactly("Sencillo")
        assertThat(repo.observeRecipe("r1").first()!!.tags).containsExactly("Sencillo")
    }

    @Test
    fun renameAndDeleteTag() = runBlocking {
        repo.save(recipe(tags = listOf("Rápido", "Cena")))

        repo.renameTag("rapido", "Express")
        assertThat(repo.observeRecipe("r1").first()!!.tags).containsExactly("Express", "Cena").inOrder()

        repo.deleteTag("CENA")
        assertThat(repo.observeRecipe("r1").first()!!.tags).containsExactly("Express")
    }

    @Test
    fun setFavorite_updatesFlag() = runBlocking {
        repo.save(recipe())
        repo.setFavorite("r1", true)
        assertThat(repo.observeRecipe("r1").first()!!.favorite).isTrue()
    }
}
