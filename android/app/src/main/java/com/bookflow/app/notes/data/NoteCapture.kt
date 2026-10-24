package com.bookflow.app.notes.data

import android.graphics.Bitmap
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.HtmlText
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import java.util.UUID
import kotlin.math.ceil

/** What a reader sends to a note. Page indices are zero-based. */
sealed interface CaptureSource {
    val bookId: String
    val page: Int

    data class Quote(val text: String, override val bookId: String, override val page: Int) : CaptureSource
    data class Clip(val bitmap: Bitmap, override val bookId: String, override val page: Int) : CaptureSource
}

/** Builds note elements from reader captures; placement on a page is the repository's job. */
class NoteCapture(private val files: NoteFileStore) {

    fun element(noteId: String, source: CaptureSource, bookTitle: String): NoteElement = when (source) {
        is CaptureSource.Quote -> quote(source, bookTitle)
        is CaptureSource.Clip -> clip(noteId, source)
    }

    private fun quote(source: CaptureSource.Quote, bookTitle: String): NoteElement {
        val text = source.text.trim().replace(Regex("\\s+"), " ")
        val html = "<p>“${HtmlText.escape(text)}”</p><p><i>— ${HtmlText.escape(bookTitle)}, p. ${source.page + 1}</i></p>"
        return NoteElement(
            id = UUID.randomUUID().toString(), kind = ElementKind.PDF_CLIP, x = 0f, y = 0f,
            width = QUOTE_WIDTH, height = estimatedQuoteHeight(text.length + bookTitle.length + 12),
            html = html, quoteText = text, sourceBookId = source.bookId, sourcePage = source.page
        )
    }

    private fun clip(noteId: String, source: CaptureSource.Clip): NoteElement {
        val image = files.saveBitmap(noteId, source.bitmap)
        val aspect = image.height.toFloat() / image.width
        // Wide clips span most of the page; tall ones are narrower so they stay readable without scrolling
        val width = if (aspect > 1.2f) 0.5f else 0.82f
        return NoteElement(
            id = UUID.randomUUID().toString(), kind = ElementKind.PDF_CLIP, x = 0f, y = 0f,
            width = width, height = width * PAGE_WIDTH * aspect / PAGE_HEIGHT,
            imagePath = image.path, sourceBookId = source.bookId, sourcePage = source.page
        )
    }

    companion object {
        const val QUOTE_WIDTH = 0.88f

        /** Rough height so placement leaves room; the text box resizes to its real height once shown. */
        fun estimatedQuoteHeight(characters: Int): Float {
            val charsPerLine = (QUOTE_WIDTH * PAGE_WIDTH - 40f) / 13f
            val lines = ceil(characters / charsPerLine).toInt() + 1
            return ((lines * 44f + 40f) / PAGE_HEIGHT).coerceIn(0.06f, 0.85f)
        }
    }
}
