package com.bookflow.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.data.repository.BookRepositoryImpl
import com.bookflow.app.pdf.engine.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/** Textbook-sized documents: import, open, outline, search, and render must all work regardless of page count. */
@RunWith(AndroidJUnit4::class)
class LargePdfRegressionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** One chapter per 10 pages, each with a section entry per 2 pages, like a real textbook outline. */
    private fun textbook(pages: Int, scanned: Boolean = false): File {
        PDFBoxResourceLoader.init(context)
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "large_${pages}_${if (scanned) "scan" else "text"}.pdf")
        if (file.exists()) return file
        val random = Random(pages)
        PDDocument().use { doc ->
            val outline = PDDocumentOutline()
            var chapter: PDOutlineItem? = null
            repeat(pages) { index ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { content ->
                    if (scanned) {
                        val bitmap = Bitmap.createBitmap(600, 850, Bitmap.Config.ARGB_8888)
                        for (y in 0 until 850 step 2) for (x in 0 until 600 step 2) {
                            val v = 140 + random.nextInt(115)
                            bitmap.setPixel(x, y, Color.rgb(v, v, v))
                        }
                        content.drawImage(JPEGFactory.createFromImage(doc, bitmap, .8f), 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                        bitmap.recycle()
                    } else {
                        content.beginText(); content.setFont(PDType1Font.HELVETICA, 11f)
                        content.newLineAtOffset(50f, 780f)
                        content.showText("Chapter ${index / 10 + 1} page ${index + 1} hydraulic actuator lift")
                        repeat(30) { content.newLineAtOffset(0f, -18f); content.showText("Body line $it of page ${index + 1} with ordinary textbook prose.") }
                        content.endText()
                    }
                }
                if (index % 10 == 0) chapter = PDOutlineItem().apply { title = "Chapter ${index / 10 + 1}"; setDestination(page) }.also { outline.addLast(it) }
                if (index % 2 == 0) chapter!!.addLast(PDOutlineItem().apply { title = "Section ${index + 1}"; setDestination(page) })
            }
            doc.documentCatalog.documentOutline = outline
            doc.documentInformation.title = "Large textbook $pages"
            doc.save(file)
        }
        return file
    }

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also { Log.i(TAG, "$label took ${SystemClock.elapsedRealtime() - start} ms") }
    }

    private fun importsOpensAndNavigates(pages: Int, scanned: Boolean = false): Unit = runBlocking(Dispatchers.IO) {
        val file = textbook(pages, scanned)
        Log.i(TAG, "fixture ${file.name}: ${file.length() / 1024} KB")
        val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val engine = AndroidPdfRendererEngine(context)
        try {
            val repository = BookRepositoryImpl(context, db.bookDao(), db.collectionDao(), db.annotationDao(), db.bookmarkDao(), PdfEngineFactory(context))
            val book = timed("import $pages") { withTimeout(IMPORT_BUDGET_MS) { repository.importPdf(uri).getOrThrow() } }
            assertEquals(pages, book.pageCount)

            val opened = timed("open $pages") { withTimeout(OPEN_BUDGET_MS) { engine.openDocument(PdfSource.UriSource(uri, context)) } }
            assertTrue("open failed: $opened", opened is PdfDocumentResult.Success)
            assertNotNull(timed("render last $pages") { engine.renderPage(pages - 1, 1f, RenderQuality.STANDARD) })

            val outline = timed("outline $pages") { withTimeout(OUTLINE_BUDGET_MS) { engine.getTableOfContents() } }
            assertEquals(pages / 10 + pages / 2, outline.size)
            assertEquals(pages - 2, outline.last().pageIndex)

            if (!scanned) {
                val hits = timed("search $pages") { withTimeout(SEARCH_BUDGET_MS) { engine.search("page $pages with") } }
                assertTrue(hits.isNotEmpty())
                assertEquals(setOf(pages - 1), hits.map { it.pageIndex }.toSet())
            }
        } finally { engine.close(); db.close() }
    }

    @Test fun textbook150Pages() = importsOpensAndNavigates(150)
    @Test fun textbook500Pages() = importsOpensAndNavigates(500)
    @Test fun textbook1500Pages() = importsOpensAndNavigates(1500)
    @Test fun scanned300Pages() = importsOpensAndNavigates(300, scanned = true)

    /**
     * Stream data larger than the app heap used to crash import with OutOfMemoryError inside PDFBox.
     * Runs when a large scan has been placed in app storage, e.g.
     * `adb shell "cat /sdcard/Download/scan.pdf | run-as com.alhadaftech.bookflow sh -c 'mkdir -p files/stress && cat > files/stress/scan.pdf'"`.
     */
    @Test fun scanLargerThanHeapImportsAndOpens(): Unit = runBlocking(Dispatchers.IO) {
        val file = File(context.filesDir, "stress").listFiles { f -> f.extension == "pdf" }?.maxByOrNull { it.length() }
        assumeTrue("No stress PDF pushed", file != null && file.length() > Runtime.getRuntime().maxMemory())
        Log.i(TAG, "stress fixture ${file!!.name}: ${file.length() / 1024 / 1024} MB, heap ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
        val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
        val engine = AndroidPdfRendererEngine(context)
        try {
            val repository = BookRepositoryImpl(context, db.bookDao(), db.collectionDao(), db.annotationDao(), db.bookmarkDao(), PdfEngineFactory(context))
            val book = timed("stress import") { withTimeout(IMPORT_BUDGET_MS) { repository.importPdf(android.net.Uri.fromFile(file)).getOrThrow() } }
            assertTrue(book.pageCount > 0)
            assertTrue(engine.openDocument(PdfSource.FileSource(file)) is PdfDocumentResult.Success)
            assertNotNull(engine.renderPage(book.pageCount - 1, 1f, RenderQuality.STANDARD))
            // The text layer either loads with bounded heap or fails softly; it must never take the process down.
            val outline = timed("stress outline") { runCatching { engine.getTableOfContents() } }
            Log.i(TAG, "stress outline: ${outline.map { it.size }}")
        } finally { engine.close(); db.close() }
    }

    private companion object {
        const val TAG = "LargePdfTest"
        const val IMPORT_BUDGET_MS = 3_000L
        const val OPEN_BUDGET_MS = 2_000L
        const val OUTLINE_BUDGET_MS = 15_000L
        const val SEARCH_BUDGET_MS = 120_000L
    }
}
