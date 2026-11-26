package app.recetario

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.backup.FakeDocuments
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.plan.MealSlot
import app.recetario.plan.PlannedMeal
import app.recetario.plan.filledMessage
import app.recetario.plan.weekOf
import app.recetario.plan.weekSlotTag
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.share.CARD_SAVED
import app.recetario.share.CardFlipAngle
import app.recetario.share.FakePdfDocument
import app.recetario.share.isPdf
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Recorrido de cierre de la entrega 2C sobre la app de verdad ([MainActivity]), una vez por tema:
 * 1. tres recetas;
 * 2. la pestaña Semana: «Rellenar con sugerencias», «Deshacer», otra vez «Rellenar» y un hueco cambiado a mano;
 * 3. «Generar lista de compra»: los ingredientes salen en la Lista;
 * 4. desde el detalle, la ficha cuadrada «Clásico», que se comparte como un PNG de 1080 × 1080;
 * 5. el PDF A4, que se guarda con «Guardar» (empieza por «%PDF»);
 * 6. el cambio de diseño, que hace girar la vista previa.
 *
 * Robolectric no implementa `PdfDocument` ni lee el `FileProvider`: el PDF es [FakePdfDocument] (dibuja sus páginas en
 * un `Bitmap`, que se guarda como captura) y el `content://` apunta a `cacheDir`. Los documentos de fuera son
 * [FakeDocuments]. Espera siempre a un estado (nunca un tiempo fijo) y guarda las capturas en
 * `app/build/capturas/recorrido-2c/`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class RecorridoEtapa2cTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    /** Jueves 26 de noviembre de 2026, 12:00 en Madrid: la semana va del lunes 23 al domingo 29. */
    private val clock = Clock.fixed(Instant.parse("2026-11-26T11:00:00Z"), ZoneId.of("Europe/Madrid"))
    private val today = LocalDate.of(2026, 11, 26)
    private val monday = weekOf(today).first()
    private val documents = FakeDocuments()
    private val pdfs = CopyOnWriteArrayList<FakePdfDocument>()
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
        container = installTestContainer(
            clock = clock,
            documents = documents,
            cardPdf = { FakePdfDocument(scale = 2f).also { pdfs += it } },
            cardUri = { context, file -> Uri.parse("content://${context.packageName}.fileprovider/" + file.relativeTo(context.cacheDir).invariantSeparatorsPath) },
        )
        runBlocking {
            with(container.settingsRepository) {
                setTheme(theme)
                setAuto(false)
                // Una copia reciente fuera del teléfono: Inicio sin el recordatorio de las copias.
                updateBackupStatus { it.copy(lastExternal = clock.instant().minusSeconds(86_400)) }
            }
        }
        threeRecipes()

        scenario = ActivityScenario.launch(MainActivity::class.java).also { s -> s.onActivity { activity = it } }
        rule.waitFor(hasText("¿Qué cocinamos hoy?"))

        week()
        shoppingList()
        shareSquare()
        savePdf()
        flipOnDesign()
    }

    // ---------------------------------------------------------------- 1. tres recetas

    private fun threeRecipes() = runBlocking {
        val repo = container.recipeRepository
        repo.save(
            sampleRecipe(
                LENTEJAS, LENTEJAS_TITLE, minutes = 50,
                ingredients = listOf(
                    ingredient("l1", "Lentejas", 300.0, MeasureUnit.GRAM),
                    ingredient("l2", "Zanahorias", 2.0, MeasureUnit.PIECE),
                    ingredient("l3", "Pimentón", 1.0, MeasureUnit.TEASPOON),
                ),
                steps = listOf(
                    Step("s1", "Sofríe la zanahoria picada con un poco de aceite.", emptyList(), null, emptySet()),
                    Step("s2", "Añade las lentejas, el pimentón y agua; cuécelas 40 minutos.", emptyList(), null, emptySet()),
                ),
            ).copy(notes = "Mejor al día siguiente."),
        )
        repo.save(
            sampleRecipe(
                ARROZ, "Arroz con tomate", minutes = 25,
                ingredients = listOf(
                    ingredient("a1", "Arroz", 200.0, MeasureUnit.GRAM),
                    ingredient("a2", "Tomates", 4.0, MeasureUnit.PIECE),
                ),
            ),
        )
        repo.save(
            sampleRecipe(
                TORTILLA, "Tortilla de patatas", minutes = 40,
                ingredients = listOf(
                    ingredient("t1", "Patatas", 500.0, MeasureUnit.GRAM),
                    ingredient("t2", "Huevos", 6.0, MeasureUnit.PIECE),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------- 2. la semana

    private fun meals(): List<PlannedMeal> = runBlocking { container.mealPlanRepository.observeWeek(monday).first() }

    private fun week() {
        rule.onNodeWithTag("tab-shopping").performClick()
        rule.waitFor(hasTestTag("shopping-tab-week"))
        rule.onNodeWithTag("shopping-tab-week").performClick()
        rule.waitFor(hasTestTag("week-fill"))
        rule.waitFor(hasText("Esta semana"))
        save("paso2-semana-vacia")

        // «Rellenar»: solo desde hoy (jueves) hasta el domingo.
        rule.onNodeWithTag("week-fill").performClick()
        until { meals().isNotEmpty() }
        val first = meals()
        assertThat(first.all { it.date >= today }).isTrue()
        rule.waitFor(hasText(filledMessage(first.size)))
        save("paso2-rellenada")

        // «Deshacer» la deja vacía otra vez.
        rule.onNodeWithText("Deshacer").performClick()
        until { meals().isEmpty() }
        rule.waitFor(hasContentDescription("Jueves 26, cena: vacío", substring = true))

        // Otra vez «Rellenar» y, a mano, la cena de hoy pasa a ser la tortilla (o el arroz, si ya era la tortilla).
        rule.onNodeWithTag("week-fill").performClick()
        until { meals().size == first.size }
        val dinner = checkNotNull(meals().firstOrNull { it.date == today && it.slot == MealSlot.DINNER })
        val chosen = if (dinner.recipeId == TORTILLA) ARROZ else TORTILLA
        rule.onNodeWithTag(weekSlotTag(today, MealSlot.DINNER)).performScrollTo().performClick()
        rule.waitFor(hasTestTag("week-panel"))
        save("paso2-panel-hueco")
        rule.onNodeWithTag("week-search").performTextInput(if (chosen == TORTILLA) "tortilla" else "arroz")
        rule.waitFor(hasTestTag("week-choice-$chosen"))
        rule.onNodeWithTag("week-choice-$chosen").performClick()
        until { meals().firstOrNull { it.date == today && it.slot == MealSlot.DINNER }?.recipeId == chosen }
        // El hueco conserva su id: se cambió la receta, no se añadió otro plato.
        assertThat(meals().single { it.date == today && it.slot == MealSlot.DINNER }.id).isEqualTo(dinner.id)
        assertThat(meals()).hasSize(first.size)
        save("paso2-cambiada-a-mano")
    }

    // ---------------------------------------------------------------- 3. la lista de la semana

    private fun shoppingList() {
        rule.onNodeWithTag("week-generate").performScrollTo().performClick()
        rule.waitFor(hasText("ingredientes añadidos", substring = true))
        val items = runBlocking { container.shoppingRepository.observe().first().values.flatten() }
        assertThat(items).isNotEmpty()
        save("paso3-lista-generada")

        rule.onNodeWithTag("shopping-tab-list").performClick()
        val sample = items.first().name
        rule.waitFor(hasText(sample))
        save("paso3-compra")
    }

    // ---------------------------------------------------------------- 4. la ficha cuadrada, compartida

    private fun previewSays(text: String) = hasTestTag("card-preview") and hasContentDescription(text, substring = true)

    private fun shareSquare() {
        rule.onNodeWithTag("tab-recipes").performClick()
        rule.waitFor(hasTestTag("recipes-search"))
        rule.waitFor(hasText(LENTEJAS_TITLE))
        rule.onAllNodes(hasText(LENTEJAS_TITLE)).onFirst().performClick()
        rule.waitFor(hasTestTag("detail-cook"))
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.onNodeWithText("Compartir").performClick()
        rule.waitFor(hasTestTag("card-screen"))
        rule.waitFor(previewSays("Cuadrada, Clásico"), 15_000)
        save("paso4-ficha-cuadrada")

        val shadow = shadowOf(activity)
        while (shadow.nextStartedActivity != null) Unit
        rule.onNodeWithTag("card-share").performScrollTo().performClick()
        var chooser: Intent? = null
        until(30_000) { shadow.nextStartedActivity?.also { chooser = it } != null }
        assertThat(chooser!!.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION")
        val send = checkNotNull(chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertThat(send.type).isEqualTo("image/png")
        @Suppress("DEPRECATION")
        val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val file = File(activity.cacheDir, checkNotNull(uri.path).removePrefix("/"))
        assertThat(file.name).isEqualTo("Sazon-lentejas-estofadas.png")
        val bitmap = checkNotNull(BitmapFactory.decodeFile(file.path))
        assertThat(bitmap.width to bitmap.height).isEqualTo(1080 to 1080)
        saveBitmap("paso4-png-compartido", bitmap)
    }

    // ---------------------------------------------------------------- 5. el PDF A4, guardado

    private fun savePdf() {
        rule.onNodeWithTag("card-format-PDF_A4").performScrollTo().performClick()
        rule.waitFor(previewSays("PDF A4, Clásico"), 15_000)
        save("paso5-ficha-pdf")

        val shadow = shadowOf(activity)
        rule.onNodeWithTag("card-save").performScrollTo().performClick()
        rule.waitForIdle()
        val started = checkNotNull(shadow.nextStartedActivityForResult) { "«Guardar» no abrió el selector" }
        assertThat(started.intent.action).isEqualTo(Intent.ACTION_CREATE_DOCUMENT)
        assertThat(started.intent.getStringExtra(Intent.EXTRA_TITLE)).isEqualTo("Sazon-lentejas-estofadas.pdf")
        val doc = "content://prueba/Sazon-lentejas-estofadas.pdf"
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(doc)))
        rule.waitFor(hasText(CARD_SAVED), 30_000)
        assertThat(isPdf(checkNotNull(documents.files[doc]))).isTrue()
        assertThat(documents.deleted).doesNotContain(doc)
        save("paso5-pdf-guardado")
        // La primera página del PDF, dibujada con el mismo pintor.
        val page = pdfs.last().pages.first()
        assertThat(page.width to page.height).isEqualTo(595 to 842)
        saveBitmap("paso5-pdf-pagina1", page.bitmap)
    }

    // ---------------------------------------------------------------- 6. el giro al cambiar de diseño

    private fun flipAngle(): Float =
        rule.onNodeWithTag("card-preview", useUnmergedTree = true).fetchSemanticsNode().config[CardFlipAngle]

    private fun previewDescription(): String =
        rule.onNodeWithTag("card-preview", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()

    private fun flipOnDesign() {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("card-design-MINIMAL").performScrollTo().performClick()
        val angles = mutableListOf<Float>()
        val deadline = System.currentTimeMillis() + 15_000
        var savedMidway = false
        while (true) {
            val angle = flipAngle()
            angles += angle
            if (angle != 0f && !savedMidway && kotlin.math.abs(angle) > 30f) {
                save("paso6-girando", settleMillis = 0)
                savedMidway = true
            }
            if ("Minimalista" in previewDescription() && angle == 0f && angles.any { it != 0f }) break
            check(System.currentTimeMillis() < deadline) { "La vista previa no giró hasta «Minimalista»; ángulos: $angles" }
            // La maquetación nueva se calcula en otro hilo: se le da un respiro antes del fotograma siguiente.
            Thread.sleep(5)
            rule.mainClock.advanceTimeByFrame()
        }
        rule.mainClock.autoAdvance = true
        assertThat(angles.all { it in -90f..90f }).isTrue()
        save("paso6-minimalista")
    }

    // ---------------------------------------------------------------- utilidades

    /** Espera a [condition] dejando ocioso el hilo principal en cada vuelta (así avanzan las corrutinas de la app). */
    private fun until(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            rule.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "No se cumplió la condición en $timeoutMillis ms" }
            Thread.sleep(10)
        }
    }

    private fun save(name: String, settleMillis: Long = 1_500) {
        // Las entradas escalonadas esperan con delay(): se adelanta el reloj para capturar el estado final.
        if (settleMillis > 0) rule.mainClock.advanceTimeBy(settleMillis)
        if (rule.mainClock.autoAdvance) rule.waitForIdle()
        saveBitmap(name, rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getProperty("user.dir"), "build/capturas/recorrido-2c").apply { mkdirs() }
        File(dir, "$name-$themeName.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val LENTEJAS = "lentejas"
        const val LENTEJAS_TITLE = "Lentejas estofadas"
        const val ARROZ = "arroz-tomate"
        const val TORTILLA = "tortilla"
    }
}
