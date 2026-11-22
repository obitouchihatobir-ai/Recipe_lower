package app.recetario.share

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.OutputStream

/**
 * PDF falso para las pruebas: Robolectric no implementa `PdfDocument` («document is closed!» al abrir la primera
 * página). Anota cada página y la dibuja en un `Bitmap` a [scale] (para comprobarla y guardarla); escribe un texto
 * que empieza por «%PDF-» con una línea por página.
 */
class FakePdfDocument(private val scale: Float = 1f) : CardPdfDocument {
    data class Page(val width: Int, val height: Int, val number: Int, val bitmap: Bitmap)

    val pages = mutableListOf<Page>()
    var closed = false
        private set

    override fun page(width: Int, height: Int, number: Int, draw: (Canvas) -> Unit) {
        check(!closed)
        val bitmap = Bitmap.createBitmap((width * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap).apply { scale(scale, scale) })
        pages += Page(width, height, number, bitmap)
    }

    override fun writeTo(out: OutputStream) {
        out.write("%PDF-falso\n".toByteArray())
        pages.forEach { out.write("pagina ${it.number} ${it.width}x${it.height}\n".toByteArray()) }
    }

    override fun close() {
        closed = true
    }
}
