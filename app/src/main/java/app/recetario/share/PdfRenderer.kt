package app.recetario.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Un documento PDF por páginas. En la app es `PdfDocument`; las pruebas usan uno falso (Robolectric no lo implementa). */
interface CardPdfDocument {
    /** Añade la página [number] de [width] × [height] pt y la dibuja con [draw]. */
    fun page(width: Int, height: Int, number: Int, draw: (Canvas) -> Unit)
    fun writeTo(out: OutputStream)
    fun close()
}

fun isPdf(bytes: ByteArray): Boolean =
    bytes.size > 4 && bytes.copyOfRange(0, 4).contentEquals("%PDF".toByteArray(Charsets.US_ASCII))

/** `android.graphics.pdf.PdfDocument`. */
class AndroidPdfDocument : CardPdfDocument {
    private val doc = PdfDocument()

    override fun page(width: Int, height: Int, number: Int, draw: (Canvas) -> Unit) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
        try {
            draw(page.canvas)
        } finally {
            doc.finishPage(page)
        }
    }

    override fun writeTo(out: OutputStream) = doc.writeTo(out)

    override fun close() = doc.close()
}

/**
 * La ficha en PDF: una página por cada página de `paginatePdf`, del tamaño del formato en puntos (A4 595 × 842,
 * Carta 612 × 792, tarjeta 283 × 425), con el texto partido por `StaticLayout` y las fuentes de la app
 * ([CardPainter]). La foto va reducida a unos 144 ppp de su caja: el PDF no lleva la foto entera.
 */
fun writeCardPdf(
    pages: List<PdfPage>,
    look: CardLook,
    painter: CardPainter,
    photo: Bitmap?,
    out: OutputStream,
    newDocument: () -> CardPdfDocument = painter.newPdf,
) {
    val box = pages.firstNotNullOfOrNull { p -> p.blocks.firstOrNull { it.kind == CardBlockKind.PHOTO } }
    val scaled = if (photo != null && box != null) downscale(photo, (max(box.width, box.height) * 2).roundToInt()) else photo
    val doc = newDocument()
    try {
        for (p in pages) {
            doc.page(p.format.width.roundToInt(), p.format.height.roundToInt(), p.number) { canvas ->
                painter.draw(canvas, p.format, p.blocks, look, scaled)
            }
        }
        doc.writeTo(out)
    } finally {
        doc.close()
        if (scaled !== photo) scaled?.recycle()
    }
}
