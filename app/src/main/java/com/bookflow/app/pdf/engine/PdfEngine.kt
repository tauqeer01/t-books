package com.bookflow.app.pdf.engine

import android.graphics.Bitmap

/**
 * Engine abstraction interface for PDF operations.
 * Decouples the UI and presentation layer from the specific PDF implementation
 * (e.g. Android Native PdfRenderer, PDFium, PSPDFKit, PdfBox, or custom C++ core).
 */
interface PdfEngine {
    /**
     * Engine identifier name (e.g., "Android Native PdfRenderer", "PDFium Engine").
     */
    val engineName: String

    /**
     * Opens a PDF document from the given source.
     */
    suspend fun openDocument(source: PdfSource): PdfDocumentResult

    /**
     * Total number of pages in the opened document.
     */
    fun getPageCount(): Int

    /**
     * Gets dimensions for a specific page.
     */
    fun getPageDimensions(pageIndex: Int): PdfPageDimensions

    /**
     * Renders a high-resolution Bitmap for the requested page.
     * Scale 1.0f represents native rendering dimension.
     */
    suspend fun renderPage(
        pageIndex: Int,
        scale: Float = 1.0f,
        quality: RenderQuality = RenderQuality.HIGH_DPI
    ): Bitmap?

    /**
     * Retrieves the document table of contents outline.
     */
    suspend fun getTableOfContents(): List<PdfOutlineItem>

    /**
     * Searches for occurrences of the text string across the document.
     */
    suspend fun search(query: String): List<PdfSearchResult>

    /**
     * Extracts text content from a specific page.
     */
    suspend fun extractText(pageIndex: Int): String

    /**
     * Retrieves text at normalized page coordinates for text selection.
     */
    suspend fun selectTextAt(pageIndex: Int, startNormalized: PdfRect, endNormalized: PdfRect): PdfTextSelection?

    /**
     * Retrieves selectable text at a specific normalized point (normX, normY) on the page.
     */
    suspend fun selectTextAtPoint(pageIndex: Int, normX: Float, normY: Float): PdfTextSelection? {
        val radius = 0.05f
        return selectTextAt(
            pageIndex,
            PdfRect(normX - radius, normY - 0.02f, normX + radius, normY + 0.02f),
            PdfRect(normX - radius, normY - 0.02f, normX + radius, normY + 0.02f)
        )
    }

    /**
     * Gets aspect ratio (width / height) for a specific page.
     */
    fun getPageAspectRatio(pageIndex: Int): Float {
        val dims = getPageDimensions(pageIndex)
        return if (dims.height > 0) dims.width / dims.height else 0.707f
    }

    /**
     * Renders a low-resolution fast thumbnail for navigation or carousel.
     */
    suspend fun renderThumbnail(pageIndex: Int): Bitmap? {
        return renderPage(pageIndex, scale = 0.25f, quality = RenderQuality.FAST)
    }

    /**
     * Optional hint to evict specific page resources from cache.
     */
    fun evictPage(pageIndex: Int) {}

    /**
     * Closes the active document and releases resources.
     */
    fun close()
}
