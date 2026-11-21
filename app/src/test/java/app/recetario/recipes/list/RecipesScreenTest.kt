package app.recetario.recipes.list

import app.recetario.testing.CloseDatabaseRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.diary.CookLogEntry
import app.recetario.navigation.AppNavHost
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Recipe
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class RecipesScreenTest {
    /** Cierra la base de datos cuando la regla de Compose ya desechó la composición (CLAUDE.md, problemas conocidos). */
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    private val limonada = "Limonada con hierbabuena"
    private val bloody = "Bloody Mary"
    private val crema = "Crema de calabaza"

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    private fun openRecipes(vararg recipes: Recipe) {
        runBlocking { recipes.forEach { container.recipeRepository.save(it) } }
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { AppNavHost(container) } }
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.onNodeWithTag("recipes-search").assertIsDisplayed()
    }

    private fun seedThree() = openRecipes(
        sampleRecipe("limonada", limonada, DefaultCategories.DRINK_ID, minutes = 10, flavor = FlavorProfile(sweet = 1, sour = 3)),
        sampleRecipe("bloody", bloody, DefaultCategories.DRINK_ID, minutes = 5, flavor = FlavorProfile(salty = 2, sour = 1)),
        sampleRecipe("crema", crema, DefaultCategories.FOOD_ID, minutes = 35, flavor = FlavorProfile(sweet = 2, salty = 2)),
    )

    @Test
    fun search_limon_showsOnlyLimonada() {
        seedThree()
        rule.waitFor(hasText(bloody))
        rule.onNodeWithText(limonada).assertIsDisplayed()
        rule.onNodeWithText(crema).assertIsDisplayed()

        rule.onNodeWithTag("recipes-search").performTextInput("limon")

        rule.waitForGone(hasText(bloody))
        rule.onNodeWithText(limonada).assertIsDisplayed()
        rule.onNodeWithText(crema).assertDoesNotExist()
    }

    @Test
    fun categoryChip_filters() {
        seedThree()
        rule.waitFor(hasText(crema))

        rule.onNodeWithText("Bebidas").performClick()

        rule.waitForGone(hasText(crema))
        rule.onNodeWithText(limonada).assertIsDisplayed()
        rule.onNodeWithText(bloody).assertIsDisplayed()

        rule.onNodeWithText("Todas").performClick()
        rule.waitFor(hasText(crema))
    }

    @Test
    fun sort_mostCooked() {
        // La limonada 3 veces, la crema 1 y el Bloody Mary ninguna (las recetas, antes: sin receta no cuentan).
        runBlocking {
            container.recipeRepository.save(sampleRecipe("limonada", limonada, DefaultCategories.DRINK_ID))
            container.recipeRepository.save(sampleRecipe("bloody", bloody, DefaultCategories.DRINK_ID))
            container.recipeRepository.save(sampleRecipe("crema", crema, DefaultCategories.FOOD_ID))
            listOf("limonada" to limonada, "limonada" to limonada, "limonada" to limonada, "crema" to crema)
                .forEachIndexed { i, (id, title) ->
                    container.diaryRepository.save(
                        CookLogEntry("log-$i", id, title, LocalDate.of(2026, 11, 1 + i), null, "", emptyList(), Instant.EPOCH, Instant.EPOCH),
                    )
                }
        }
        openRecipes()
        rule.waitFor(hasText(crema))
        // «Recientes»: las tres se guardaron a la vez (mismo reloj), así que van por título.
        rule.waitUntil(5_000) { readingOrder() == listOf(bloody, crema, limonada) }

        rule.onNodeWithText("Recientes").performClick()
        rule.waitFor(hasText("Más cocinadas"))
        rule.onNodeWithText("Más cocinadas").performClick()

        // En la cuadrícula, el orden de lectura: de arriba abajo y de izquierda a derecha.
        rule.waitUntil(5_000) { readingOrder() == listOf(limonada, crema, bloody) }
    }

    @Test
    fun filter_conMiDespensa() {
        runBlocking {
            container.pantryRepository.add("limones")
            container.pantryRepository.add("hierbabuena")
        }
        openRecipes(
            sampleRecipe(
                "limonada", limonada, DefaultCategories.DRINK_ID,
                ingredients = listOf(
                    ingredient("l1", "Limón", 4.0, MeasureUnit.PIECE),
                    ingredient("l2", "Hierbabuena", null, MeasureUnit.PINCH),
                    ingredient("l3", "Agua", 1.0, MeasureUnit.LITER),
                    ingredient("l4", "Azúcar", 100.0, MeasureUnit.GRAM),
                ),
            ),
            sampleRecipe(
                "crema", crema, DefaultCategories.FOOD_ID,
                ingredients = listOf(ingredient("c1", "Calabaza", 800.0, MeasureUnit.GRAM), ingredient("c2", "Sal", null, MeasureUnit.PINCH)),
            ),
        )
        rule.waitFor(hasText(crema))

        rule.onNodeWithContentDescription("Filtros").performClick()
        rule.waitFor(hasTestTag("filter-pantry"))
        rule.onNodeWithTag("filter-pantry").performScrollTo().performClick()

        rule.waitForGone(hasText(crema))
        rule.onNodeWithText(limonada).assertExists()
        rule.onNodeWithTag("filter-pantry").assertIsOn()
    }

    private fun readingOrder(): List<String> = listOf(limonada, bloody, crema)
        .map { title -> title to rule.onNodeWithText(title, useUnmergedTree = true).getBoundsInRoot() }
        .sortedWith(compareBy({ it.second.top.value }, { it.second.left.value }))
        .map { it.first }

    @Test
    fun longTitle_isEllipsizedInCard() {
        val long = "Tarta de queso al horno con base de galleta, frutos rojos del bosque y un toque de limón, receta de la abuela Rosa"
            .padEnd(120, '.')
        assertThat(long).hasLength(120)
        openRecipes(sampleRecipe("larga", long, DefaultCategories.DESSERT_ID))
        rule.waitFor(hasText(long))

        val title = rule.onNodeWithText(long, useUnmergedTree = true)
        title.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        title.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertThat(layouts.single().lineCount).isAtMost(2)
        assertThat(layouts.single().hasVisualOverflow).isTrue()
    }
}
