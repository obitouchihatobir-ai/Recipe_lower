package app.recetario.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Carpeta de las fichas que se comparten (`cacheDir/fichas/`, publicada por el `FileProvider`). */
const val CARD_FILES_DIR = "fichas"

/** Vacía `cacheDir/fichas/` (al iniciar la app): lo compartido antes ya no hace falta. No falla nunca. */
fun clearCardFiles(cacheDir: File) {
    runCatching { File(cacheDir, CARD_FILES_DIR).listFiles()?.forEach { it.deleteRecursively() } }
}

/**
 * Dibuja los bloques de la maquetación (`layoutImage` o una página de `paginatePdf`) en un `Canvas` con las
 * unidades del formato (px o pt). Lo usan la vista previa, la imagen PNG y el PDF, y el medidor de la maquetación
 * ([measurer]) parte las líneas con el mismo `StaticLayout` y la misma fuente: lo medido es lo dibujado.
 */
class CardPainter(private val fonts: CardFonts) {

    fun paint(look: CardLook, style: CardTextStyle): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.get(look.font(style.role))
        textSize = style.sizePx
    }

    fun measurer(look: CardLook): TextMeasurer = object : TextMeasurer {
        override fun lines(text: String, style: CardTextStyle, widthPx: Float): Int =
            lineBreaks(text, paint(look, style), widthPx).lineCount
    }

    /**
     * Dibuja [blocks] sobre todo el lienzo de [format] (fondo incluido). [photo]: la foto de la receta (null si no
     * hay o la opción está apagada; entonces la maquetación ya no tiene bloque PHOTO).
     */
    fun draw(canvas: Canvas, format: CardFormat, blocks: List<CardBlock>, look: CardLook, photo: Bitmap?) {
        val pal = look.palette
        canvas.drawColor(pal.bg)
        val w = format.width
        if (look.design == CardDesign.MINIMAL) {
            // La línea de acento, arriba a la izquierda (en el margen, encima del título).
            val margin = w * (if (format.isPdf) 0.08f else 0.07f)
            val thick = max(2f, w * 0.006f)
            val top = margin * 0.4f
            canvas.drawRect(margin, top, margin + w * 0.12f, top + thick, Paint().apply { color = pal.accent })
        }
        val radius = if (look.design == CardDesign.BIG_PHOTO) 0f else w * 0.025f
        for (b in blocks) {
            when (b.kind) {
                CardBlockKind.PHOTO -> drawPhoto(canvas, b, photo, pal, radius, veil = blocks.any { it.overPhoto })
                CardBlockKind.EMOJI -> drawEmoji(canvas, b, pal, radius, veil = blocks.any { it.overPhoto })
                else -> drawText(canvas, b, look)
            }
        }
    }

    private fun drawText(canvas: Canvas, b: CardBlock, look: CardLook) {
        val style = b.style ?: return
        val pal = look.palette
        val paint = paint(look, style).apply {
            color = when {
                b.overPhoto -> OVER_PHOTO_INK
                b.kind == CardBlockKind.HEADING -> pal.accent
                b.kind in MUTED -> pal.muted
                else -> pal.ink
            }
        }
        val layout = lineBreaks(b.text, paint, b.width)
        val lh = style.lineHeightPx
        val fm = paint.fontMetrics
        // Cada línea mide exactamente lineHeightPx (lo que cuenta la maquetación), con el texto centrado en ella.
        val baseline = (lh - (fm.descent - fm.ascent)) / 2 - fm.ascent
        val n = min(layout.lineCount, max(b.lines, 1))
        for (i in 0 until n) {
            val line = b.text.substring(layout.getLineStart(i), layout.getLineEnd(i)).trimEnd()
            canvas.drawText(line, b.x, b.y + i * lh + baseline, paint)
        }
    }

    private fun drawPhoto(canvas: Canvas, b: CardBlock, photo: Bitmap?, pal: CardPalette, radius: Float, veil: Boolean) {
        val dst = RectF(b.x, b.y, b.x + b.width, b.y + b.height)
        canvas.save()
        if (radius > 0f) canvas.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
        if (photo == null) {
            canvas.drawColor(pal.accent)
        } else {
            canvas.drawBitmap(photo, centerCrop(photo.width, photo.height, dst), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (veil) drawVeil(canvas, dst)
        canvas.restore()
    }

    private fun drawEmoji(canvas: Canvas, b: CardBlock, pal: CardPalette, radius: Float, veil: Boolean) {
        val dst = RectF(b.x, b.y, b.x + b.width, b.y + b.height)
        canvas.drawRoundRect(dst, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent })
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = min(b.width, b.height) * 0.38f
            textAlign = Paint.Align.CENTER
            color = pal.onAccent
        }
        val fm = paint.fontMetrics
        // Con el título encima, el emoji sube un poco para no quedar debajo del texto.
        val cy = if (veil) dst.top + dst.height() * 0.4f else dst.centerY()
        canvas.drawText(b.text, dst.centerX(), cy - (fm.ascent + fm.descent) / 2, paint)
        if (veil) drawVeil(canvas, dst)
    }

    /** Velo oscuro en la mitad de abajo para que el título claro se lea sobre cualquier foto. */
    private fun drawVeil(canvas: Canvas, dst: RectF) {
        val top = dst.top + dst.height() * 0.35f
        val paint = Paint().apply {
            shader = LinearGradient(0f, top, 0f, dst.bottom, 0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(dst.left, top, dst.right, dst.bottom, paint)
    }

    private companion object {
        val MUTED = setOf(CardBlockKind.META, CardBlockKind.FLAVOR, CardBlockKind.MORE, CardBlockKind.FOOTER)
        const val OVER_PHOTO_INK = 0xFFFFFFFF.toInt()
    }
}

/** El mismo partido de líneas al medir y al dibujar: sin relleno de fuente ni guiones, con el ancho redondeado hacia abajo. */
internal fun lineBreaks(text: String, paint: TextPaint, width: Float): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, floor(width).toInt()))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()

