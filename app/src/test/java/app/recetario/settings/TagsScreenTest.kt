package app.recetario.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.data.RecipeRepository
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class TagsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer()
    }

    @After
    fun tearDown() = container.database.close()

    @Test
    fun tags_deleteFailure_showsASnackbarInsteadOfCrashing() {
        runBlocking { container.recipeRepository.save(sampleRecipe("r", "Receta", tags = listOf("Otoño"))) }
        val failing = object : RecipeRepository by container.recipeRepository {
            override suspend fun deleteTag(name: String) = throw IOException("disco lleno")
        }
        rule.setContent { RecetarioTheme(ThemeChoice.CALIDO) { TagsRoute(container, onBack = {}, repo = failing) } }
        rule.waitFor(hasText("Otoño"))
        rule.onNodeWithTag("tag-delete-Otoño").performClick()
        rule.onNodeWithTag("tag-delete-confirm").performClick()
        rule.waitFor(hasText("No se pudo borrar la etiqueta."))
    }
}
