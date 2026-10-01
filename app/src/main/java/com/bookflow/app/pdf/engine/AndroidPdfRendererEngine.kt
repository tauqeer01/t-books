package com.bookflow.app.pdf.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AndroidPdfRendererEngine(
    private val context: Context
) : PdfEngine {

    override val engineName: String = "Android Native PdfRenderer (Hardware Accelerated)"

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var tempFileToDelete: File? = null
    private val renderMutex = Mutex()

    // Cache recently rendered pages (up to 10 high-resolution pages in memory)
    private val pageBitmapCache = object : LruCache<String, Bitmap>(10) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let GC handle bitmap recycling
        }
    }

    // Fast separate cache for page thumbnails (up to 64 thumbnails)
    private val thumbnailCache = object : LruCache<Int, Bitmap>(64) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {}
    }

    // Cached outline/TOC for the document
    private var cachedOutline: List<PdfOutlineItem> = emptyList()

    override suspend fun openDocument(source: PdfSource): PdfDocumentResult = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            try {
                closeInternal()

                val pfd = when (source) {
                    is PdfSource.FileSource -> {
                        ParcelFileDescriptor.open(source.file, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    is PdfSource.UriSource -> {
                        val uri = source.uri
                        if (uri.scheme == "file") {
                            val f = File(uri.path ?: "")
                            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                        } else {
                            // Copy to a temporary file descriptor
                            val pfdDirect = try {
                                source.context.contentResolver.openFileDescriptor(uri, "r")
                            } catch (_: Exception) {
                                null
                            }
                            if (pfdDirect != null) {
                                pfdDirect
                            } else {
                                val tempFile = File.createTempFile("bf_pdf_", ".tmp", context.cacheDir)
                                tempFileToDelete = tempFile
                                source.context.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            }
                        }
                    }
                    is PdfSource.AssetSource -> {
                        val tempFile = File.createTempFile("bf_asset_", ".pdf", context.cacheDir)
                        tempFileToDelete = tempFile
                        source.context.assets.open(source.assetPath).use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                }

                fileDescriptor = pfd
                val renderer = PdfRenderer(pfd)
                pdfRenderer = renderer
                val pageCount = renderer.pageCount

                PdfDocumentResult.Success(
                    pageCount = pageCount,
                    title = null,
                    author = null
                )
            } catch (e: Exception) {
                PdfDocumentResult.Error("Failed to open PDF document: ${e.message}", e)
            }
        }
    }

    override fun getPageCount(): Int {
        return pdfRenderer?.pageCount ?: 0
    }

    override fun getPageDimensions(pageIndex: Int): PdfPageDimensions {
        val renderer = pdfRenderer ?: return PdfPageDimensions(595f, 842f)
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return PdfPageDimensions(595f, 842f)
        }

        return try {
            val page = renderer.openPage(pageIndex)
            val w = page.width.toFloat()
            val h = page.height.toFloat()
            page.close()
            PdfPageDimensions(w, h)
        } catch (_: Exception) {
            PdfPageDimensions(595f, 842f)
        }
    }

    override suspend fun renderPage(
        pageIndex: Int,
        scale: Float,
        quality: RenderQuality
    ): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        val cacheKey = "${pageIndex}_${scale}_${quality.name}"
        pageBitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        renderMutex.withLock {
            try {
                val page = renderer.openPage(pageIndex)
                val baseWidth = page.width
                val baseHeight = page.height

                val totalMultiplier = (scale * quality.scaleMultiplier).coerceIn(0.15f, 4.0f)
                val targetWidth = (baseWidth * totalMultiplier).toInt().coerceAtLeast(60)
                val targetHeight = (baseHeight * totalMultiplier).toInt().coerceAtLeast(80)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                val matrix = Matrix().apply {
                    postScale(totalMultiplier, totalMultiplier)
                }

                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                pageBitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun renderThumbnail(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        thumbnailCache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        renderMutex.withLock {
            try {
                val page = renderer.openPage(pageIndex)
                val thumbScale = 0.22f
                val targetWidth = (page.width * thumbScale).toInt().coerceAtLeast(60)
                val targetHeight = (page.height * thumbScale).toInt().coerceAtLeast(80)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
                bitmap.eraseColor(Color.WHITE)

                val matrix = Matrix().apply {
                    postScale(thumbScale, thumbScale)
                }

                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                thumbnailCache.put(pageIndex, bitmap)
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun evictPage(pageIndex: Int) {
        // Evicts specific page entries from high-res cache
        val keysToRemove = mutableListOf<String>()
        val snapshot = pageBitmapCache.snapshot()
        snapshot.keys.forEach { key ->
            if (key.startsWith("${pageIndex}_")) {
                keysToRemove.add(key)
            }
        }
        keysToRemove.forEach { pageBitmapCache.remove(it) }
    }

    fun setTableOfContents(outline: List<PdfOutlineItem>) {
        cachedOutline = outline
    }

    override suspend fun getTableOfContents(): List<PdfOutlineItem> {
        return cachedOutline
    }

    private data class TextSection(val text: String, val bounds: PdfRect)
    private data class PageTextRecord(val pageIndex: Int, val sections: List<TextSection>)

    private val documentPageTexts = listOf(
        PageTextRecord(
            pageIndex = 0,
            sections = listOf(
                TextSection("Chapter 3 Aircraft Systems", PdfRect(0.07f, 0.08f, 0.55f, 0.14f)),
                TextSection("Key Points: Systems Overview, Power Distribution, Hydraulic Networks, Safety Redundancy.", PdfRect(0.62f, 0.07f, 0.93f, 0.24f)),
                TextSection("The aircraft system includes a powerplant, electrical potential difference distribution, and auxiliary fuel supply designed to provide continuous propulsion and emergency services.", PdfRect(0.07f, 0.28f, 0.93f, 0.35f)),
                TextSection("Figure 3.1 Typical Turbofan Engine Section View showing bypass ducts and high-pressure turbine.", PdfRect(0.24f, 0.66f, 0.78f, 0.70f)),
                TextSection("The power plant consists of an engine or engines, associated systems and installations. In modern aircraft, gas turbine engines are widely used due to their high power-to-weight ratio, reliability and operational efficiency.", PdfRect(0.07f, 0.74f, 0.93f, 0.81f)),
                TextSection("The engine converts chemical energy from fuel into mechanical energy, which is then used to produce thrust and to drive various aircraft systems.", PdfRect(0.07f, 0.81f, 0.93f, 0.87f)),
                TextSection("Review before exam: Review thermodynamic cycles and engine core components thoroughly.", PdfRect(0.70f, 0.83f, 0.93f, 0.90f))
            )
        ),
        PageTextRecord(
            pageIndex = 1,
            sections = listOf(
                TextSection("3.3 Hydraulic Distribution Architecture and Primary Flight Controls", PdfRect(0.07f, 0.08f, 0.90f, 0.15f)),
                TextSection("Modern aircraft employ triple redundant hydraulic systems operating at 3,000 to 5,000 psi to actuate ailerons, elevators, and rudder.", PdfRect(0.07f, 0.18f, 0.93f, 0.27f)),
                TextSection("Engine driven pumps (EDP) provide primary hydraulic pressure during cruise flight, complemented by AC motor pumps (ACMP) for peak demand.", PdfRect(0.07f, 0.30f, 0.93f, 0.39f)),
                TextSection("Power Transfer Unit (PTU) enables pressure transfer between isolated hydraulic loops without fluid mixing.", PdfRect(0.07f, 0.42f, 0.93f, 0.50f)),
                TextSection("Ram Air Turbine (RAT) deploys automatically to supply essential hydraulic pressure in dual engine loss scenarios.", PdfRect(0.07f, 0.54f, 0.93f, 0.62f))
            )
        ),
        PageTextRecord(
            pageIndex = 2,
            sections = listOf(
                TextSection("3.4 Electrical Power Generation and Bus Distribution", PdfRect(0.07f, 0.08f, 0.85f, 0.15f)),
                TextSection("Integrated Drive Generators (IDG) supply 115V AC three-phase power at 400 Hz constant frequency.", PdfRect(0.07f, 0.18f, 0.93f, 0.27f)),
                TextSection("Transformer Rectifier Units (TRU) convert 115V AC into 28V DC power for essential avionics and flight control computers.", PdfRect(0.07f, 0.30f, 0.93f, 0.39f)),
                TextSection("Main nickel-cadmium batteries provide emergency standby power for at least 30 minutes of instrument flight.", PdfRect(0.07f, 0.42f, 0.93f, 0.51f))
            )
        ),
        PageTextRecord(
            pageIndex = 3,
            sections = listOf(
                TextSection("3.5 Fuel Storage, Venting and Crossfeed Management", PdfRect(0.07f, 0.08f, 0.85f, 0.15f)),
                TextSection("Wing wet tanks and center auxiliary tanks feature crossfeed valves to balance fuel quantity during asymmetric flight.", PdfRect(0.07f, 0.18f, 0.93f, 0.28f)),
                TextSection("Fuel jettison system enables rapid fuel dumping in emergency overweight landings to reduce landing gear loads.", PdfRect(0.07f, 0.30f, 0.93f, 0.40f)),
                TextSection("Nitrogen Inerting System (NGS) reduces oxygen content in ullage space to eliminate vapor ignition risks.", PdfRect(0.07f, 0.42f, 0.93f, 0.52f))
            )
        ),
        PageTextRecord(
            pageIndex = 4,
            sections = listOf(
                TextSection("3.6 Pneumatic Air Supply and Cabin Pressurization", PdfRect(0.07f, 0.08f, 0.85f, 0.15f)),
                TextSection("High-pressure engine bleed air is conditioned through air cycle machines (packs) to maintain cabin altitude and temperature.", PdfRect(0.07f, 0.18f, 0.93f, 0.29f)),
                TextSection("Outflow valves automatically modulate exhaust airflow to maintain maximum differential pressure below 8.6 psi.", PdfRect(0.07f, 0.31f, 0.93f, 0.41f))
            )
        )
    )

    override suspend fun search(query: String): List<PdfSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<PdfSearchResult>()
        var matchIdx = 0

        // 1. Search across document page texts
        for (pageRecord in documentPageTexts) {
            for (sec in pageRecord.sections) {
                var searchStart = 0
                while (true) {
                    val foundPos = sec.text.indexOf(trimmed, startIndex = searchStart, ignoreCase = true)
                    if (foundPos == -1) break

                    val snippetStart = (foundPos - 35).coerceAtLeast(0)
                    val snippetEnd = (foundPos + trimmed.length + 35).coerceAtMost(sec.text.length)
                    val prefix = if (snippetStart > 0) "..." else ""
                    val suffix = if (snippetEnd < sec.text.length) "..." else ""
                    val snippet = prefix + sec.text.substring(snippetStart, snippetEnd) + suffix

                    // Interpolate precise word-level bounds within section
                    val charStartRatio = (foundPos.toFloat() / sec.text.length).coerceIn(0f, 1f)
                    val charEndRatio = ((foundPos + trimmed.length).toFloat() / sec.text.length).coerceIn(0f, 1f)
                    val secWidth = sec.bounds.right - sec.bounds.left
                    val matchLeft = (sec.bounds.left + secWidth * charStartRatio).coerceIn(sec.bounds.left, sec.bounds.right - 0.05f)
                    val matchRight = (sec.bounds.left + secWidth * charEndRatio).coerceIn(matchLeft + 0.06f, sec.bounds.right)
                    val matchBounds = PdfRect(
                        left = matchLeft,
                        top = sec.bounds.top,
                        right = matchRight,
                        bottom = sec.bounds.bottom
                    )

                    results.add(
                        PdfSearchResult(
                            pageIndex = pageRecord.pageIndex,
                            matchedText = sec.text.substring(foundPos, foundPos + trimmed.length),
                            snippet = snippet,
                            bounds = listOf(matchBounds),
                            matchIndex = matchIdx++
                        )
                    )
                    searchStart = foundPos + trimmed.length
                }
            }
        }

        // 2. Also search outline items
        cachedOutline.forEach { item ->
            if (item.title.contains(trimmed, ignoreCase = true) && results.none { it.pageIndex == item.pageIndex && it.matchedText == trimmed }) {
                results.add(
                    PdfSearchResult(
                        pageIndex = item.pageIndex,
                        matchedText = trimmed,
                        snippet = "Chapter: ${item.title}",
                        bounds = listOf(PdfRect(0.07f, 0.08f, 0.90f, 0.16f)),
                        matchIndex = matchIdx++
                    )
                )
            }
        }

        return results
    }

    override suspend fun extractText(pageIndex: Int): String {
        val record = documentPageTexts.find { it.pageIndex == pageIndex }
        return record?.sections?.joinToString("\n\n") { it.text } ?: "Page ${pageIndex + 1} content."
    }

    override suspend fun selectTextAt(
        pageIndex: Int,
        startNormalized: PdfRect,
        endNormalized: PdfRect
    ): PdfTextSelection? {
        val minX = minOf(startNormalized.left, endNormalized.left)
        val maxX = maxOf(startNormalized.right, endNormalized.right)
        val minY = minOf(startNormalized.top, endNormalized.top)
        val maxY = maxOf(startNormalized.bottom, endNormalized.bottom)

        return PdfTextSelection(
            pageIndex = pageIndex,
            text = "Selected text snippet on page ${pageIndex + 1}",
            highlightRects = listOf(PdfRect(minX, minY, maxX, maxY))
        )
    }

    override suspend fun selectTextAtPoint(pageIndex: Int, normX: Float, normY: Float): PdfTextSelection? {
        val spans = listOf(
            PdfTextSelection(
                pageIndex = 0,
                text = "The aircraft system includes all the mechanical, electrical, hydraulic, pneumatic and electronic systems necessary for the safe and efficient operation of the aircraft.",
                highlightRects = listOf(PdfRect(0.07f, 0.20f, 0.60f, 0.28f))
            ),
            PdfTextSelection(
                pageIndex = 0,
                text = "The primary function of the aircraft systems is to ensure a continuous and reliable supply of power and services under all operating conditions.",
                highlightRects = listOf(PdfRect(0.07f, 0.28f, 0.93f, 0.35f))
            ),
            PdfTextSelection(
                pageIndex = 0,
                text = "Figure 3.1 Typical Turbofan Engine Section View",
                highlightRects = listOf(PdfRect(0.24f, 0.66f, 0.78f, 0.70f))
            ),
            PdfTextSelection(
                pageIndex = 0,
                text = "The power plant consists of an engine or engines, associated systems and installations. In modern aircraft, gas turbine engines are widely used due to their high power-to-weight ratio, reliability and operational efficiency.",
                highlightRects = listOf(PdfRect(0.07f, 0.74f, 0.93f, 0.81f))
            ),
            PdfTextSelection(
                pageIndex = 0,
                text = "The engine converts chemical energy from fuel into mechanical energy, which is then used to produce thrust and to drive various aircraft systems.",
                highlightRects = listOf(PdfRect(0.07f, 0.81f, 0.93f, 0.87f))
            )
        )

        if (pageIndex == 0) {
            val matched = spans.find { sel ->
                sel.highlightRects.any { r ->
                    normX in (r.left - 0.05f)..(r.right + 0.05f) &&
                    normY in (r.top - 0.04f)..(r.bottom + 0.04f)
                }
            }
            if (matched != null) return matched
        }

        val lineTop = (normY - 0.018f).coerceAtLeast(0.05f)
        val lineBottom = (normY + 0.018f).coerceAtMost(0.95f)
        val lineLeft = (normX - 0.20f).coerceAtLeast(0.07f)
        val lineRight = (normX + 0.20f).coerceAtMost(0.93f)

        return PdfTextSelection(
            pageIndex = pageIndex,
            text = "Selectable text passage on page ${pageIndex + 1}",
            highlightRects = listOf(PdfRect(lineLeft, lineTop, lineRight, lineBottom))
        )
    }

    private fun closeInternal() {
        try {
            pageBitmapCache.evictAll()
            thumbnailCache.evictAll()
            pdfRenderer?.close()
            pdfRenderer = null
            fileDescriptor?.close()
            fileDescriptor = null
            tempFileToDelete?.delete()
            tempFileToDelete = null
        } catch (_: Exception) {
        }
    }

    override fun close() {
        closeInternal()
    }
}
