package com.bookflow.app.pdf.engine

import android.content.Context
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.RandomAccessRead
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.FileInputStream
import java.nio.ByteBuffer

/** Text/outline companion to Android's renderer, including Android 10–14.
 * Reads seekable SAF descriptors in place. Only the last eight text pages are cached.
 */
internal class PdfTextIndex(private val context: Context) {
    private val lock = Any()
    private var document: PDDocument? = null
    private var source: PdfSource? = null
    private val pages = object : LinkedHashMap<Int, TextPage>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TextPage>?) = size > 8
    }
    val title: String? get() = document?.documentInformation?.title
    val author: String? get() = document?.documentInformation?.author

    // Parse lazily: importing/rendering a scan must not depend on text extraction.
    fun open(source: PdfSource) = synchronized(lock) { close(); this.source = source }

    private fun doc(): PDDocument {
        document?.let { return it }
        // Heavy I/O: intentionally NOT inside synchronized(lock) to avoid blocking
        // other callers. The double-check pattern below is safe because PDDocument
        // assignment is an atomic reference write, and duplicate parses are harmless
        // (the loser is simply closed).
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = when (val s = checkNotNull(source)) {
            is PdfSource.FileSource -> FileInputStream(s.file)
            is PdfSource.UriSource -> {
                val descriptor = requireNotNull(s.context.contentResolver.openFileDescriptor(s.uri, "r"))
                ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            }
            is PdfSource.AssetSource -> error("Text extraction requires a seekable document")
        }
        val access = DescriptorAccess(input)
        try {
            val parser = PDFParser(access)
            parser.parse()
            val parsed = parser.pdDocument
            synchronized(lock) {
                // Double-check: another thread may have parsed while we were working
                document?.let { access.close(); return it }
                document = parsed
                return parsed
            }
        } catch (e: Exception) { access.close(); throw e }
    }

    suspend fun outline(): List<PdfOutlineItem> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val doc = doc()
            val result = mutableListOf<PdfOutlineItem>()
            fun visit(node: PDOutlineNode, level: Int) {
                if (level > 32 || result.size >= 10000) return
                node.children().forEach { child ->
                    val page = child.findDestinationPage(doc)
                    val index = page?.let { doc.pages.indexOf(it) } ?: -1
                    if (index >= 0) result += PdfOutlineItem(child.title ?: "Untitled", index, level)
                    visit(child, level + 1)
                }
            }
            doc.documentCatalog.documentOutline?.let { visit(it, 0) }
            result
        }
    }

    suspend fun metadata(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        synchronized(lock) { doc().documentInformation.let { it.title to it.author } }
    }

    suspend fun internalLinkAt(index: Int, x: Float, y: Float): Int? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val doc = doc()
            val page = doc.getPage(index)
            val crop = page.cropBox
            // Transform display-normalized coordinates back into the unrotated PDF crop box.
            val point = when ((page.rotation % 360 + 360) % 360) {
                90 -> y to x
                180 -> (1f - x) to y
                270 -> (1f - y) to (1f - x)
                else -> x to (1f - y)
            }
            val px = crop.lowerLeftX + point.first * crop.width
            val py = crop.lowerLeftY + point.second * crop.height
            val link = page.annotations.filterIsInstance<com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink>()
                .firstOrNull { it.rectangle?.contains(px, py) == true } ?: return@synchronized null
            val destination = link.destination ?: (link.action as? com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo)?.destination
            val resolved = when (destination) {
                is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination -> destination
                is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination -> doc.documentCatalog.findNamedDestinationPage(destination)
                else -> null
            } ?: return@synchronized null
            val target = resolved.page?.let { doc.pages.indexOf(it) } ?: resolved.retrievePageNumber()
            target.takeIf { it in 0 until doc.numberOfPages }
        }
    }

    private data class Glyph(val start: Int, val end: Int, val bounds: PdfRect)
    private data class TextPage(val text: String, val glyphs: List<Glyph>)

    private fun page(index: Int): TextPage {
        pages[index]?.let { return it }
        val doc = doc()
        val page = doc.getPage(index)
        val text = StringBuilder()
        val glyphs = mutableListOf<Glyph>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(value: String, positions: MutableList<TextPosition>) {
                positions.forEach { position ->
                    val start = text.length
                    text.append(position.unicode)
                    val left = position.xDirAdj / page.cropBox.width
                    val top = (position.yDirAdj - position.heightDir) / page.cropBox.height
                    val right = (position.xDirAdj + position.widthDirAdj) / page.cropBox.width
                    val bottom = (position.yDirAdj + position.heightDir * .2f) / page.cropBox.height
                    val bounds = when ((page.rotation % 360 + 360) % 360) {
                        90 -> PdfRect(1f - bottom, left, 1f - top, right)
                        180 -> PdfRect(1f - right, 1f - bottom, 1f - left, 1f - top)
                        270 -> PdfRect(top, 1f - right, bottom, 1f - left)
                        else -> PdfRect(left, top, right, bottom)
                    }
                    glyphs += Glyph(start, text.length, PdfRect(bounds.left.coerceIn(0f, 1f), bounds.top.coerceIn(0f, 1f), bounds.right.coerceIn(0f, 1f), bounds.bottom.coerceIn(0f, 1f)))
                }
            }
            override fun writeWordSeparator() { text.append(' ') }
            override fun writeLineSeparator() { text.append(' ') }
            override fun writeParagraphSeparator() { text.append(' ') }
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.getText(doc)
        return TextPage(text.toString(), glyphs).also { pages[index] = it }
    }

    suspend fun extractText(index: Int): String = withContext(Dispatchers.IO) {
        synchronized(lock) { page(index).text }
    }

    suspend fun search(query: String): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        val count = synchronized(lock) { doc().numberOfPages }
        val results = mutableListOf<PdfSearchResult>()
        for (index in 0 until count) {
            currentCoroutineContext().ensureActive()
            val page = synchronized(lock) { page(index) }
            var from = 0
            while (from < page.text.length) {
                currentCoroutineContext().ensureActive()
                val match = page.text.indexOf(needle, from, ignoreCase = true)
                if (match < 0) break
                val end = match + needle.length
                results += PdfSearchResult(index, page.text.substring(match, end),
                    page.text.substring((match - 60).coerceAtLeast(0), (end + 60).coerceAtMost(page.text.length)),
                    page.glyphs.filter { it.end > match && it.start < end }.map { it.bounds }, results.size)
                from = end
            }
        }
        results
    }

    suspend fun select(index: Int, x: Float, y: Float): PdfTextSelection? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val page = page(index)
            val hit = page.glyphs.firstOrNull { x in it.bounds.left..it.bounds.right && y in it.bounds.top..it.bounds.bottom }
                ?: return@synchronized null
            var start = hit.start
            var end = hit.end
            while (start > 0 && !page.text[start - 1].isWhitespace()) start--
            while (end < page.text.length && !page.text[end].isWhitespace()) end++
            val bounds = page.glyphs.filter { it.end > start && it.start < end }.map { it.bounds }
            PdfTextSelection(index, page.text.substring(start, end), bounds)
        }
    }

    fun close() = synchronized(lock) {
        runCatching { document?.close() }
        document = null
        source = null
        pages.clear()
    }
}

