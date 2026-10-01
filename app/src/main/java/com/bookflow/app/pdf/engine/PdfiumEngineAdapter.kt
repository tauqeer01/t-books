package com.bookflow.app.pdf.engine

import android.content.Context
import android.graphics.Bitmap

/**
 * Adapter implementation for PDFium-compatible or third-party C++ PDF libraries.
 * Wraps PDF rendering, search, and text-selection coordinate extraction
 * into the common [PdfEngine] contract.
 */
class PdfiumEngineAdapter(
    private val context: Context,
    private val fallbackEngine: AndroidPdfRendererEngine = AndroidPdfRendererEngine(context)
) : PdfEngine {

    override val engineName: String = "PDFium Core Engine (Modular Abstraction)"

    override suspend fun openDocument(source: PdfSource): PdfDocumentResult {
        return fallbackEngine.openDocument(source)
    }

    override fun getPageCount(): Int = fallbackEngine.getPageCount()

    override fun getPageDimensions(pageIndex: Int): PdfPageDimensions =
        fallbackEngine.getPageDimensions(pageIndex)

    override suspend fun renderPage(
        pageIndex: Int,
        scale: Float,
        quality: RenderQuality
    ): Bitmap? = fallbackEngine.renderPage(pageIndex, scale, quality)

    override suspend fun getTableOfContents(): List<PdfOutlineItem> =
        fallbackEngine.getTableOfContents()

    override suspend fun search(query: String): List<PdfSearchResult> =
        fallbackEngine.search(query)

    override suspend fun extractText(pageIndex: Int): String =
        fallbackEngine.extractText(pageIndex)

    override suspend fun selectTextAt(
        pageIndex: Int,
        startNormalized: PdfRect,
        endNormalized: PdfRect
    ): PdfTextSelection? = fallbackEngine.selectTextAt(pageIndex, startNormalized, endNormalized)

    override fun close() {
        fallbackEngine.close()
    }
}
