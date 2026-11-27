package app.recetario.share

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * PDF falso para las pruebas: Robolectric no implementa `PdfDocument` («document is closed!» al abrir la primera
 * página). Anota cada página y la dibuja en un `Bitmap` a [scale] (para comprobarla y guardarla); escribe un texto
 * que empieza por «%PDF-» con una línea por página (o, con `garbage`, algo que no es un PDF).
 */
class FakePdfDocument(
    private val scale: Float = 1f,
    private val garbage: Boolean = false,
    /** Si se pasa, escribir espera a que se abra (para probar lo que pasa mientras se genera la ficha). */
    private val gate: CountDownLatch? = null,
) : CardPdfDocument {
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
        gate?.let { check(it.await(30, TimeUnit.SECONDS)) { "La puerta del PDF falso no se abrió" } }
        // Con [garbage], lo que escribiría un PdfDocument roto: algo que no es un PDF.
        if (garbage) return out.write("basura".toByteArray())
        out.write("%PDF-falso\n".toByteArray())
        pages.forEach { out.write("pagina ${it.number} ${it.width}x${it.height}\n".toByteArray()) }
    }

    override fun close() {
        closed = true
    }
}
