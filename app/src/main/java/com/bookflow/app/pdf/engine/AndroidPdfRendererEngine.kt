package com.bookflow.app.pdf.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/** Android raster rendering plus real PDFBox text/navigation, behind a single engine contract. */
class AndroidPdfRendererEngine(private val context: Context) : PdfEngine {
    override val engineName = "Android PdfRenderer + PDFBox text"
    private val lock = Any()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var temporaryFile: File? = null
    private val textIndex = PdfTextIndex(context)
    private val dimensions = ConcurrentHashMap<Int, PdfPageDimensions>()
    @Volatile private var pageCount = 0
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val thumbnails = object : LruCache<Int, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
    }

    override suspend fun openDocument(source: PdfSource): PdfDocumentResult = withContext(Dispatchers.IO) {
        synchronized(lock) {
            close()
            try {
                var textSource = source
                descriptor = when (source) {
                    is PdfSource.FileSource -> ParcelFileDescriptor.open(source.file, ParcelFileDescriptor.MODE_READ_ONLY)
                    is PdfSource.UriSource -> requireNotNull(source.context.contentResolver.openFileDescriptor(source.uri, "r")) { "Document is no longer available" }
                    is PdfSource.AssetSource -> {
                        val file = File.createTempFile("bookflow_asset_", ".pdf", context.cacheDir)
                        temporaryFile = file
                        source.context.assets.open(source.assetPath).use { input -> file.outputStream().use { input.copyTo(it) } }
                        textSource = PdfSource.FileSource(file)
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                }
                renderer = PdfRenderer(requireNotNull(descriptor))
                pageCount = requireNotNull(renderer).pageCount
                check(pageCount > 0) { "This PDF has no pages" }
                textIndex.open(textSource)
                PdfDocumentResult.Success(pageCount)
            } catch (e: Exception) {
                close()
                PdfDocumentResult.Error("Cannot open PDF. It may be password-protected, damaged, or unavailable. ${e.message.orEmpty()}", e)
            }
        }
    }
    override fun getPageCount() = pageCount
    // Synchronous UI queries only touch the dimension cache, never a live PDF page.
    override fun getPageDimensions(pageIndex: Int) = dimensions[pageIndex] ?: PdfPageDimensions(595f, 842f)
    override suspend fun loadPageDimensions(pageIndex: Int): PdfPageDimensions = withContext(Dispatchers.IO) {
        synchronized(lock) {
            dimensions[pageIndex] ?: requireNotNull(renderer).openPage(pageIndex).use { page ->
                PdfPageDimensions(page.width.toFloat(), page.height.toFloat()).also { dimensions[pageIndex] = it }
            }
        }
    }
    override suspend fun renderPage(pageIndex: Int, scale: Float, quality: RenderQuality): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val active = renderer ?: return@synchronized null
            if (pageIndex !in 0 until pageCount) return@synchronized null
            val key = "${pageIndex}_${scale}_${quality.name}"
            cache.get(key)?.let { return@synchronized it }
            render(active, pageIndex, scale * quality.scaleMultiplier, 4_000_000)?.also { cache.put(key, it) }
        }
    }
    override suspend fun renderThumbnail(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val active = renderer ?: return@synchronized null
            if (pageIndex !in 0 until pageCount) return@synchronized null
            thumbnails.get(pageIndex) ?: render(active, pageIndex, .22f, 160_000)?.also { thumbnails.put(pageIndex, it) }
        }
    }
    private fun render(active: PdfRenderer, index: Int, requestedScale: Float, maxPixels: Int): Bitmap? = try {
        active.openPage(index).use { page ->
            dimensions[index] = PdfPageDimensions(page.width.toFloat(), page.height.toFloat())
            val scale = minOf(requestedScale.coerceIn(.05f, 4f), sqrt(maxPixels.toFloat() / (page.width.toFloat() * page.height)))
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, Matrix().apply { postScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    } catch (_: Exception) { null }

    override suspend fun getTableOfContents() = textIndex.outline()
    override suspend fun search(query: String) = textIndex.search(query)
    override suspend fun extractText(pageIndex: Int) = textIndex.extractText(pageIndex)
    override suspend fun selectTextAt(pageIndex: Int, startNormalized: PdfRect, endNormalized: PdfRect) = textIndex.select(pageIndex, startNormalized.left, startNormalized.top)
    override suspend fun selectTextAtPoint(pageIndex: Int, normX: Float, normY: Float) = textIndex.select(pageIndex, normX, normY)
    override suspend fun selectTextRange(pageIndex: Int, startX: Float, startY: Float, endX: Float, endY: Float) =
        textIndex.selectRange(pageIndex, startX, startY, endX, endY)
    override suspend fun internalLinkAt(pageIndex: Int, x: Float, y: Float) = textIndex.internalLinkAt(pageIndex, x, y)
    override suspend fun documentMetadata() = textIndex.metadata()
    override fun evictPage(pageIndex: Int) { cache.snapshot().keys.filter { it.startsWith("${pageIndex}_") }.forEach { cache.remove(it) } }
    override fun close() = synchronized(lock) {
        // UI references may still hold a bitmap; eviction deliberately does not recycle it.
        cache.evictAll(); thumbnails.evictAll(); dimensions.clear()
        runCatching { renderer?.close() }; renderer = null
        runCatching { descriptor?.close() }; descriptor = null
        textIndex.close()
        temporaryFile?.delete(); temporaryFile = null
        pageCount = 0
    }
}