/** Small read buffer avoids a syscall for every PDF parser byte without copying the PDF. */
internal class DescriptorAccess(private val input: FileInputStream) : RandomAccessRead {
    private val channel = input.channel
    private var cursor = 0L
    private val buffer = ByteArray(16384)
    private var bufferStart = -1L
    private var bufferLength = 0
    override fun read(): Int {
        if (isEOF) return -1
        if (cursor < bufferStart || cursor >= bufferStart + bufferLength) {
            bufferStart = cursor
            bufferLength = channel.read(ByteBuffer.wrap(buffer), cursor)
            if (bufferLength <= 0) return -1
        }
        return buffer[(cursor++ - bufferStart).toInt()].toInt() and 255
    }
    override fun read(bytes: ByteArray) = read(bytes, 0, bytes.size)
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val n = channel.read(ByteBuffer.wrap(bytes, offset, length), cursor)
        if (n > 0) cursor += n
        return n
    }
    override fun getPosition() = cursor
    override fun seek(position: Long) { require(position >= 0); cursor = position }
    override fun length() = channel.size()
    override fun isClosed() = !channel.isOpen
    override fun peek(): Int { val value = read(); if (value >= 0) cursor--; return value }
    override fun rewind(bytes: Int) = seek(cursor - bytes)
    override fun readFully(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) { val n = read(bytes, offset, length - offset); if (n < 0) throw EOFException(); offset += n }
        return bytes
    }
    override fun isEOF() = cursor >= length()
    override fun available() = (length() - cursor).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    override fun close() = input.close()
}
