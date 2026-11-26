package app.recetario.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.RecetarioApp
import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.testing.ingredient
import app.recetario.testing.sampleRecipe
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

/** Recetas de prueba de las fichas. */
internal object CardSamples {
    fun short(photo: Boolean = true): Recipe = sampleRecipe(
        "r1", "Crema de calabaza",
        ingredients = listOf(
            ingredient("i1", "Calabaza", 800.0, MeasureUnit.GRAM),
            ingredient("i2", "Cebolla", 1.0, MeasureUnit.PIECE),
            ingredient("i3", "Caldo de verduras", 750.0, MeasureUnit.MILLILITER),
            ingredient("i4", "Sal y pimienta", null, MeasureUnit.TO_TASTE),
        ),
        steps = listOf(
            "Pela la calabaza y córtala en dados.",
            "Sofríe la cebolla con un poco de aceite.",
            "Añade la calabaza y el caldo y cuécelo 20 minutos.",
            "Tritúralo hasta que quede fino.",
        ).mapIndexed { i, s -> Step("s$i", s, emptyList(), null, emptySet()) },
        flavor = FlavorProfile(sweet = 3, salty = 1),
    ).copy(coverPhoto = if (photo) "foto.jpg" else null, notes = "Mejor al día siguiente, con pipas tostadas por encima.")

    /** 40 ingredientes, 25 pasos y notas largas (punto 3 de la revisión). */
    fun long(): Recipe = sampleRecipe(
        "r2", "Guiso de la abuela con muchas cosas, para un domingo largo con toda la familia reunida",
        ingredients = (1..40).map { ingredient("i$it", "Ingrediente número $it bien descrito", it * 10.0, MeasureUnit.GRAM) },
        steps = (1..25).map { Step("s$it", "Paso $it: remueve con calma la mezcla durante unos minutos hasta que espese y brille.", emptyList(), null, emptySet()) },
        flavor = FlavorProfile(salty = 2, spicy = 1),
    ).copy(
        coverPhoto = "foto.jpg",
        notes = (1..30).joinToString(" ") { "Nota larga $it sobre cómo servirla." } + "\n" + (1..20).joinToString(" ") { "Segundo párrafo $it." },
    )

    /** Una «foto» de un color liso (verde) con un borde de otro, para verla en las capturas. */
    fun photo(): Bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply {
        eraseColor(PHOTO_COLOR)
    }

    const val PHOTO_COLOR = 0xFF2E7D32.toInt()

