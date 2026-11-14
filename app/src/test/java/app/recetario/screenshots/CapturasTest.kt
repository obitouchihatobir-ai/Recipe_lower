package app.recetario.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.navigation.AppNavHost
import app.recetario.navigation.RecipeDetail
import app.recetario.navigation.RecipeEditor
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de Inicio, Recetas y el editor en los tres temas, en `app/build/capturas/` (CI las sube como
 * artefacto `capturas`). No comprueban píxeles: sirven para revisarlas a ojo frente a los bocetos.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class CapturasTest {
    @get:Rule
    val rule = createComposeRule()

    private var container: AppContainer? = null

    @After
    fun tearDown() {
        container?.database?.close()
    }

    @Test
    fun calido() = captureAll(ThemeChoice.CALIDO)

    @Test
    fun fresco() = captureAll(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = captureAll(ThemeChoice.NOCTURNO)

    private fun captureAll(theme: ThemeChoice) {
        val c = installTestContainer().also { container = it }
        runBlocking { sampleRecipes().forEach { c.recipeRepository.save(it) } }
        lateinit var nav: NavHostController
        rule.setContent {
            nav = rememberNavController()
            RecetarioTheme(theme) { AppNavHost(c, navController = nav) }
        }
        val name = theme.name.lowercase()
        rule.waitFor(hasText("Limonada con hierbabuena"))
        save("inicio-$name")

        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasText("Crema de calabaza"))
        save("recetas-$name")

        rule.runOnIdle { nav.navigate(RecipeEditor(id = "crema")) }
        rule.waitFor(hasTestTag("editor-title"))
        save("editor-$name")
        rule.onNodeWithTag("editor-list").performScrollToNode(hasTestTag("flavor-section"))
        save("editor-sabor-$name")
        rule.onNodeWithTag("editor-list").performScrollToNode(hasTestTag("ingredient-name"))
        save("editor-ingredientes-$name")
        rule.onNodeWithTag("editor-list").performScrollToNode(hasTestTag("step-text"))
        save("editor-pasos-$name")

        rule.runOnIdle { nav.navigate(RecipeDetail("calabaza")) }
        rule.waitFor(hasTestTag("detail-list"))
        rule.waitFor(hasText("Sal y pimienta"))
        save("detalle-$name")
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Sal y pimienta"))
        save("detalle-ingredientes-$name")
        rule.onNodeWithTag("detail-list").performScrollToNode(hasText("Mis notas"))
        save("detalle-pasos-notas-$name")
    }

    private fun save(name: String) {
        // La entrada escalonada espera con delay(): se adelanta el reloj para capturar el estado final.
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("user.dir"), "build/capturas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleRecipes() = listOf(
        sampleRecipe(
            "limonada", "Limonada con hierbabuena", DefaultCategories.DRINK_ID, minutes = 10,
            flavor = FlavorProfile(sweet = 1, sour = 3), updatedAt = 600, favorite = true,
        ),
        sampleRecipe(
            "bloody", "Bloody Mary", DefaultCategories.DRINK_ID, minutes = 5,
            flavor = FlavorProfile(salty = 2, sour = 1, spicy = 1), updatedAt = 500,
        ),
        sampleRecipe(
            "crema", "Crema de calabaza", DefaultCategories.FOOD_ID, minutes = 35,
            flavor = FlavorProfile(sweet = 3, salty = 3, sour = 1, spicy = 1), updatedAt = 400, favorite = true,
            serveTemp = ServeTemp.HOT, moments = setOf(Moment.LUNCH, Moment.DINNER), tags = listOf("Vegetariana", "Otoño"),
            ingredients = listOf(
                ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
                ingredient("i2", "Cebolla", 1.0, MeasureUnit.PIECE),
                ingredient("i3", "Ajo", 2.0, MeasureUnit.CLOVE),
                ingredient("i4", "Caldo de verduras", 750.0, MeasureUnit.MILLILITER),
                ingredient("i5", "Nata", 100.0, MeasureUnit.MILLILITER, section = "Para servir"),
            ),
            steps = listOf(
                Step("s1", "Pela la calabaza, quítale las semillas y córtala en cubos medianos.", emptyList(), null, setOf("i1")),
                Step("s2", "Añade la calabaza y el caldo. Cuece 20 minutos a fuego medio.", emptyList(), null, setOf("i1", "i4")),
            ),
        ),
        sampleRecipe(
            "calabaza", "Crema de calabaza", DefaultCategories.FOOD_ID, minutes = 35,
            flavor = FlavorProfile(sweet = 3, salty = 3, sour = 1, spicy = 1), updatedAt = 450, favorite = true,
            serveTemp = ServeTemp.HOT, tags = listOf("Vegetariana"),
            ingredients = listOf(
                ingredient("c1", "Calabaza", 800.0, MeasureUnit.GRAM),
                ingredient("c2", "Cebolla", 1.0, MeasureUnit.PIECE),
                ingredient("c3", "Ajo", 2.0, MeasureUnit.CLOVE),
                ingredient("c4", "Caldo de verduras", 750.0, MeasureUnit.MILLILITER),
                ingredient("c5", "Crema / nata", 100.0, MeasureUnit.MILLILITER),
                ingredient("c6", "Mantequilla", 20.0, MeasureUnit.GRAM),
                ingredient("c7", "Nuez moscada", null, MeasureUnit.PINCH),
                ingredient("c8", "Sal y pimienta", null, MeasureUnit.TO_TASTE),
            ),
            steps = listOf(
                Step("p1", "Pela la calabaza, quítale las semillas y córtala en cubos medianos.", emptyList(), null, setOf("c1")),
                Step("p2", "Sofríe la cebolla y el ajo picados en la mantequilla hasta que se ablanden.", emptyList(), 600, setOf("c2", "c3", "c6")),
                Step("p3", "Añade la calabaza y el caldo. Cuece 20 minutos a fuego medio.", emptyList(), 1200, setOf("c1", "c4")),
                Step("p4", "Tritura, añade la nata, la nuez moscada, sal y pimienta, y sirve caliente.", emptyList(), null, setOf("c5", "c7", "c8")),
            ),
        ).copy(notes = "La próxima vez: un toque de jengibre y menos caldo."),
        sampleRecipe(
            "flan", "Flan casero de la abuela con caramelo líquido y un toque de vainilla", DefaultCategories.DESSERT_ID,
            minutes = 50, flavor = FlavorProfile(sweet = 3), updatedAt = 300,
        ),
        sampleRecipe("tortilla", "Tortilla de patatas", DefaultCategories.FOOD_ID, minutes = 40, flavor = FlavorProfile(salty = 2), updatedAt = 200),
        sampleRecipe("chocolate", "Chocolate caliente", DefaultCategories.DRINK_ID, minutes = 10, flavor = FlavorProfile(sweet = 3, bitter = 1), updatedAt = 100),
    )
}
