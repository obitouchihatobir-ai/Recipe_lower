package app.recetario.pairing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import app.recetario.AppContainer
import app.recetario.core.data.DefaultCategories
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.sampleRecipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Ingredientes del mole de prueba (la guía de sabores conoce el chile poblano, el chocolate y el ajonjolí). */
val MOLE_INGREDIENTS = listOf("Chile poblano", "Chocolate", "Ajonjolí")

/** Un mole (principal), una limonada propia (bebida) y un flan (postre). */
fun seedPairingRecipes(container: AppContainer) = runBlocking {
    val repo = container.recipeRepository
    repo.save(
        sampleRecipe(
            "mole", "Mole poblano",
            flavor = FlavorProfile(sweet = 1, spicy = 2, intensity = Intensity.INTENSE, rich = true),
            serveTemp = ServeTemp.HOT,
            ingredients = MOLE_INGREDIENTS.mapIndexed { i, name -> ingredient("m$i", name, 100.0, MeasureUnit.GRAM) },
        ).copy(course = Course.MAIN),
    )
    repo.save(
        sampleRecipe(
            "limonada", "Limonada de la casa",
            categoryId = DefaultCategories.DRINK_ID,
            flavor = FlavorProfile(sweet = 2, sour = 3, intensity = Intensity.LIGHT),
            serveTemp = ServeTemp.COLD,
        ).copy(alcoholic = false),
    )
    repo.save(
        sampleRecipe(
            "flan", "Flan",
            categoryId = DefaultCategories.DESSERT_ID,
            flavor = FlavorProfile(sweet = 3, rich = true),
            serveTemp = ServeTemp.COLD,
        ),
    )
}

fun pairingData(container: AppContainer): PairingData = runBlocking { container.pairingRepository.observeData().first() }

fun ratings(container: AppContainer): Map<String, Boolean> = runBlocking { container.pairingRepository.observeRatings().first() }

/** Nodos cuya etiqueta de prueba empieza por [prefix]. */
fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag empieza por $prefix") {
    it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
}

/** Desplaza la lista [listTag] hasta [matcher], esperando a que los datos lleguen (Room va en otro hilo). */
fun ComposeTestRule.scrollUntil(listTag: String, matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) {
        try {
            onNodeWithTag(listTag).performScrollToNode(matcher)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