/** La parte central de la foto con la proporción de [dst]. */
internal fun centerCrop(width: Int, height: Int, dst: RectF): Rect {
    val target = dst.width() / dst.height()
    val source = width.toFloat() / height
    return if (source > target) {
        val w = (height * target).roundToInt()
        val left = (width - w) / 2
        Rect(left, 0, left + w, height)
    } else {
        val h = (width / target).roundToInt()
        val top = (height - h) / 2
        Rect(0, top, width, top + h)
    }
}

/** La ficha como imagen del tamaño exacto del formato (1080 × 1080, 1080 × 1920 o 1500 × 1000). */
fun renderCardImage(layout: ImageLayout, look: CardLook, painter: CardPainter, photo: Bitmap?): Bitmap {
    val format = layout.format
    val bitmap = Bitmap.createBitmap(format.width.roundToInt(), format.height.roundToInt(), Bitmap.Config.ARGB_8888)
    painter.draw(Canvas(bitmap), format, layout.blocks, look, photo)
    return bitmap
}

fun writePng(bitmap: Bitmap, out: OutputStream) {
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "No se pudo escribir la imagen" }
}

/** La foto de la receta, reducida para que su lado mayor no pase de [maxSide]; null si no se puede leer. */
fun loadCardPhoto(file: File, maxSide: Int = 1600): Bitmap? {
    if (!file.isFile) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}

/** [bitmap] reducido para que su lado mayor no pase de [maxSide] (la foto del PDF, a unos 144 ppp). */
internal fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
    val side = max(bitmap.width, bitmap.height)
    if (side <= maxSide) return bitmap
    val f = maxSide.toFloat() / side
    return Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * f).roundToInt()), max(1, (bitmap.height * f).roundToInt()), true)
}

/**
 * Lo que abre el selector de «Compartir» para un archivo de `cacheDir/fichas/`: `ACTION_SEND` con su `content://`
 * del `FileProvider`, el tipo y permiso de lectura (en el intent, en su `ClipData` y en el selector).
 */
fun cardShareIntent(context: Context, file: File, mimeType: String): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, SHARE_TITLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** El texto de la etapa 1, con el selector de siempre. */
fun textShareIntent(text: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return Intent.createChooser(send, SHARE_TITLE)
}

private const val SHARE_TITLE = "Compartir receta"
