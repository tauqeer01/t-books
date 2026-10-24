package com.bookflow.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

data class DocumentMetadata(
    val fileName: String,
    val fileSizeBytes: Long
)

object FileUtils {
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }

    fun extractDocumentMetadata(context: Context, uri: Uri): DocumentMetadata {
        var displayName: String? = null
        var sizeBytes: Long = 0L

        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            displayName = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (displayName == null) {
            val path = uri.path ?: ""
            val cut = path.lastIndexOf('/')
            displayName = if (cut != -1) path.substring(cut + 1) else "document.pdf"
        }

        return DocumentMetadata(
            fileName = displayName ?: "document.pdf",
            fileSizeBytes = sizeBytes
        )
    }

    fun saveThumbnail(context: Context, bookId: String, bitmap: Bitmap): String {
        val thumbDir = File(context.filesDir, "thumbnails")
        if (!thumbDir.exists()) thumbDir.mkdirs()

        val thumbFile = File(thumbDir, "thumb_${bookId}.jpg")
        FileOutputStream(thumbFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return thumbFile.absolutePath
    }

    fun deleteThumbnail(path: String?) {
        if (path == null) return
        try {
            val file = File(path)
            if (file.exists() && file.parentFile?.name == "thumbnails") {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun deleteDocumentSaf(context: Context, uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }
}
