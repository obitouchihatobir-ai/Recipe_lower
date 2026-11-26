package app.recetario.share

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.backup.FakeDocuments
import app.recetario.core.ui.motion.LocalReducedMotion
import app.recetario.core.ui.theme.RecetarioTheme
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.detail.formatRecipeAsText
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.settings.CardSettings
import app.recetario.testing.CloseDatabaseRule
import app.recetario.testing.ingredient
import app.recetario.testing.installTestContainer
import app.recetario.testing.sampleRecipe
import app.recetario.testing.waitFor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardScreenTest {
    @get:Rule(order = 0)
    val closeDatabase = CloseDatabaseRule { container.database.close() }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private lateinit var container: AppContainer
    private val documents = FakeDocuments()
    private val pickedUri = Uri.parse("content://prueba/Sazon-crema-de-calabaza.png")

    @Before
    fun setUp() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        container = installTestContainer(documents = documents)
    }

    /** Selector del sistema falso: responde al momento con [uri] y anota lo que se le pidió. */
    private class FakePicker(private val uri: Uri) : ActivityResultRegistryOwner {
        val inputs = mutableListOf<Any?>()
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                inputs += input
                dispatchResult(requestCode, uri)
            }
        }
    }

    private fun recipe(photo: String? = null): Recipe = sampleRecipe(
        "r1", "Crema de calabaza",
        ingredients = listOf(
            ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
            ingredient("i2", "Cebolla", 1.0, MeasureUnit.PIECE),
        ),
        steps = listOf(Step("s1", "Pela la calabaza.", emptyList(), null, emptySet()), Step("s2", "Cuécela 20 minutos.", emptyList(), null, emptySet())),
        flavor = FlavorProfile(sweet = 3),
    ).copy(coverPhoto = photo, notes = "Mejor al día siguiente.")

    private fun save(photo: Boolean = false): Recipe {
        val name = if (photo) "foto.jpg" else null
        if (photo) {
            val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2E7D32.toInt()) }
            val file = container.photoStore.file("foto.jpg")
            file.parentFile?.mkdirs()
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val r = recipe(name)
        runBlocking { container.recipeRepository.save(r) }
        return r
    }

    private fun launch(servings: Int = 4, reduced: Boolean? = null, picker: FakePicker = FakePicker(pickedUri)): FakePicker {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                RecetarioTheme(ThemeChoice.CALIDO) {
                    if (reduced == null) {
                        Route(servings)
                    } else {
                        CompositionLocalProvider(LocalReducedMotion provides reduced) { Route(servings) }
                    }
                }
            }
        }
        rule.waitFor(hasTestTag("card-preview"))
        return picker
    }

    /** Robolectric no implementa `PdfDocument` ni lee los `meta-data` del `FileProvider`: PDF y `content://` falsos. */
    private val painter = CardPainter(CardFonts(RuntimeEnvironment.getApplication()), newPdf = { FakePdfDocument() })
    private val authority = "${RuntimeEnvironment.getApplication().packageName}.fileprovider"

    @androidx.compose.runtime.Composable
    private fun Route(servings: Int) = CardRoute(
        container, "r1", servings, onBack = {},
        painter = painter,
        uriFor = { Uri.parse("content://$authority/" + it.relativeTo(cacheDir).invariantSeparatorsPath) },
        startShare = startShare,
    )

    private val cacheDir get() = RuntimeEnvironment.getApplication().cacheDir

    /** Abre el selector; null: el de verdad (`startActivity`). */
    private var startShare: ((Intent) -> Unit)? = null

    /** El archivo detrás de un `content://` falso de [Route]. */
    private fun fileOf(uri: Uri) = File(cacheDir, checkNotNull(uri.path).removePrefix("/"))

    private fun previewSays(text: String) = hasTestTag("card-preview") and hasContentDescription(text, substring = true)

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun nextChooser(): Intent? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity

    /**
     * Espera al selector. Causa de los fallos de la primera ronda: `waitUntil` con una condición que solo mira
     * Robolectric (`nextStartedActivity`) no hace avanzar el `Looper` principal (en pausa), así que la corrutina del
     * ViewModel nunca volvía de `Dispatchers.IO` ni el `LaunchedEffect` recibía el evento. Generar el PNG tarda
     * décimas (ver `tiempos.txt`). Aquí cada vuelta deja ocioso el hilo principal (`waitForIdle`).
     */
    private fun awaitChooser(timeoutMs: Long = 30_000): Intent {
        val start = System.currentTimeMillis()
        var found: Intent? = null
        while (found == null) {
            check(System.currentTimeMillis() - start < timeoutMs) { "No se abrió el selector en $timeoutMs ms" }
            rule.waitForIdle()
            check(rule.onAllNodes(hasText(CARD_SHARE_FAILED), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) { CARD_SHARE_FAILED }
            found = nextChooser()
            if (found == null) Thread.sleep(10)
        }
        val chooser = checkNotNull(found)
        CardTimes.record("pantalla: tocar «Compartir» → selector", System.currentTimeMillis() - start)
        // Ni publicar el archivo ni abrir el selector fallaron (la pantalla lo anota y sigue).
        val errors = org.robolectric.shadows.ShadowLog.getLogsForTag("Ficha").map { "${it.msg}: ${it.throwable}" }
        assertWithMessage(errors.joinToString()).that(errors).isEmpty()
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION")
        return checkNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
    }

    @Test
    fun share_text_usesFormatter() {
        val r = save()
        launch(servings = 6)
        click("card-format-TEXT")
        rule.waitFor(previewSays("Texto"))
        click("card-share")
        val send = awaitChooser()
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("text/plain")
        val text = send.getStringExtra(Intent.EXTRA_TEXT)
        assertThat(text).isEqualTo(formatRecipeAsText(r, 6, "🍲"))
        assertThat(text).contains("1,2 kg")
    }

    private fun sharePng(format: CardFormat, w: Int, h: Int) {
        save()
        launch()
        click("card-format-${format.name}")
        rule.waitFor(previewSays(format.label))
        click("card-share")
        val send = awaitChooser()
        assertThat(send.type).isEqualTo("image/png")
        @Suppress("DEPRECATION")
        val file = fileOf(checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)))
        assertThat(file.name).isEqualTo("Sazon-crema-de-calabaza.png")
        assertThat(file.parentFile?.parentFile).isEqualTo(File(cacheDir, CARD_FILES_DIR))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertWithMessage("$format").that(bounds.outWidth to bounds.outHeight).isEqualTo(w to h)
    }

    @Test
    fun share_png_writesFileOfExactSize() = sharePng(CardFormat.SQUARE, 1080, 1080)

    @Test
    fun share_png_story_writesFileOfExactSize() = sharePng(CardFormat.STORY, 1080, 1920)

    @Test
    fun share_png_landscape_writesFileOfExactSize() = sharePng(CardFormat.LANDSCAPE, 1500, 1000)

    /** Si no se puede abrir el selector (o publicar el archivo), se avisa: no se queda en silencio. */
    @Test
    fun share_launchFailure_showsMessage() {
        save()
        startShare = { throw SecurityException("sin permiso") }
        launch()
        click("card-share")
        rule.waitFor(hasText(CARD_SHARE_FAILED), 30_000)
        assertThat(org.robolectric.shadows.ShadowLog.getLogsForTag("Ficha")).isNotEmpty()
    }

    @Test
    fun share_launchFailure_activityNotFound_showsMessage() {
        save()
        startShare = { throw android.content.ActivityNotFoundException() }
        launch()
        click("card-format-TEXT")
        rule.waitFor(previewSays("Texto"))
        click("card-share")
        rule.waitFor(hasText(CARD_SHARE_FAILED), 30_000)
    }

    @Test
    fun unreadablePhoto_saysSo() {
        runBlocking { container.recipeRepository.save(recipe("no-existe.jpg")) }
        launch()
        rule.waitFor(hasText(PHOTO_UNREADABLE_TEXT), 10_000)
    }

    /** El diseño «Tema» usa el tema ya resuelto que le pasa `RecetarioTheme`. */
    @Test
    fun themeChoice_isPassedDown() {
        var seen: ThemeChoice? = null
        rule.setContent { RecetarioTheme(ThemeChoice.NOCTURNO) { seen = app.recetario.core.ui.theme.LocalThemeChoice.current } }
        rule.waitForIdle()
        assertThat(seen).isEqualTo(ThemeChoice.NOCTURNO)
    }

    @Test
    fun intent_grantsReadPermission() {
        save()
        launch()
        click("card-format-PDF_A4")
        rule.waitFor(previewSays("PDF A4"))
        click("card-share")
        val send = awaitChooser()
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("application/pdf")
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION")
        val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo(authority)
        assertThat(uri.path).endsWith("/Sazon-crema-de-calabaza.pdf")
        assertThat(send.clipData?.getItemAt(0)?.uri).isEqualTo(uri)
        assertThat(isPdf(fileOf(uri).readBytes())).isTrue()
    }

    @Test
    fun save_createDocument_writesFile() {
        save()
        val picker = launch()
        click("card-format-SQUARE")
        rule.waitFor(previewSays("Cuadrada"))
        click("card-save")
        rule.waitUntil(10_000) { (documents.files[pickedUri.toString()]?.size ?: 0) > 0 }
        assertThat(picker.inputs).containsExactly("Sazon-crema-de-calabaza.png")
        val bytes = documents.files.getValue(pickedUri.toString())
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertThat(bmp.width to bmp.height).isEqualTo(1080 to 1080)
        rule.waitFor(hasText(CARD_SAVED))
        assertThat(documents.deleted).isEmpty()
    }

    @Test
    fun save_failure_deletesEmptyDocument() {
        save()
        documents.failOutput = true
        launch()
        click("card-format-PDF_LETTER")
        rule.waitFor(previewSays("PDF Carta"))
        click("card-save")
        rule.waitFor(hasText(CARD_SAVE_FAILED), 10_000)
        assertThat(documents.deleted).contains(pickedUri.toString())
    }

    @Test
    fun photoDesign_withoutPhoto_usesEmoji() {
        save(photo = false)
        launch()
        click("card-design-BIG_PHOTO")
        rule.waitFor(previewSays("Foto grande"))
        rule.onNodeWithTag("card-photo").assertIsNotEnabled()
        rule.waitFor(hasTestTag("card-photo") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, NO_PHOTO_TEXT))
    }

    @Test
    fun photo_withPhoto_canBeTurnedOff() {
        save(photo = true)
        launch()
        rule.waitFor(hasTestTag("card-photo") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Selected, true))
        click("card-photo")
        rule.waitFor(hasTestTag("card-photo") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Selected, false))
    }

    @Test
    fun options_remembered() {
        save()
        launch()
        click("card-format-STORY")
        click("card-design-MINIMAL")
        click("card-steps")
        click("card-flavor")
        val expected = CardSettings(
            CardOptions(format = CardFormat.STORY, design = CardDesign.MINIMAL, photo = true, steps = false, notes = true, flavor = false),
        )
        rule.waitUntil(5_000) { runBlocking { container.settingsRepository.cardSettings.first() } == expected }
        click("card-format-TEXT")
        rule.waitUntil(5_000) { runBlocking { container.settingsRepository.cardSettings.first() } == expected.copy(asText = true) }
    }

    @Test
    fun options_rememberedAcrossScreens() {
        save()
        runBlocking {
            container.settingsRepository.setCardSettings(CardSettings(CardOptions(format = CardFormat.PDF_CARD, design = CardDesign.THEME)))
        }
        launch()
        rule.waitFor(previewSays("Tarjeta 10×15, Tema"))
    }

    /** Recorre fotograma a fotograma hasta que la vista previa diga [text]; devuelve los ángulos vistos. */
    private fun anglesUntil(text: String): List<Float> {
        val angles = mutableListOf<Float>()
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val node = rule.onNodeWithTag("card-preview", useUnmergedTree = true).fetchSemanticsNode()
            angles += node.config[CardFlipAngle]
            val description = node.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
            if (text in description && angles.last() == 0f) return angles
            check(System.currentTimeMillis() < deadline) { "La vista previa no llegó a «$text»; ángulos: $angles" }
            Thread.sleep(5)
            rule.mainClock.advanceTimeByFrame()
        }
    }

    @Test
    fun flip_onDesignChange_reducedMotionInstant() {
        save()
        launch(reduced = true)
        rule.waitFor(previewSays("Clásico"))
        rule.mainClock.autoAdvance = false
        click("card-design-MINIMAL")
        val angles = anglesUntil("Minimalista")
        assertThat(angles.toSet()).containsExactly(0f)
    }

    @Test
    fun flip_onFormatChange_rotatesAndCanBeTouched() {
        save()
        launch(reduced = false)
        rule.waitFor(previewSays("Cuadrada"))
        rule.mainClock.autoAdvance = false
        click("card-format-LANDSCAPE")
        // A mitad del giro se puede tocar otro chip: el giro sigue y acaba en el último elegido.
        var seen = 0
        val deadline = System.currentTimeMillis() + 10_000
        while (seen == 0) {
            check(System.currentTimeMillis() < deadline) { "No giró" }
            Thread.sleep(5)
            rule.mainClock.advanceTimeByFrame()
            val a = rule.onNodeWithTag("card-preview", useUnmergedTree = true).fetchSemanticsNode().config[CardFlipAngle]
            if (a != 0f) seen++
        }
        click("card-design-THEME")
        val angles = anglesUntil("Horizontal, Tema")
        assertThat(angles.any { it != 0f }).isTrue()
        assertThat(angles.all { it in -90f..90f }).isTrue()
    }

    @Test
    fun photoDropped_sayWhy() {
        // 60 ingredientes con foto: en la cuadrada no caben ni a la letra mínima, y la foto se quita.
        val many = (1..60).map { ingredient("i$it", "Ingrediente número $it bien descrito", 10.0 * it, MeasureUnit.GRAM) }
        val bmp = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        val file = container.photoStore.file("foto.jpg")
        file.parentFile?.mkdirs()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        runBlocking { container.recipeRepository.save(sampleRecipe("r1", "Crema de calabaza", ingredients = many).copy(coverPhoto = "foto.jpg")) }
        launch()
        click("card-format-STORY")
        rule.waitFor(hasText(PHOTO_DROPPED_TEXT), 10_000)
    }
}
