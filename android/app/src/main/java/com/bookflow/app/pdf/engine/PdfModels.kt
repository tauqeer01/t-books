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

/**
 * Merges glyph rects (in reading order) into one rect per text line.
 */
fun List<PdfRect>.mergedIntoLines(): List<PdfRect> {
    val lines = mutableListOf<PdfRect>()
    for (rect in this) {
        val last = lines.lastOrNull()
        val centerY = (rect.top + rect.bottom) / 2f
        if (last != null && centerY in last.top..last.bottom && rect.left >= last.left - rect.height) {
            lines[lines.lastIndex] = PdfRect(
                minOf(last.left, rect.left), minOf(last.top, rect.top),
                maxOf(last.right, rect.right), maxOf(last.bottom, rect.bottom)
            )
        } else {
            lines += rect
        }
    }
    return lines
}

/**
 * Text markup annotations (highlight/underline/strikethrough) store one rect per line in
 * BookAnnotation.strokePathData as "rects:l,t,r,b;l,t,r,b". The rect* fields hold their union for hit testing.
 */
object MarkupRects {
    private const val PREFIX = "rects:"

    fun encode(rects: List<PdfRect>): String = PREFIX + rects.joinToString(";") {
        String.format(java.util.Locale.US, "%.5f,%.5f,%.5f,%.5f", it.left, it.top, it.right, it.bottom)
    }

    fun decode(data: String?): List<PdfRect>? {
        if (data == null || !data.startsWith(PREFIX)) return null
        return data.removePrefix(PREFIX).split(";").mapNotNull { item ->
            val v = item.split(",").mapNotNull { it.toFloatOrNull() }
            if (v.size == 4) PdfRect(v[0], v[1], v[2], v[3]) else null
        }.takeIf { it.isNotEmpty() }
    }

    fun union(rects: List<PdfRect>): PdfRect = PdfRect(
        rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom }
    )
}

fun com.bookflow.app.domain.model.BookAnnotation.markupRects(): List<PdfRect> =
    MarkupRects.decode(strokePathData) ?: listOf(PdfRect(rectLeft, rectTop, rectRight, rectBottom))

/** A note dropped on the page with the sticky-note tool, rather than attached to selected text. */
val com.bookflow.app.domain.model.BookAnnotation.isStickyNote: Boolean
    get() = type == com.bookflow.app.domain.model.AnnotationType.NOTE && selectedText.isBlank()
