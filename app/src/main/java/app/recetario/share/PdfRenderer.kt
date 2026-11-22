package app.recetario.share

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La ficha en PDF: una página de `PdfDocument` por cada página de `paginatePdf`, del tamaño del formato en puntos
 * (A4 595 × 842, Carta 612 × 792, tarjeta 283 × 425), con el texto partido por `StaticLayout` y las fuentes de la
 * app ([CardPainter]). La foto va reducida a unos 144 ppp de su caja: el PDF no lleva la foto entera.
 */
fun writeCardPdf(pages: List<PdfPage>, look: CardLook, painter: CardPainter, photo: Bitmap?, out: OutputStream) {
    val box = pages.firstNotNullOfOrNull { p -> p.blocks.firstOrNull { it.kind == CardBlockKind.PHOTO } }
    val scaled = if (photo != null && box != null) downscale(photo, (max(box.width, box.height) * 2).roundToInt()) else photo
    val doc = PdfDocument()
    try {
        for (p in pages) {
            val info = PdfDocument.PageInfo.Builder(p.format.width.roundToInt(), p.format.height.roundToInt(), p.number).create()
            val page = doc.startPage(info)
            painter.draw(page.canvas, p.format, p.blocks, look, scaled)
            doc.finishPage(page)
        }
        doc.writeTo(out)
    } finally {
        doc.close()
        if (scaled !== photo) scaled?.recycle()
    }
}
