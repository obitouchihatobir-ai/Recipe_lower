package app.recetario.recipes.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.roundToInt

/** Guarda las fotos como JPEG reducido en [dir]; las recetas solo recuerdan el nombre del archivo. */
class PhotoStore(private val dir: File) {

    /** Reduce la imagen (lado mayor ≤ 1600 px), la guarda como JPEG de calidad 85 y devuelve el nombre del archivo. */
    suspend fun import(source: InputStream): String = withContext(Dispatchers.IO) {
        val bytes = source.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("No es una imagen válida")

        val (width, height) = targetSize(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("No se pudo leer la imagen")

        val scaled = if (decoded.width == width && decoded.height == height) decoded
        else Bitmap.createScaledBitmap(decoded, width, height, true)
        val oriented = rotate(scaled, exifRotation(bytes))

        dir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        try {
            file(name).outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } finally {
            if (oriented !== scaled) oriented.recycle()
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
        name
    }

    fun file(name: String): File = File(dir, name)

    fun delete(name: String) {
        file(name).delete()
    }

    private fun exifRotation(bytes: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: IOException) {
        0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    companion object {
        /** Tamaño final: el lado mayor no pasa de [max] y se mantiene la proporción. */
        fun targetSize(w: Int, h: Int, max: Int = 1600): Pair<Int, Int> {
            val longest = maxOf(w, h)
            if (longest <= max) return w to h
            val scale = max.toDouble() / longest
            return maxOf(1, (w * scale).roundToInt()) to maxOf(1, (h * scale).roundToInt())
        }
    }
}
