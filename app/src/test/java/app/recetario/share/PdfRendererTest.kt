package app.recetario.share

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.RecetarioApp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfRendererTest {
    private val context = ApplicationProvider.getApplicationContext<RecetarioApp>()
    private val painter = CardPainter(CardFonts(context))
    private val pdfFormats = CardFormat.entries.filter { it.isPdf }

    private fun pages(format: CardFormat, design: CardDesign, long: Boolean = true): List<PdfPage> {
        val recipe = if (long) CardSamples.long() else CardSamples.short()
        val content = cardContent(recipe, recipe.servings, "🍲", CardOptions(format, design))
        return paginatePdf(content, format, design, painter.measurer(CardLook(design)))
    }

    private fun pdfBytes(pages: List<PdfPage>, design: CardDesign): ByteArray =
        ByteArrayOutputStream().also { writeCardPdf(pages, CardLook(design), painter, CardSamples.photo(), it) }.toByteArray()

    /** Páginas del PDF (los diccionarios `/Type /Page`, no `/Pages`) y sus `MediaBox`. */
    private fun pageCount(pdf: String) = Regex("""/Type\s*/Page(?![a-zA-Z])""").findAll(pdf).count()

    @Test
    fun share_pdf_pageCountAndSize() {
        for (format in pdfFormats) for (design in CardDesign.entries) {
            val pages = pages(format, design)
            assertWithMessage("$format $design").that(pages.size).isGreaterThan(1)
            val bytes = pdfBytes(pages, design)
            val text = String(bytes, Charsets.ISO_8859_1)
            assertThat(text).startsWith("%PDF-")
            assertWithMessage("$format $design").that(pageCount(text)).isEqualTo(pages.size)
            val w = format.width.roundToInt()
            val h = format.height.roundToInt()
            assertWithMessage("$format $design").that(Regex("""/MediaBox\s*\[\s*0\s+0\s+$w\s+$h\s*]""").findAll(text).count())
                .isEqualTo(pages.size)
        }
    }

    @Test
    fun shortRecipe_onePage() {
        val pages = pages(CardFormat.PDF_A4, CardDesign.CLASSIC, long = false)
        assertThat(pages).hasSize(1)
        assertThat(pageCount(String(pdfBytes(pages, CardDesign.CLASSIC), Charsets.ISO_8859_1))).isEqualTo(1)
    }

    /** Las páginas se dibujan a PNG (con el mismo pintor que el PDF) para revisarlas a ojo. */
    @Test
    fun pdfPages_renders() {
        for (format in pdfFormats) for (design in CardDesign.entries) {
            val pages = pages(format, design)
            for (page in pages.take(2)) {
                val scale = 2f
                val bitmap = Bitmap.createBitmap((format.width * scale).roundToInt(), (format.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap).apply { scale(scale, scale) }
                painter.draw(canvas, format, page.blocks, CardLook(design), CardSamples.photo())
                CardSamples.saveRender("pdf-${format.name.lowercase()}-${design.name.lowercase()}-p${page.number}", bitmap)
            }
        }
    }
}