    fun saveRender(name: String, bitmap: Bitmap) {
        val dir = File(System.getProperty("user.dir"), "build/capturas/fichas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Tiempos medidos en las pruebas: se imprimen y se apuntan en `build/capturas/fichas/tiempos.txt` (artefacto de CI). */
internal object CardTimes {
    fun record(label: String, millis: Long) {
        val line = "$label: $millis ms"
        println("[tiempos-ficha] $line")
        val dir = File(System.getProperty("user.dir"), "build/capturas/fichas").apply { mkdirs() }
        File(dir, "tiempos.txt").appendText(line + "\n")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardRendererTest {
    private val context = ApplicationProvider.getApplicationContext<RecetarioApp>()
    private val painter = CardPainter(CardFonts(context))
    private val imageFormats = CardFormat.entries.filter { !it.isPdf }

    private fun image(recipe: Recipe, format: CardFormat, design: CardDesign, photo: Bitmap?, theme: ThemeChoice = ThemeChoice.CALIDO): Pair<ImageLayout, Bitmap> {
        val look = CardLook(design, theme)
        val content = cardContent(recipe, recipe.servings, "🍲", CardOptions(format, design, photo = photo != null))
        val layout = layoutImage(content, format, design, painter.measurer(look))
        return layout to renderCardImage(layout, look, painter, photo.takeIf { content.hasPhoto })
    }

    @Test
    fun share_png_writesFileOfExactSize() {
        val sizes = mapOf(CardFormat.SQUARE to (1080 to 1080), CardFormat.STORY to (1080 to 1920), CardFormat.LANDSCAPE to (1500 to 1000))
        for (format in imageFormats) {
            val start = System.nanoTime()
            val (_, bitmap) = image(CardSamples.short(), format, CardDesign.CLASSIC, CardSamples.photo())
            val drawn = System.nanoTime()
            val bytes = ByteArrayOutputStream().also { writePng(bitmap, it) }.toByteArray()
            CardTimes.record("maquetar y dibujar $format", (drawn - start) / 1_000_000)
            CardTimes.record("codificar PNG $format (${bytes.size} bytes)", (System.nanoTime() - drawn) / 1_000_000)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            assertWithMessage("$format").that(bounds.outWidth to bounds.outHeight).isEqualTo(sizes.getValue(format))
            assertThat(bytes.copyOfRange(1, 4).decodeToString()).isEqualTo("PNG")
        }
    }

    @Test
    fun photoDesign_withoutPhoto_usesEmoji() {
        for (format in imageFormats) {
            val (layout, bitmap) = image(CardSamples.short(photo = false), format, CardDesign.BIG_PHOTO, photo = null)
            val emoji = layout.blocks.single { it.kind == CardBlockKind.EMOJI }
            assertThat(emoji.text).isEqualTo("🍲")
            assertThat(layout.blocks.none { it.kind == CardBlockKind.PHOTO }).isTrue()
            // La esquina de arriba a la izquierda es el acento, a sangre.
            assertWithMessage("$format").that(bitmap.getPixel(2, 2)).isEqualTo(CardLook(CardDesign.BIG_PHOTO).palette.accent)
            CardSamples.saveRender("sin-foto-${format.name.lowercase()}", bitmap)
        }
        // Con foto, la foto (y no el acento).
        val (_, withPhoto) = image(CardSamples.short(), CardFormat.SQUARE, CardDesign.BIG_PHOTO, CardSamples.photo())
        assertThat(withPhoto.getPixel(2, 2)).isEqualTo(CardSamples.PHOTO_COLOR)
    }

    @Test
    fun measurer_matchesDrawing() {
        val look = CardLook(CardDesign.CLASSIC)
        val style = CardTextStyle(CardTextRole.BODY, 28f)
        val m = painter.measurer(look)
        assertThat(m.lines("Hola", style, 900f)).isEqualTo(1)
        val long = (1..60).joinToString(" ") { "palabra" }
        assertThat(m.lines(long, style, 900f)).isGreaterThan(2)
        assertThat(m.lines("uno\ndos", style, 900f)).isEqualTo(2)
    }

    /**
     * Lo medido es lo dibujado: con la receta larga, en cada formato de imagen y cada diseño, ningún píxel de texto
     * queda fuera de las cajas de la maquetación (en particular, nada por debajo del pie).
     */
    @Test
    fun longRecipe_noTextOutsideItsBlocks() {
        for (format in imageFormats) for (design in CardDesign.entries) for (theme in listOf(ThemeChoice.CALIDO, ThemeChoice.NOCTURNO)) {
            if (design != CardDesign.THEME && theme != ThemeChoice.CALIDO) continue
            val (layout, bitmap) = image(CardSamples.long(), format, design, CardSamples.photo(), theme)
            val bg = CardLook(design, theme).palette.bg
            val footer = layout.blocks.single { it.kind == CardBlockKind.FOOTER }
            // El pie es lo último: nada debajo de su caja.
            val allowed = layout.blocks.map { RectF(floor(it.x) - 4, floor(it.y) - 2, ceil(it.x + it.width) + 4, ceil(it.y + it.height) + 2) }.toMutableList()
            if (design == CardDesign.MINIMAL) allowed += RectF(0f, 0f, format.width, format.width * 0.07f)
            var outside = 0
            var below = 0
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (pixels[y * bitmap.width + x] == bg) continue
                if (allowed.none { it.contains(x.toFloat(), y.toFloat()) }) outside++
                if (y > footer.y + footer.height + 2) below++
            }
            assertWithMessage("$format $design $theme: píxeles fuera de las cajas").that(outside).isEqualTo(0)
            assertWithMessage("$format $design $theme: píxeles bajo el pie").that(below).isEqualTo(0)
            assertWithMessage("$format $design: recorta").that(layout.hiddenSteps + layout.hiddenIngredients).isGreaterThan(0)
            if (theme == ThemeChoice.CALIDO || design == CardDesign.THEME) {
                CardSamples.saveRender("larga-${format.name.lowercase()}-${design.name.lowercase()}-${theme.name.lowercase()}", bitmap)
            }
        }
    }

    @Test
    fun designs_renders() {
        for (design in CardDesign.entries) for (theme in ThemeChoice.entries) {
            if (design != CardDesign.THEME && theme != ThemeChoice.CALIDO) continue
            for (format in imageFormats) {
                val (_, bitmap) = image(CardSamples.short(), format, design, CardSamples.photo(), theme)
                CardSamples.saveRender("${format.name.lowercase()}-${design.name.lowercase()}-${theme.name.lowercase()}", bitmap)
            }
        }
    }

    @Test
    fun cacheFichas_clearedOnStart() {
        val dir = File(context.cacheDir, CARD_FILES_DIR).apply { mkdirs() }
        val old = File(File(dir, "vieja").apply { mkdirs() }, "Sazon-vieja.png").apply {
            writeText("x")
            setLastModified(System.currentTimeMillis() - 60_000)
        }
        val other = File(context.cacheDir, "otra-cosa.txt").apply { writeText("y") }
        context.onCreate()
        // Se vacía en segundo plano (no en el hilo principal).
        val deadline = System.currentTimeMillis() + 10_000
        while (old.exists() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertThat(old.exists()).isFalse()
        assertThat(other.exists()).isTrue()
    }

    /** Una ficha compartida después de arrancar (más nueva que el arranque) no se borra. */
    @Test
    fun clearCardFiles_keepsNewerThanStart() {
        val dir = File(context.cacheDir, CARD_FILES_DIR)
        val old = File(File(dir, "a").apply { mkdirs() }, "vieja.png").apply { writeText("x"); setLastModified(1_000) }
        val fresh = File(File(dir, "b").apply { mkdirs() }, "nueva.png").apply { writeText("y"); setLastModified(9_000) }
        clearCardFiles(context.cacheDir, olderThan = 5_000)
        assertThat(old.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
    }
}
