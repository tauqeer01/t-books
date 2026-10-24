package com.bookflow.app.notes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** App-private files for notes: `filesDir/notes/<noteId>/` holds page ink, images, and the thumbnail. */
class NoteFileStore(private val context: Context) {
    private val root get() = File(context.filesDir, "notes")

    fun noteDir(noteId: String) = File(root, noteId).apply { mkdirs() }
    fun inkFile(noteId: String, pageId: String) = File(noteDir(noteId), "page_$pageId.ink")

    fun deleteNote(noteId: String) { File(root, noteId).deleteRecursively() }
    fun deletePage(noteId: String, pageId: String) { inkFile(noteId, pageId).delete() }

    /** Copies a picked or captured photo into the note, downscaled to [MAX_IMAGE_EDGE] and stored as WebP. */
    fun importImage(noteId: String, uri: Uri): ImportedImage = importImageTo(noteDir(noteId), uri)

    /** Same as [importImage] for any app-private folder (flashcards use their own). */
    fun importImageTo(dir: File, uri: Uri): ImportedImage {
        requireSpace()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("This image could not be read")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_EDGE) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("This image could not be read")
        val oriented = ExifRotation.apply(context, uri, decoded)
        return saveBitmapTo(dir, oriented).also { if (oriented !== decoded) decoded.recycle(); oriented.recycle() }
    }

    fun saveBitmap(noteId: String, bitmap: Bitmap): ImportedImage = saveBitmapTo(noteDir(noteId), bitmap)

    fun saveBitmapTo(dir: File, bitmap: Bitmap): ImportedImage {
        requireSpace()
        val scale = minOf(1f, MAX_IMAGE_EDGE.toFloat() / max(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true) else bitmap
        val file = File(dir.apply { mkdirs() }, "img_${UUID.randomUUID()}.webp")
        file.outputStream().use { scaled.compress(webp(), 88, it) }
        val result = ImportedImage(file.absolutePath, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()
        return result
    }

    /** Copy of an image for a duplicated element, so deleting one never removes the other's file. */
    fun duplicate(noteId: String, path: String): String {
        val copy = File(noteDir(noteId), "img_${UUID.randomUUID()}.webp")
        File(path).copyTo(copy)
        return copy.absolutePath
    }

    /** A new file name per save, so image caches keyed by path never show a stale cover. */
    fun saveThumbnail(noteId: String, bitmap: Bitmap): String {
        val dir = noteDir(noteId)
        val file = File(dir, "thumb_${System.currentTimeMillis()}.webp")
        file.outputStream().use { bitmap.compress(webp(), 80, it) }
        dir.listFiles { f -> f.name.startsWith("thumb") && f != file }?.forEach { it.delete() }
        return file.absolutePath
    }

    private fun requireSpace() {
        if (StatFs(context.filesDir.path).availableBytes < MIN_FREE_BYTES) throw IOException("Not enough storage space to add this image")
    }

    @Suppress("DEPRECATION")
    private fun webp() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    data class ImportedImage(val path: String, val width: Int, val height: Int)

    private companion object {
        const val MAX_IMAGE_EDGE = 2048
        const val MIN_FREE_BYTES = 50L * 1024 * 1024
    }
}

/** Camera photos are often stored sideways with an EXIF orientation tag. */
private object ExifRotation {
    fun apply(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (android.media.ExifInterface(stream).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
