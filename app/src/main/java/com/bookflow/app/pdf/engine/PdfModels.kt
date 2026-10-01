package com.bookflow.app.pdf.engine

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Representation of a source for loading a PDF document.
 */
sealed interface PdfSource {
    data class FileSource(val file: File) : PdfSource
    data class UriSource(val uri: Uri, val context: Context) : PdfSource
    data class AssetSource(val assetPath: String, val context: Context) : PdfSource
}

/**
 * Page dimensions in PDF points.
 */
data class PdfPageDimensions(
    val width: Float,
    val height: Float
) {
    val aspectRatio: Float
        get() = if (height > 0) width / height else 1f
}

/**
 * Table of contents / Outline tree node.
 */
data class PdfOutlineItem(
    val title: String,
    val pageIndex: Int,
    val level: Int = 0,
    val children: List<PdfOutlineItem> = emptyList()
)

/**
 * PDF Search Result item.
 */
data class PdfSearchResult(
    val pageIndex: Int,
    val matchedText: String = "",
    val snippet: String,
    val bounds: List<PdfRect> = emptyList(),
    val matchIndex: Int = 0
)

/**
 * Normalized coordinate rectangle (0.0 to 1.0 relative to page width and height).
 */
data class PdfRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Selection state for text selection abstraction.
 */
data class PdfTextSelection(
    val pageIndex: Int,
    val text: String,
    val highlightRects: List<PdfRect>
)

/**
 * Render quality configuration.
 */
enum class RenderQuality(val scaleMultiplier: Float) {
    FAST(1.0f),
    STANDARD(1.5f),
    HIGH_DPI(2.0f),
    ULTRA(3.0f)
}

/**
 * Result of attempting to open a document.
 */
sealed interface PdfDocumentResult {
    data class Success(
        val pageCount: Int,
        val title: String? = null,
        val author: String? = null
    ) : PdfDocumentResult

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : PdfDocumentResult
}
