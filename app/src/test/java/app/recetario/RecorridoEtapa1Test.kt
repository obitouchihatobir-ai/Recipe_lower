package app.recetario

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.backup.FakeDocuments
import app.recetario.backup.unzip
import app.recetario.core.data.DefaultCategories
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.paletteFor
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.quantity.MeasureUnit
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Recorrido de cierre de la etapa 1 sobre la app de verdad ([MainActivity] con su [AppNavHost][app.recetario.navigation.AppNavHost]),
 * una vez por tema (Cálido, Fresco y Nocturno, elegidos a mano antes de abrir la app):
 * 1. crear una receta con foto (de la «galería»), 3 ingredientes («1,5» tazas de harina) y 2 pasos (uno con «20 min»);
 * 2. verla en Inicio y en Recetas, y encontrarla buscando sin tildes;
 * 3. abrir el detalle y pasar a 6 porciones;
 * 4. «Lo que necesito» y comprobarlo en Compra;
 * 5. modo cocina hasta «¡Listo!»;
 * 6. exportar e importar esa copia con «Combinar» (sin cambios ni duplicados);
 * 7. encender Automático: de noche, Nocturno; de día, el tema claro elegido.
 *
 * Los selectores del sistema (galería y documentos) los contesta la prueba con los resultados que recibe la actividad;
 * los documentos de fuera son [FakeDocuments]. Guarda una captura de cada paso en `app/build/capturas/recorrido/`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class RecorridoEtapa1Test {
    @get:Rule
    val rule = createEmptyComposeRule()

    /** 21:40 en Madrid (de noche); en el paso 7 pasa a las 13:10 del día siguiente. */
    private val clock = MutableClock(NIGHT, ZoneId.of("Europe/Madrid"))
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
        val dayTheme = if (theme == ThemeChoice.FRESCO) ThemeChoice.FRESCO else ThemeChoice.CALIDO
        runBlocking {
            with(container.settingsRepository) {
                setDayTheme(dayTheme)
                setTheme(theme)
                setAuto(false)
            }
            // Otra receta, para que la búsqueda tenga algo que dejar fuera.
            container.recipeRepository.save(sampleRecipe("tortilla", "Tortilla de patatas", minutes = 40).copy(createdAt = clock.instant()))
        }
        shadowOf(ApplicationProvider.getApplicationContext<Context>().contentResolver)
            .registerInputStreamSupplier(Uri.parse(PHOTO_URI)) { ByteArrayInputStream(photoBytes()) }

        scenario = ActivityScenario.launch(MainActivity::class.java).also { s -> s.onActivity { activity = it } }
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))
        rule.waitFor(hasText("Tortilla de patatas"))

        val recipe = createRecipe()
        findIt()
        sixServings()
        whatINeed()
        cook()
        exportAndMerge(recipe)
        automatic(dayTheme)
    }

    // ---------------------------------------------------------------- 1. crear la receta

    private fun createRecipe(): Recipe {
        rule.onNodeWithContentDescription("Nueva receta").performClick()
        rule.waitFor(hasTestTag("editor-title"))
        rule.waitForIdle()

        // La foto de portada, de la «galería»: el selector del sistema lo contesta la prueba.
        rule.onNodeWithContentDescription("Elegir la foto de portada de la galería").performClick()
        answerPicker(action = null, uri = PHOTO_URI)
        rule.waitFor(hasContentDescription("Quitar la foto de portada"), 10_000)

        rule.onNodeWithTag("editor-title").performTextInput("Bizcocho de limón")
        rule.onNode(hasText("Postres") and hasAnyAncestor(hasTestTag("editor-list"))).performClick()
        addIngredient("Harina", "1,5", unit = "taza")
        addIngredient("Huevos", "2", unit = "unidad")
        addIngredient("Azúcar", "200", unit = null) // g, la unidad de serie
        addStep("Mezcla la harina, el azúcar y los huevos.")
        addStep("Hornea 20 min a 180 °C.")
        val timerChip = hasText("⏱ Añadir temporizador de 20 min")
        editorList().performScrollToNode(timerChip)
        rule.onNode(timerChip).performClick()
        rule.waitFor(hasText("⏱ 20 min"))

        editorList().performScrollToIndex(0)
        save("paso1-editor", photo = true)
        editorList().performScrollToNode(hasText("Añadir ingrediente"))
        save("paso1-editor-ingredientes")
        editorList().performScrollToNode(hasText("⏱ 20 min"))
        save("paso1-editor-pasos")

        rule.onNodeWithText("Guardar").performClick()
        rule.waitForGone(hasTestTag("editor-title"))

        val id = runBlocking { container.recipeRepository.observeSummaries().first() }.single { it.title == "Bizcocho de limón" }.id
        val recipe = runBlocking { container.recipeRepository.observeRecipe(id).first() }!!
        assertThat(recipe.categoryId).isEqualTo(DefaultCategories.DESSERT_ID)
        assertThat(recipe.servings).isEqualTo(4)
        assertThat(recipe.coverPhoto).isNotNull()
        assertThat(container.photoStore.file(recipe.coverPhoto!!).length()).isGreaterThan(0L)
        assertThat(recipe.ingredients.map { Triple(it.name, it.quantity, it.unit) }).containsExactly(
            Triple("Harina", 1.5, MeasureUnit.CUP),
            Triple("Huevos", 2.0, MeasureUnit.PIECE),
            Triple("Azúcar", 200.0, MeasureUnit.GRAM),
        ).inOrder()
        assertThat(recipe.steps.map { it.text }).containsExactly(
            "Mezcla la harina, el azúcar y los huevos.", "Hornea 20 min a 180 °C.",
        ).inOrder()
        assertThat(recipe.steps.map { it.timerSeconds }).containsExactly(null, 1200).inOrder()
        return recipe
    }

    private fun editorList() = rule.onNodeWithTag("editor-list")

    /** Un campo de texto con [tag] aún vacío: el de la fila que se acaba de añadir. */
    private fun emptyField(tag: String) = hasTestTag(tag) and SemanticsMatcher("vacío") { node ->
        node.config.getOrNull(SemanticsProperties.EditableText)?.text?.isEmpty() == true
    }

    private fun addIngredient(name: String, quantity: String, unit: String?) {
        editorList().performScrollToNode(hasText("Añadir ingrediente"))
        rule.onNodeWithText("Añadir ingrediente").performClick()
        val nameField = emptyField("ingredient-name")
        editorList().performScrollToNode(nameField)
        rule.onNode(nameField).performTextInput(name)
        val quantityField = emptyField("ingredient-quantity")
        editorList().performScrollToNode(quantityField)
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
        editorList().performScrollToNode(hasText("Añadir paso"))
        rule.onNodeWithText("Añadir paso").performClick()
        val field = emptyField("step-text")
        editorList().performScrollToNode(field)
        rule.onNode(field).performTextInput(text)
    }

    // ---------------------------------------------------------------- 2. Inicio, Recetas y búsqueda sin tildes

    private fun findIt() {
        // «Guardar» vuelve a Inicio, que ya la muestra en «Recientes».
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))
        rule.waitFor(hasText("Bizcocho de limón"))
        save("paso2-inicio", photo = true)

        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitFor(hasText("Bizcocho de limón"))
        rule.waitFor(hasText("Tortilla de patatas"))
        save("paso2-recetas", photo = true)

        rule.onNodeWithTag("recipes-search").performTextInput("limon")
        rule.waitForGone(hasText("Tortilla de patatas"))
        rule.onNodeWithText("Bizcocho de limón").assertExists()
        save("paso2-busqueda", photo = true)
    }

    // ---------------------------------------------------------------- 3. detalle con 6 porciones

    private fun sixServings() {
        openDetail()
        save("paso3-detalle", photo = true)

        // «Ingredientes» arriba: el selector de porciones, las filas y «Lo que necesito» quedan a la vista y lejos
        // del botón fijo «▶ Modo cocina».
        rule.onNodeWithTag("detail-list").performScrollToKey("ingredients-title")
        rule.waitFor(hasText("4 porciones"))
        rule.waitFor(hasText("1½ tazas"))
        rule.waitFor(hasText("200 g"))
        rule.onNodeWithContentDescription("Añadir una porción").performClick()
        rule.onNodeWithContentDescription("Añadir una porción").performClick()
        rule.waitFor(hasText("6 porciones"))
        // 1,5 tazas × 6/4 = 2¼ tazas; 200 g → 300 g; 2 huevos → 3.
        rule.waitFor(hasText("2¼ tazas"))
        rule.waitFor(hasText("300 g"))
        save("paso3-seis-porciones")
    }

    /** Desde Recetas: abre el detalle y espera a que cargue (el botón «▶ Modo cocina» solo sale con la receta). */
    private fun openDetail() {
        rule.onNodeWithText("Bizcocho de limón").performClick()
        rule.waitFor(hasTestTag("detail-cook"))
        rule.waitForIdle()
    }

    /** «Volver» del detalle (está en la cabecera: primero se sube al principio de la lista). */
    private fun leaveDetail() {
        rule.onNodeWithTag("detail-list").performScrollToIndex(0)
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitForIdle()
    }

    // ---------------------------------------------------------------- 4. «Lo que necesito» y Compra

    private fun whatINeed() {
        rule.onNodeWithText(NEED).performClick()
        rule.waitFor(hasText("✓ 3 ingredientes añadidos a tu lista de compra"))
        save("paso4-lo-que-necesito", settleMillis = 300)
        val items = runBlocking { container.shoppingRepository.observe().first() }.values.flatten()
        assertThat(items.map { Triple(it.name, it.quantity, it.unit) }).containsExactly(
            Triple("Harina", 2.25, MeasureUnit.CUP),
            Triple("Huevos", 3.0, MeasureUnit.PIECE),
            Triple("Azúcar", 300.0, MeasureUnit.GRAM),
        )

        leaveDetail()
        rule.onNodeWithTag("tab-shopping").performClick()
        rule.waitFor(hasTestTag("shopping-screen"))
        rule.waitFor(hasText("Harina"))
        rule.waitFor(hasText("2¼ tazas"))
        rule.waitFor(hasText("Azúcar"))
        rule.waitFor(hasText("300 g"))
        save("paso4-compra")
    }

    // ---------------------------------------------------------------- 5. modo cocina hasta «¡Listo!»

    private fun cook() {
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasText("Bizcocho de limón"))
        rule.waitForIdle()
        openDetail()
        rule.onNodeWithTag("detail-cook").performClick()

        rule.waitFor(hasTestTag("cooking-list"))
        rule.waitFor(hasText("Paso 1 de 2"))
        save("paso5-cocina")
        markDone(1)
        rule.waitFor(hasText("Paso 2 de 2"))
        // El paso con «20 min» ofrece su temporizador.
        rule.onNodeWithTag("cooking-list").performScrollToNode(hasText("⏱ Iniciar 20:00"))
        save("paso5-cocina-temporizador")
        markDone(2)
        rule.waitFor(hasText("¡Listo!"))
        rule.waitFor(hasText("Has terminado todos los pasos."))
        rule.onNodeWithTag("cooking-list").performScrollToNode(hasText("Volver a la receta"))
        save("paso5-cocina-listo")

        rule.onNodeWithText("Volver a la receta").performClick()
        rule.waitFor(hasTestTag("detail-list"))
        rule.waitForGone(hasTestTag("cooking-list"))
    }

    private fun markDone(step: Int) {
        val button = hasContentDescription("Marcar el paso $step como hecho")
        rule.waitFor(button)
        rule.onNodeWithTag("cooking-list").performScrollToNode(button)
        rule.onNode(button).performClick()
    }

    // ---------------------------------------------------------------- 6. exportar e importar con «Combinar»

    private fun exportAndMerge(recipe: Recipe) {
        leaveDetail()
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
        val zip = unzip(documents.files.getValue(EXPORT_URI))
        assertThat(zip.keys).containsAtLeast("backup.json", "images/${recipe.coverPhoto}")
        rule.waitUntil(5_000) { runBlocking { container.settingsRepository.backupStatus.first().lastExternal } == clock.instant() }
        save("paso6-exportar")
        // El aviso tapa «Importar copia»: se espera a que se vaya (4 s).
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForGone(hasText("Copia guardada."))

        val before = snapshot()
        rule.onNodeWithTag("backup-list").performScrollToNode(hasTestTag("backup-import"))
        rule.onNodeWithTag("backup-import").performClick()
        answerPicker(Intent.ACTION_OPEN_DOCUMENT, EXPORT_URI)
        rule.waitFor(hasTestTag("import-merge"), 10_000)
        save("paso6-importar")
        rule.onNodeWithTag("import-merge").performClick()
        // Es la misma copia: nada es más reciente ni falta nada.
        rule.waitFor(hasText("Copia importada: 0 recetas, 0 fotos."), 10_000)
        assertThat(snapshot()).isEqualTo(before)
        assertThat(runBlocking { container.recipeRepository.observeSummaries().first() }.map { it.title })
            .containsExactly("Bizcocho de limón", "Tortilla de patatas")
        assertThat(runBlocking { container.recipeRepository.observeRecipe(recipe.id).first() }).isEqualTo(recipe)
        save("paso6-combinar")
    }

    /** Lo que «Combinar» no debe cambiar: recetas completas, compra, fotos y copias del teléfono (sin copia previa). */
    private fun snapshot(): List<Any?> = runBlocking {
        val repo = container.recipeRepository
        listOf(
            repo.observeSummaries().first().map { repo.observeRecipe(it.id).first() }.sortedBy { it?.id },
            container.shoppingRepository.observe().first().values.flatten().sortedBy { it.id },
            container.photoStore.dir.list()?.sorted(),
            container.backupManager.localBackupsDir.list()?.sorted(),
        )
    }

    // ---------------------------------------------------------------- 7. Automático

    private fun automatic(dayTheme: ThemeChoice) {
        rule.onNodeWithTag("settings-back").performClick()
        rule.waitFor(hasTestTag("settings-appearance"))
        rule.waitForIdle()
        rule.onNodeWithTag("settings-appearance").performClick()
        rule.waitFor(hasTestTag("auto-switch"))
        rule.waitForIdle()

        rule.onNodeWithTag("auto-switch").performClick()
        val day = LABELS.getValue(dayTheme)
        // 21:40: de noche, Nocturno (en la nota y en toda la app).
        rule.waitFor(hasText("De día: $day · De noche: Nocturno · Ahora: Nocturno"))
        waitForBackground(ThemeChoice.NOCTURNO)
        val settings = runBlocking { container.settingsRepository.themeSettings.first() }
        assertThat(settings.auto).isTrue()
        assertThat(settings.dayTheme).isEqualTo(dayTheme)
        save("paso7-automatico-noche")

        // 13:10 del día siguiente: Automático lo ve en el siguiente minuto y vuelve al tema claro elegido.
        clock.now = DAY
        rule.mainClock.advanceTimeBy(61_000)
        rule.waitFor(hasText("De día: $day · De noche: Nocturno · Ahora: $day"))
        waitForBackground(dayTheme)
        save("paso7-automatico-dia")
    }

    /** Espera a que el fondo de Apariencia (que pinta el tema de toda la app) sea el de [theme]. */
    private fun waitForBackground(theme: ThemeChoice) {
        val expected = paletteFor(theme).bg.toArgb()
        var last = 0
        repeat(80) {
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            val bitmap = rule.onNodeWithTag("appearance-screen").captureToImage().asAndroidBitmap()
            // Margen izquierdo, a tres cuartos de la altura: ahí solo está el fondo de la pantalla.
            last = bitmap.getPixel(8, bitmap.height * 3 / 4)
            if (sameColor(last, expected)) return
            Thread.sleep(20)
        }
        throw AssertionError("El fondo es #%08X y debería ser #%08X (%s)".format(last, expected, theme))
    }

    private fun sameColor(a: Int, b: Int): Boolean =
        listOf(16, 8, 0).all { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= 3 }

    // ---------------------------------------------------------------- utilidades

    /**
     * Contesta el último selector del sistema que abrió la app (galería, crear o abrir documento) con [uri], como
     * haría Android: la actividad recibe el resultado y se lo entrega al lanzador que lo pidió.
     */
    private fun answerPicker(action: String?, uri: String) {
        rule.waitForIdle()
        val shadow = shadowOf(activity)
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "La app no abrió ningún selector" }
        if (action != null) assertThat(started.intent.action).isEqualTo(action)
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(uri)))
        rule.waitForIdle()
    }

    private fun save(name: String, settleMillis: Long = 1_500, photo: Boolean = false) {
        // La entrada escalonada espera con delay(): se adelanta el reloj para capturar el estado final.
        rule.mainClock.advanceTimeBy(settleMillis)
        rule.waitForIdle()
        // Coil lee y decodifica la foto en otro hilo.
        if (photo) repeat(4) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("user.dir"), "build/capturas/recorrido").apply { mkdirs() }
        File(dir, "$name-$themeName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Una «foto» de bizcocho de limón: un JPEG de 800 × 600 dibujado en el momento. */
    private fun photoBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(android.graphics.Color.rgb(250, 226, 170))
        paint.color = android.graphics.Color.rgb(170, 104, 48)
        canvas.drawOval(170f, 250f, 630f, 520f, paint)
        paint.color = android.graphics.Color.rgb(232, 178, 96)
        canvas.drawOval(190f, 230f, 610f, 470f, paint)
        paint.color = android.graphics.Color.rgb(250, 222, 60)
        canvas.drawCircle(640f, 140f, 80f, paint)
        paint.color = android.graphics.Color.rgb(120, 170, 60)
        canvas.drawOval(560f, 40f, 640f, 80f, paint)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    /** Reloj que la prueba puede mover (el del paso 7). */
    private class MutableClock(@Volatile var now: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
        override fun instant(): Instant = now
    }

    private companion object {
        val NIGHT: Instant = Instant.parse("2026-11-13T20:40:00Z")
        val DAY: Instant = Instant.parse("2026-11-14T12:10:00Z")
        const val PHOTO_URI = "content://recorrido/bizcocho.jpg"
        const val EXPORT_URI = "content://recorrido/Sazon-2026-11-13.zip"
        const val NEED = "🛒 Lo que necesito → a la compra"
        val LABELS = mapOf(ThemeChoice.CALIDO to "Cálido", ThemeChoice.FRESCO to "Fresco", ThemeChoice.NOCTURNO to "Nocturno")
    }
}
