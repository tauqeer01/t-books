package com.bookflow.app.esign.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import com.bookflow.app.esign.domain.SavedSignature
import com.bookflow.app.esign.domain.SignatureKind
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/** Saved signatures and initials as transparent PNGs in app-private storage (`filesDir/signatures`). */
class SignatureStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "signatures").apply { mkdirs() }

    fun list(): List<SavedSignature> = dir.listFiles { f -> f.extension == "png" }.orEmpty().mapNotNull { f ->
        val parts = f.nameWithoutExtension.split('_')
        val kind = runCatching { SignatureKind.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
        SavedSignature(parts.getOrElse(1) { f.nameWithoutExtension }, kind, f.absolutePath, f.lastModified())
    }.sortedByDescending { it.createdAt }

    /** Saves [bitmap] cropped to its ink; replaces any earlier signature of the same kind. */
    fun save(kind: SignatureKind, bitmap: Bitmap): SavedSignature {
        val trimmed = trimTransparent(bitmap) ?: throw IOException("The signature is empty")
        list().filter { it.kind == kind }.forEach { File(it.path).delete() }
        val id = UUID.randomUUID().toString()
        val file = File(dir, "${kind.name}_$id.png")
        file.outputStream().use { trimmed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return SavedSignature(id, kind, file.absolutePath, file.lastModified())
    }

    fun delete(signature: SavedSignature) { File(signature.path).delete() }

    /** Renders typed text in a handwriting-style font. */
    fun renderTyped(text: String, style: TypedStyle, colorArgb: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = 160f
            typeface = style.typeface
        }
        val width = (paint.measureText(text) + 80).toInt().coerceAtLeast(1)
        val metrics = paint.fontMetrics
        val height = (metrics.bottom - metrics.top + 60).toInt()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawText(text, 40f, 30f - metrics.top, paint)
        }
    }

    /**
     * A photo of a signature on paper: the paper becomes transparent and the ink keeps a single color, so it
     * looks signed rather than pasted.
     */
    fun fromPhoto(uri: Uri, colorArgb: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw IOException("This image could not be read")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("This image could not be read")
        return inkOnly(source, colorArgb).also { source.recycle() }
    }

    enum class TypedStyle(val title: String) {
        SCRIPT("Script"), ELEGANT("Elegant"), PLAIN("Plain");

        val typeface: Typeface get() = when (this) {
            SCRIPT -> Typeface.create("cursive", Typeface.NORMAL)
            ELEGANT -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            PLAIN -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
    }

    companion object {
        /** Pixels darker than this (0..255 luminance) count as ink in a signature photo. */
        private const val INK_THRESHOLD = 150

        fun inkOnly(source: Bitmap, colorArgb: Int): Bitmap {
            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val rgb = colorArgb and 0x00FFFFFF
            for (i in pixels.indices) {
                val p = pixels[i]
                val luminance = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                // Soft edge: alpha ramps up as pixels get darker than the threshold
                val alpha = ((INK_THRESHOLD - luminance) * 255 / 60).coerceIn(0, 255)
                pixels[i] = (alpha shl 24) or rgb
            }
            out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            return out
        }

        /** Crops to non-transparent pixels with a small margin; null when nothing was drawn. */
        fun trimTransparent(bitmap: Bitmap, margin: Int = 8): Bitmap? {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var left = w; var top = h; var right = -1; var bottom = -1
            for (y in 0 until h) for (x in 0 until w) {
                if (Color.alpha(pixels[y * w + x]) > 16) {
                    if (x < left) left = x; if (x > right) right = x
                    if (y < top) top = y; if (y > bottom) bottom = y
                }
            }
            if (right < 0) return null
            val l = (left - margin).coerceAtLeast(0)
            val t = (top - margin).coerceAtLeast(0)
            val r = (right + margin).coerceAtMost(w - 1)
            val b = (bottom + margin).coerceAtMost(h - 1)
            return Bitmap.createBitmap(bitmap, l, t, r - l + 1, b - t + 1)
        }
    }
}
