package app.recetario

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.backup.FakeDocuments
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.components.StarFillsKey
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import app.recetario.testing.waitForGone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recorrido de cierre de la entrega 2A sobre la app de verdad ([MainActivity]), una vez por tema:
 * 1. crear una receta («Arroz con tomate») y cocinarla hasta «¡Listo!»;
 * 2. guardarla en el diario con 4 estrellas;
 * 3. ver la línea «La hiciste 1 vez · hoy» (con sus estrellas dibujadas) en el detalle;
 * 4. añadir 2 ingredientes a la despensa («Arroz» y «Tomates», en plural);
 * 5. «¿Qué puedo hacer?»: la receta, completa;
 * 6. el filtro «Con mi despensa» en Recetas;
 * 7. exportar e importar con «Combinar», sin cambios (tampoco en el diario ni en la despensa).
 *
 * Los selectores de documentos los contesta la prueba; los documentos son [FakeDocuments]. Espera siempre a un estado
 * (nunca un tiempo fijo) y guarda una captura de cada paso en `app/build/capturas/recorrido-2a/`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class RecorridoEtapa2Test {
    @get:Rule
    val rule = createEmptyComposeRule()

    /** 19 de noviembre de 2026, 20:30 en Madrid. */
    private val clock = Clock.fixed(Instant.parse("2026-11-19T19:30:00Z"), ZoneId.of("Europe/Madrid"))
    private val documents = FakeDocuments()
    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity
    private lateinit var themeName: String

    @After
    fun tearDown() {
        // Primero se cierra la actividad (y su composición) y después la base de datos.
        scenario?.close()
        if (::container.isInitialized) container.database.close()
    }

    @Test
    fun calido() = walk(ThemeChoice.CALIDO)

    @Test
    fun fresco() = walk(ThemeChoice.FRESCO)

    @Test
    fun nocturno() = walk(ThemeChoice.NOCTURNO)

    private fun walk(theme: ThemeChoice) {
        themeName = theme.name.lowercase()
        container = installTestContainer(clock = clock, documents = documents)
        runBlocking {
            with(container.settingsRepository) {
                setTheme(theme)
                setAuto(false)
            }
            // Otra receta que la despensa no cubre: «Con mi despensa» la deja fuera.
            container.recipeRepository.save(
                sampleRecipe(
                    "tortilla", "Tortilla de patatas",
                    ingredients = listOf(
                        ingredient("t1", "Patatas", 500.0, MeasureUnit.GRAM),
                        ingredient("t2", "Huevos", 4.0, MeasureUnit.PIECE),
                        ingredient("t3", "Aceite", 100.0, MeasureUnit.MILLILITER),
                    ),
                ).copy(createdAt = clock.instant(), updatedAt = clock.instant()),
            )
        }

        scenario = ActivityScenario.launch(MainActivity::class.java).also { s -> s.onActivity { activity = it } }
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))
        rule.waitFor(hasText("Tortilla de patatas"))

        val recipe = createRecipe()
        cookAndLog(recipe)
        statsInDetail()
        fillPantry()
        whatCanIMake(recipe)
        pantryFilter()
        exportAndMerge()
    }

    // ---------------------------------------------------------------- 1. crear la receta

    private fun createRecipe(): Recipe {
        rule.onNodeWithContentDescription("Nueva receta").performClick()
        rule.waitFor(hasTestTag("editor-title"))
        rule.waitForIdle()

        rule.onNodeWithTag("editor-title").performTextInput(TITLE)
        rule.onNode(hasText("Comidas") and hasAnyAncestor(hasTestTag("editor-list"))).performClick()
        // Primero la que cambia de unidad: solo la fila nueva puede tener aún la de serie (g).
        addIngredient("Tomate", "3", unit = "unidad")
        addIngredient("Arroz", "200", unit = null) // g, la unidad de serie
        addIngredient("Sal", "5", unit = null) // un básico: no cuenta en la despensa
        addStep("Sofríe el tomate rallado.")
        addStep("Añade el arroz y cuécelo.")
        rule.onNodeWithTag("editor-list").performScrollToIndex(0)
        save("paso1-editor")

        rule.onNodeWithText("Guardar").performClick()
        rule.waitForGone(hasTestTag("editor-title"))

        val id = runBlocking { container.recipeRepository.observeSummaries().first() }.single { it.title == TITLE }.id
        val recipe = runBlocking { container.recipeRepository.observeRecipe(id).first() }!!
        assertThat(recipe.categoryId).isEqualTo(DefaultCategories.FOOD_ID)
        assertThat(recipe.ingredients.map { it.name }).containsExactly("Tomate", "Arroz", "Sal").inOrder()
        assertThat(recipe.steps).hasSize(2)
        return recipe
    }

    /** Un campo de texto con [tag] aún vacío: el de la fila que se acaba de añadir. */
    private fun emptyField(tag: String) = hasTestTag(tag) and SemanticsMatcher("vacío") { node ->
        node.config.getOrNull(SemanticsProperties.EditableText)?.text?.isEmpty() == true
    }

    private fun addIngredient(name: String, quantity: String, unit: String?) {
        val list = rule.onNodeWithTag("editor-list")
        list.performScrollToNode(hasText("Añadir ingrediente"))
        rule.onNodeWithText("Añadir ingrediente").performClick()
        val nameField = emptyField("ingredient-name")
        list.performScrollToNode(nameField)
        rule.onNode(nameField).performTextInput(name)
        val quantityField = emptyField("ingredient-quantity")
        list.performScrollToNode(quantityField)
        rule.onNode(quantityField).performTextInput(quantity)
        if (unit != null) {
            // Solo la fila nueva tiene aún la unidad de serie (g).
            rule.onNode(hasContentDescription("Unidad: g")).performClick()
            rule.waitFor(hasText(unit))
            rule.onNodeWithText(unit).performScrollTo().performClick()
            rule.waitFor(hasContentDescription("Unidad: $unit"))
        }
    }

    private fun addStep(text: String) {
        val list = rule.onNodeWithTag("editor-list")
        list.performScrollToNode(hasText("Añadir paso"))
        rule.onNodeWithText("Añadir paso").performClick()
        val field = emptyField("step-text")
        list.performScrollToNode(field)
        rule.onNode(field).performTextInput(text)
    }

    // ---------------------------------------------------------------- 1 y 2. cocinarla y guardarla en el diario

    private fun cookAndLog(recipe: Recipe) {
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitFor(hasText(TITLE))
        openDetail()
        rule.waitFor(hasText("Aún no la has hecho"))
        rule.onNodeWithTag("detail-cook").performClick()

        rule.waitFor(hasTestTag("cooking-list"))
        rule.waitFor(hasText("Paso 1 de 2"))
        markDone(1)
        rule.waitFor(hasText("Paso 2 de 2"))
        markDone(2)
        rule.waitFor(hasText("¡Listo!"))

        // Bajo «¡Listo!», «Guardar en el diario»: 4 estrellas y «Guardar».
        rule.waitFor(hasTestTag("diary-card"))
        val four = hasContentDescription("4 estrellas")
        rule.onNodeWithTag("cooking-list").performScrollToNode(four)
        rule.onNode(four).performClick()
        rule.waitFor(hasText("4 de 5 estrellas"))
        rule.onNodeWithTag("cooking-list").performScrollToNode(hasTestTag("diary-save"))
        save("paso2-diario-tarjeta")
        rule.onNodeWithTag("diary-save").performClick()
        rule.waitFor(hasTestTag("diary-saved"))
        rule.waitUntil(5_000) { diary().isNotEmpty() }
        val entry = diary().single()
        assertThat(entry.recipeId).isEqualTo(recipe.id)
        assertThat(entry.recipeTitle).isEqualTo(TITLE)
        assertThat(entry.rating).isEqualTo(4)
        assertThat(entry.date).isEqualTo(LocalDate.of(2026, 11, 19))
        save("paso2-diario-guardado")

        rule.onNodeWithTag("cooking-list").performScrollToNode(hasText("Volver a la receta"))
        rule.onNodeWithText("Volver a la receta").performClick()
        rule.waitFor(hasTestTag("detail-list"))
        rule.waitForGone(hasTestTag("cooking-list"))
    }

    private fun diary() = runBlocking { container.diaryRepository.observeEntries().first() }

    private fun markDone(step: Int) {
        val button = hasContentDescription("Marcar el paso $step como hecho")
        rule.waitFor(button)
        rule.onNodeWithTag("cooking-list").performScrollToNode(button)
        rule.onNode(button).performClick()
    }

    /** Desde Recetas: abre el detalle y espera a que cargue (el botón «▶ Modo cocina» solo sale con la receta). */
    private fun openDetail() {
        rule.onNodeWithText(TITLE).performClick()
        rule.waitFor(hasTestTag("detail-cook"))
        rule.waitForIdle()
    }

    // ---------------------------------------------------------------- 3. la línea del detalle

    private fun statsInDetail() {
        rule.waitFor(hasText("La hiciste 1 vez · hoy"))
        rule.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("detail-stats"))
        // La media (4) en estrellas dibujadas; TalkBack la lee en palabras.
        rule.onNodeWithTag("detail-stars", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(StarFillsKey, listOf(1f, 1f, 1f, 1f, 0f)))
        rule.onNode(hasText("La hiciste 1 vez · hoy"), useUnmergedTree = true)
            .assert(hasContentDescription("La hiciste 1 vez · hoy · valoración media: 4 de 5 estrellas"))
        save("paso3-detalle")

        // «Volver» está en la cabecera: primero se sube al principio de la lista.
        rule.onNodeWithTag("detail-list").performScrollToIndex(0)
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitForIdle()
    }

    // ---------------------------------------------------------------- 4. la despensa

    private fun fillPantry() {
        rule.onNodeWithTag("tab-shopping").performClick()
        rule.waitFor(hasTestTag("shopping-tab-pantry"))
        rule.onNodeWithTag("shopping-tab-pantry").performClick()
        rule.waitFor(hasText("Tu despensa está vacía. Añade lo que tienes en casa."))

        for (name in listOf("Arroz", "Tomates")) {
            rule.onNodeWithTag("pantry-field").performTextInput(name)
            rule.onNodeWithTag("pantry-add").performClick()
            rule.waitFor(hasContentDescription("Quitar «$name»"))
        }
        assertThat(runBlocking { container.pantryRepository.observe().first() }.map { it.name })
            .containsExactly("Arroz", "Tomates")
        save("paso4-despensa")
    }

    // ---------------------------------------------------------------- 5. «¿Qué puedo hacer?»

    private fun whatCanIMake(recipe: Recipe) {
        rule.onNodeWithTag("pantry-what-can-i-make").performClick()
        rule.waitFor(hasTestTag("what-can-i-make-list"))
        rule.waitFor(hasTestTag("what-can-i-make-${recipe.id}"))
        // «Tomates» cubre «Tomate» y la sal es un básico: está todo. A la tortilla le faltan patatas y huevos.
        rule.onNodeWithTag("what-can-i-make-${recipe.id}").assert(hasText("¡Tienes todo!"))
        rule.onNodeWithTag("what-can-i-make-tortilla").assert(hasText("Tienes 0 de 2 · falta: Patatas, Huevos"))
        save("paso5-que-puedo-hacer")

        rule.onNodeWithTag("settings-back").performClick()
        rule.waitForGone(hasTestTag("what-can-i-make-list"))
        rule.waitForIdle()
    }

    // ---------------------------------------------------------------- 6. «Con mi despensa»

    private fun pantryFilter() {
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitFor(hasText("Tortilla de patatas"))
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Filtros").performClick()
        rule.waitFor(hasTestTag("filter-pantry"))
        rule.onNodeWithTag("filter-pantry").performScrollTo().performClick()
        rule.onNodeWithTag("filter-pantry").assertIsOn()
        rule.waitFor(hasText("Ver 1 receta"))
        save("paso6-filtro")
        rule.onNodeWithText("Ver 1 receta").performScrollTo().performClick()
        rule.waitForGone(hasTestTag("filter-pantry"))

        rule.waitForGone(hasText("Tortilla de patatas"))
        rule.onNodeWithText(TITLE).assertExists()
        save("paso6-con-mi-despensa")
    }

    // ---------------------------------------------------------------- 7. exportar e importar con «Combinar»

    private fun exportAndMerge() {
        rule.onNodeWithTag("tab-home").performClick()
        rule.waitFor(hasTestTag("home-settings"))
        rule.waitForIdle()
        rule.onNodeWithTag("home-settings").performClick()
        rule.waitFor(hasTestTag("settings-backup"))
        rule.waitForIdle()
        rule.onNodeWithTag("settings-backup").performClick()
        rule.waitFor(hasTestTag("backup-export"))
        rule.waitForIdle()

        rule.onNodeWithTag("backup-export").performClick()
        answerPicker(Intent.ACTION_CREATE_DOCUMENT, EXPORT_URI)
        rule.waitFor(hasText("Copia guardada."), 10_000)
        assertThat(documents.files.getValue(EXPORT_URI)).isNotEmpty()
        // El aviso tapa «Importar copia»: se espera a que se vaya (4 s).
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForGone(hasText("Copia guardada."))

        val before = snapshot()
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("backup-import"))
        rule.onNodeWithTag("backup-import").performClick()
        answerPicker(Intent.ACTION_OPEN_DOCUMENT, EXPORT_URI)
        rule.waitFor(hasTestTag("import-merge"), 10_000)
        rule.onNodeWithTag("import-merge").performClick()
        // Es la misma copia: nada es más reciente ni falta nada (tampoco en el diario ni en la despensa).
        rule.waitFor(hasText("Copia importada: 0 recetas, 0 fotos."), 10_000)
        assertThat(snapshot()).isEqualTo(before)
        save("paso7-combinar")
    }

    /** Lo que «Combinar» no debe cambiar: recetas, compra, diario, despensa, fotos y copias del teléfono. */
    private fun snapshot(): List<Any?> = runBlocking {
        val repo = container.recipeRepository
        listOf(
            repo.observeSummaries().first().map { repo.observeRecipe(it.id).first() }.sortedBy { it?.id },
            container.shoppingRepository.observe().first().values.flatten().sortedBy { it.id },
            container.diaryRepository.observeEntries().first().sortedBy { it.id },
            container.pantryRepository.observe().first().sortedBy { it.id },
            container.photoStore.dir.list()?.sorted(),
            container.backupManager.localBackupsDir.list()?.sorted(),
        )
    }

    // ---------------------------------------------------------------- utilidades

    /** Contesta el último selector de documentos que abrió la app con [uri], como haría Android. */
    private fun answerPicker(action: String, uri: String) {
        rule.waitForIdle()
        val shadow = shadowOf(activity)
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "La app no abrió ningún selector" }
        assertThat(started.intent.action).isEqualTo(action)
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(uri)))
        rule.waitForIdle()
    }

    private fun save(name: String, settleMillis: Long = 1_500) {
        // La entrada escalonada espera con delay(): se adelanta el reloj para capturar el estado final.
        rule.mainClock.advanceTimeBy(settleMillis)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("user.dir"), "build/capturas/recorrido-2a").apply { mkdirs() }
        File(dir, "$name-$themeName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TITLE = "Arroz con tomate"
        const val EXPORT_URI = "content://recorrido-2a/Sazon-2026-11-19.zip"
    }
}
