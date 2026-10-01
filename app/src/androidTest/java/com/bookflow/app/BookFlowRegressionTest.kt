package com.bookflow.app

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.data.local.entity.BookEntity
import com.bookflow.app.data.local.entity.CollectionEntity
import com.bookflow.app.data.preferences.DataStorePreferencesRepository
import com.bookflow.app.data.repository.BookRepositoryImpl
import com.bookflow.app.domain.model.*
import com.bookflow.app.pdf.engine.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BookFlowRegressionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture(rotations: List<Int> = List(12) { 0 }, blank: Boolean = false): File {
        PDFBoxResourceLoader.init(context)
        val file = File.createTempFile("bookflow_test_", ".pdf", context.cacheDir)
        PDDocument().use { doc ->
            rotations.forEachIndexed { index, rotation ->
                val page = PDPage(PDRectangle(400f, 600f)).apply { this.rotation = rotation }
                doc.addPage(page)
                if (!blank) PDPageContentStream(doc, page).use { content ->
                    content.beginText(); content.setFont(PDType1Font.HELVETICA, 18f)
                    content.newLineAtOffset(40f, 510f)
                    content.showText("Page ${index + 1}: amber quartz navigation")
                    content.endText()
                    if (index == 9) {
                        content.beginText(); content.setFont(PDType1Font.HELVETICA, 14f)
                        content.newLineAtOffset(40f, 450f); content.showText("Unique zephyr result with context."); content.endText()
                    }
                }
            }
            val outline = PDDocumentOutline()
            val item = PDOutlineItem().apply { title = "Real chapter"; setDestination(doc.getPage(rotations.lastIndex)) }
            outline.addLast(item)
            doc.documentCatalog.documentOutline = outline
            val destination = PDPageFitDestination().apply { page = doc.getPage(rotations.lastIndex) }
            doc.getPage(0).annotations.add(PDAnnotationLink().apply { rectangle = PDRectangle(40f, 400f, 120f, 30f); this.destination = destination })
            doc.documentInformation.title = "Regression fixture"
            doc.documentInformation.author = "BookFlow tests"
            doc.save(file)
        }
        return file
    }

    @Test fun searchesActualDocumentAndJumpsBeyondInitialPages() = runBlocking {
        val file = fixture()
        val engine = AndroidPdfRendererEngine(context)
        try {
            assertTrue(engine.openDocument(PdfSource.FileSource(file)) is PdfDocumentResult.Success)
            assertEquals(12, engine.getPageCount())
            val results = engine.search("ZEPHYR")
            assertEquals(1, results.size)
            assertEquals(9, results.single().pageIndex)
            assertEquals("zephyr", results.single().matchedText)
            assertTrue(results.single().snippet.contains("with context"))
            assertTrue(results.single().bounds.isNotEmpty())
            assertEquals(12, engine.search("amber quartz").size)
            assertTrue(engine.search("aircraft").isEmpty())
            assertTrue(engine.search("  ").isEmpty())
            assertEquals("Real chapter", engine.getTableOfContents().single().title)
            assertEquals(11, engine.getTableOfContents().single().pageIndex)
            assertEquals(11, engine.internalLinkAt(0, .2f, .3f))
            assertEquals("Regression fixture" to "BookFlow tests", engine.documentMetadata())
        } finally { engine.close(); file.delete() }
    }

    @Test fun textSelectionAndSearchBoundsFollowPageRotation() = runBlocking {
        val file = fixture(listOf(0, 90, 180, 270))
        val engine = AndroidPdfRendererEngine(context)
        try {
            engine.openDocument(PdfSource.FileSource(file))
            val results = engine.search("amber")
            assertEquals(4, results.size)
            results.forEach { result ->
                val rect = result.bounds[result.bounds.size / 2]
                assertTrue("Normalized bounds on page ${result.pageIndex}", rect.left >= 0 && rect.right <= 1 && rect.top >= 0 && rect.bottom <= 1 && rect.width > 0 && rect.height > 0)
                val selection = engine.selectTextAtPoint(result.pageIndex, (rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)
                assertEquals("amber", selection?.text)
                val bitmap = requireNotNull(engine.renderPage(result.pageIndex, 1f, RenderQuality.STANDARD))
                assertTrue("Text bounds overlap rendered ink on page ${result.pageIndex}", containsInk(bitmap, rect))
            }
        } finally { engine.close(); file.delete() }
    }

    private fun containsInk(bitmap: Bitmap, rect: PdfRect): Boolean {
        for (y in (rect.top * bitmap.height).toInt().coerceAtLeast(0) until (rect.bottom * bitmap.height).toInt().coerceAtMost(bitmap.height)) {
            for (x in (rect.left * bitmap.width).toInt().coerceAtLeast(0) until (rect.right * bitmap.width).toInt().coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) < 128) return true
            }
        }
        return false
    }

    @Test fun noTextDocumentHasNoInventedSearchOrSelection() = runBlocking {
        val file = fixture(listOf(0), blank = true)
        val engine = AndroidPdfRendererEngine(context)
        try {
            assertTrue(engine.openDocument(PdfSource.FileSource(file)) is PdfDocumentResult.Success)
            assertTrue(engine.search("aircraft").isEmpty())
            assertTrue(engine.extractText(0).isBlank())
            assertNull(engine.selectTextAtPoint(0, .5f, .5f))
            assertNotNull(engine.renderThumbnail(0))
        } finally { engine.close(); file.delete() }
    }

    @Test fun corruptDocumentFailsAndEngineCanReopen() = runBlocking {
        val bad = File.createTempFile("bookflow_bad_", ".pdf", context.cacheDir).apply { writeText("not a PDF") }
        val good = fixture(listOf(0))
        val engine = AndroidPdfRendererEngine(context)
        try {
            assertTrue(engine.openDocument(PdfSource.FileSource(bad)) is PdfDocumentResult.Error)
            assertTrue(engine.openDocument(PdfSource.FileSource(good)) is PdfDocumentResult.Success)
            assertEquals(1, engine.search("quartz").size)
        } finally { engine.close(); bad.delete(); good.delete() }
    }

    @Test fun globalAndPerBookPreferencesPersistAndResetIndependently() = runBlocking {
        val repository = DataStorePreferencesRepository(context)
        val original = repository.preferencesFlow.first()
        val bookId = "regression_test_book"
        try {
            val global = original.copy(scrollMode = PageScrollMode.SINGLE_PAGE, pageSpacing = 23, brightness = .35f,
                volumeButtonNavigation = true, orientation = ReadingOrientation.LANDSCAPE, keepScreenOn = false, nightTreatment = true)
            repository.savePreferences(global)
            assertEquals(global, DataStorePreferencesRepository(context).preferencesFlow.first())
            assertEquals(global, repository.preferencesForBook(bookId).first())
            val book = global.copy(pageSpacing = 3, scrollMode = PageScrollMode.CONTINUOUS_VERTICAL)
            repository.savePreferences(book, bookId)
            assertEquals(book, repository.preferencesForBook(bookId).first())
            assertEquals(global, repository.preferencesFlow.first())
            repository.clearBookPreferences(bookId)
            assertEquals(global, repository.preferencesForBook(bookId).first())
        } finally { repository.clearBookPreferences(bookId); repository.savePreferences(original) }
    }

    @Test fun contentUriImportDeduplicatesAndRemainsReadable() = runBlocking(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
        val file = fixture(listOf(0, 0))
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val engine = AndroidPdfRendererEngine(context)
        try {
            val repository = BookRepositoryImpl(context, db.bookDao(), db.collectionDao(), db.annotationDao(), db.bookmarkDao(), PdfEngineFactory(context))
            val imported = repository.importPdf(uri).getOrThrow()
            assertEquals("Regression fixture", imported.title)
            assertEquals("BookFlow tests", imported.author)
            assertEquals(2, imported.pageCount)
            assertEquals("", imported.filePath)
            assertEquals(uri.toString(), imported.uriString)
            assertTrue(File(requireNotNull(imported.thumbnailPath)).exists())
            val duplicate = repository.importMultiplePdfs(listOf(uri))
            assertEquals(0, duplicate.importedCount)
            assertEquals(1, duplicate.duplicateCount)
            assertTrue(engine.openDocument(PdfSource.UriSource(uri, context)) is PdfDocumentResult.Success)
            assertEquals(2, engine.search("quartz").size)
            repository.deleteBookFromLibrary(imported.id)
            assertTrue(file.exists())
            assertFalse(File(imported.thumbnailPath!!).exists())
        } finally { engine.close(); db.close(); file.delete() }
    }

    @Test fun manyToManyCollectionsAndLibraryRemovalPreserveOriginal() = runBlocking(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
        val file = fixture(listOf(0))
        try {
            val repository = BookRepositoryImpl(context, db.bookDao(), db.collectionDao(), db.annotationDao(), db.bookmarkDao(), PdfEngineFactory(context))
            val book = Book("test_book", "Fixture", "Author", file.absolutePath, pageCount = 1)
            repository.insertOrUpdateBook(book)
            db.collectionDao().insertAll(listOf(CollectionEntity("a", "A", "", "#FFFFFF", "folder", 1), CollectionEntity("b", "B", "", "#FFFFFF", "folder", 1)))
            repository.setBookCollections(book.id, listOf("a", "b"))
            assertEquals(setOf("a", "b"), repository.getAllBooks().first().single().collectionIds.toSet())
            assertEquals(1, repository.getBooksByCollection("b").first().size)
            repository.setBookCollections(book.id, listOf("b"))
            assertTrue(repository.getBooksByCollection("a").first().isEmpty())
            repository.toggleFavorite(book.id, true)
            assertEquals(1, repository.getFavoriteBooks().first().size)
            repository.updateReadingProgress(book.id, 0, 1)
            assertEquals(1f, repository.getBookById(book.id).first()!!.readingProgress)
            db.bookmarkDao().insertBookmark(com.bookflow.app.data.local.entity.BookmarkEntity("bm", book.id, 0, "Saved", 1))
            repository.deleteBookFromLibrary(book.id)
            assertTrue(file.exists())
            assertTrue(repository.getAllBooks().first().isEmpty())
            assertTrue(db.bookmarkDao().getBookmarksForBook(book.id).first().isEmpty())
            assertTrue(db.bookDao().getAllCrossRefs().first().isEmpty())
        } finally { db.close(); file.delete() }
    }
}
