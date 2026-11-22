package app.recetario.share

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.R
import app.recetario.RecetarioApp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * El PDF de la ficha. Robolectric no implementa `PdfDocument`, así que se escribe con [FakePdfDocument]: se
 * comprueba qué páginas pide `writeCardPdf` (cuántas, de qué tamaño y en qué orden) y se dibuja cada una con el
 * mismo pintor. Que el `PdfDocument` real se abra bien solo se comprueba en un teléfono.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfRendererTest {
    private val context = ApplicationProvider.getApplicationContext<RecetarioApp>()
    private val pdfFormats = CardFormat.entries.filter { it.isPdf }

    private fun pages(painter: CardPainter, format: CardFormat, design: CardDesign, long: Boolean = true): List<PdfPage> {
        val recipe = if (long) CardSamples.long() else CardSamples.short()
        val content = cardContent(recipe, recipe.servings, "🍲", CardOptions(format, design))
        return paginatePdf(content, format, design, painter.measurer(CardLook(design)))
    }

    @Test
    fun share_pdf_pageCountAndSize() {
        for (format in pdfFormats) for (design in CardDesign.entries) {
            val fake = FakePdfDocument()
            val painter = CardPainter(CardFonts(context), newPdf = { fake })
            val pages = pages(painter, format, design)
            assertWithMessage("$format $design").that(pages.size).isGreaterThan(1)
            val out = ByteArrayOutputStream()
            writeCardPdf(pages, CardLook(design), painter, CardSamples.photo(), out)
            val w = format.width.roundToInt()
            val h = format.height.roundToInt()
            assertWithMessage("$format $design").that(fake.pages.map { Triple(it.width, it.height, it.number) })
                .isEqualTo((1..pages.size).map { Triple(w, h, it) })
            assertThat(fake.closed).isTrue()
            assertThat(out.toString(Charsets.UTF_8.name())).startsWith("%PDF-")
            // Cada página tiene algo dibujado además del fondo (el pie, al menos).
            val bg = CardLook(design).palette.bg
            for (p in fake.pages) {
                val px = IntArray(p.bitmap.width * p.bitmap.height)
                p.bitmap.getPixels(px, 0, p.bitmap.width, 0, 0, p.bitmap.width, p.bitmap.height)
                assertWithMessage("$format $design p${p.number}").that(px.count { it != bg }).isGreaterThan(100)
            }
        }
    }

    @Test
    fun shortRecipe_onePage_closedEvenIfDrawingFails() {
        val fake = FakePdfDocument()
        val painter = CardPainter(CardFonts(context), newPdf = { fake })
        val pages = pages(painter, CardFormat.PDF_A4, CardDesign.CLASSIC, long = false)
        assertThat(pages).hasSize(1)
        val failing = object : java.io.OutputStream() {
            override fun write(b: Int) = throw java.io.IOException("disco lleno")
        }
        runCatching { writeCardPdf(pages, CardLook(CardDesign.CLASSIC), painter, null, failing) }
        assertThat(fake.pages).hasSize(1)
        assertThat(fake.closed).isTrue()
    }

    @Test
    fun appUsesPdfDocument() {
        assertThat(CardPainter(CardFonts(context)).newPdf()).isInstanceOf(AndroidPdfDocument::class.java)
    }

    /** El `FileProvider` publica `cacheDir/fichas/` (y nada más que lo de la cámara). */
    @Test
    fun fileProvider_sharesTheCardFolder() {
        val parser = context.resources.getXml(R.xml.file_paths)
        val paths = mutableListOf<Triple<String, String?, String?>>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name != "paths") {
                paths += Triple(parser.name, parser.getAttributeValue(null, "name"), parser.getAttributeValue(null, "path"))
            }
        }
        assertThat(paths).containsExactly(
            Triple("cache-path", "camera", "camera/"),
            Triple("cache-path", "fichas", "$CARD_FILES_DIR/"),
        )
    }

    /** Las páginas se dibujan a PNG (con el mismo pintor que el PDF) para revisarlas a ojo. */
    @Test
    fun pdfPages_renders() {
        for (format in pdfFormats) for (design in CardDesign.entries) {
            val fake = FakePdfDocument(scale = 2f)
            val painter = CardPainter(CardFonts(context), newPdf = { fake })
            writeCardPdf(pages(painter, format, design), CardLook(design), painter, CardSamples.photo(), ByteArrayOutputStream())
            fake.pages.take(2).forEach {
                CardSamples.saveRender("pdf-${format.name.lowercase()}-${design.name.lowercase()}-p${it.number}", it.bitmap)
            }
        }
    }
}
