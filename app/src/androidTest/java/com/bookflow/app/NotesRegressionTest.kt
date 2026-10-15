package com.bookflow.app

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.notes.data.NoteFileStore
import com.bookflow.app.notes.data.NotesRepositoryImpl
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.InkSerializer
import com.bookflow.app.notes.ink.InkStroke
import com.bookflow.app.notes.ink.PenType
import com.bookflow.app.presentation.screens.notes.editor.EditorPage
import com.bookflow.app.presentation.screens.notes.export.NoteExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotesRegressionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
    private val files = NoteFileStore(context)
    private val repository = NotesRepositoryImpl(db.notesDao(), files)

    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BookFlowDatabase::class.java)

    @After fun tearDown() = db.close()

    private fun text(id: String, html: String) = NoteElement(id, ElementKind.TEXT, 0.1f, 0.1f, 0.5f, 0.05f, html = html)

    @Test fun addingAndReorderingPagesKeepsEveryPagesElements(): Unit = runBlocking(Dispatchers.IO) {
        val noteId = repository.createNote(title = "Physics")
        val first = repository.loadNote(noteId)!!.pages.single()
        repository.saveElements(noteId, first.id, listOf(text("t1", "<p>Bernoulli</p>")))
        val second = repository.addPage(noteId, 0, PageBackground.GRID)
        repository.saveElements(noteId, second.id, listOf(text("t2", "<p>Second</p>")))
        repository.reorderPages(noteId, listOf(first.id, second.id))
        repository.addPage(noteId, 2, PageBackground.BLANK)

        val doc = repository.loadNote(noteId)!!
        assertEquals(listOf(first.id, second.id), doc.pages.take(2).map { it.id })
        assertEquals(listOf("t1"), doc.pages[0].elements.map { it.id })
        assertEquals(listOf("t2"), doc.pages[1].elements.map { it.id })
        assertEquals(3, doc.pages.size)
        repository.deleteNote(noteId)
    }

    @Test fun captureAppendsBelowContentAndOverflowsToANewPage(): Unit = runBlocking(Dispatchers.IO) {
        val noteId = repository.createNote()
        val clip = NoteElement("q", ElementKind.PDF_CLIP, 0f, 0f, 0.8f, 0.5f, html = "<p>quote</p>", quoteText = "quote", sourceBookId = "b", sourcePage = 4)
        val (page1, placed1) = repository.appendElement(noteId, clip)
        val (page2, placed2) = repository.appendElement(noteId, clip.copy(id = "q2"))
        assertNotEquals("Second half-page clip does not fit below the first", page1, page2)
        assertEquals(placed1.y, placed2.y, 1e-6f)
        val doc = repository.loadNote(noteId)!!
        assertEquals(2, doc.pages.size)
        assertEquals(4, doc.pages[0].elements.single().sourcePage)
        repository.deleteNote(noteId)
    }

    @Test fun searchFindsTitlesTextTagsAndQuotesAndForgetsDeletedNotes(): Unit = runBlocking(Dispatchers.IO) {
        val noteId = repository.createNote(title = "Aerodynamics")
        val page = repository.loadNote(noteId)!!.pages.single()
        repository.saveElements(noteId, page.id, listOf(text("t", "<p>The <b>stall</b> angle</p>"),
            NoteElement("c", ElementKind.PDF_CLIP, 0f, 0.3f, 0.5f, 0.2f, imagePath = null, quoteText = "turbulent boundary layer")))
        repository.setTags(noteId, listOf("exam", "chapter3"))
        assertEquals(noteId, repository.search("aero").first().single().id)
        assertEquals(noteId, repository.search("stall").first().single().id)
        assertEquals(noteId, repository.search("turbul").first().single().id)
        assertEquals(noteId, repository.search("chapter3").first().single().id)
        assertTrue(repository.search("hydraulics").first().isEmpty())
        repository.deleteNote(noteId)
        assertTrue(repository.search("stall").first().isEmpty())
        assertTrue(db.notesDao().pages(noteId).isEmpty())
        assertFalse(File(context.filesDir, "notes/$noteId").exists())
    }

    @Test fun deletingAFolderMovesItsNotesAndChildrenUp(): Unit = runBlocking(Dispatchers.IO) {
        val parent = repository.createFolder("Year 1", "#000000")
        val child = repository.createFolder("Physics", "#000000", parent)
        val noteId = repository.createNote(folderId = child)
        repository.deleteFolder(child)
        assertEquals(parent, repository.notes().first().first { it.id == noteId }.folderId)
        repository.deleteFolder(parent)
        assertNull(repository.notes().first().first { it.id == noteId }.folderId)
        repository.deleteNote(noteId)
    }

    @Test fun inkRoundTripsThroughThePageFile() {
        val batch = MutableStrokeInputBatch()
        repeat(20) { i -> batch.add(InputToolType.STYLUS, 10f + i * 5, 20f + i, i * 8L, pressure = 0.2f + i * 0.03f, tiltRadians = 0.3f) }
        val strokes = listOf(
            InkStroke.from(Stroke(InkBrushes.brush(PenType.FOUNTAIN, 0xFF2563EB.toInt(), 6f), batch), PenType.FOUNTAIN),
            InkStroke.from(Stroke(InkBrushes.brush(PenType.HIGHLIGHTER, 0xFFFDE047.toInt(), 28f), batch), PenType.HIGHLIGHTER)
        )
        val file = File(context.cacheDir, "roundtrip.ink")
        InkSerializer.write(file, strokes)
        val read = InkSerializer.read(file)
        assertEquals(strokes.map { it.id }, read.map { it.id })
        assertEquals(strokes.map { it.pen }, read.map { it.pen })
        assertEquals(strokes[0].colorArgb, read[0].colorArgb)
        assertEquals(20, read[0].stroke.inputs.size)
        // Ink's encoding quantizes coordinates (~0.01 page units), far below anything visible
        strokes[0].points.zip(read[0].points).forEach { (a, b) -> assertEquals(a.x, b.x, 0.05f); assertEquals(a.y, b.y, 0.05f) }
        assertTrue(read[0].stroke.inputs.hasPressure())
        // Moving a stroke keeps pressure and timing
        val moved = read[0].transformed { x, y -> (x + 100f) to y }
        assertEquals(read[0].points.first().x + 100f, moved.points.first().x, 1e-3f)
        assertTrue(moved.stroke.inputs.hasPressure())
        file.delete()
    }

    @Test fun exportedPdfHasOnePagePerNotePageWithInkAtPageScale(): Unit = runBlocking {
        // A thick black stroke across the middle of the page, in page units (page is 1000 wide)
        val batch = MutableStrokeInputBatch()
        repeat(30) { i -> batch.add(InputToolType.STYLUS, 300f + i * 10f, 700f, i * 8L) }
        val ink = InkStroke.from(Stroke(InkBrushes.brush(PenType.BALLPOINT, 0xFF000000.toInt(), 16f), batch), PenType.BALLPOINT)
        val pages = List(3) { EditorPage("p$it", PageBackground.BLANK, listOf(text("t$it", "<p>Page $it</p>")), if (it == 0) listOf(ink) else emptyList()) }
        val uri = NoteExporter.exportPdf(context, "Export test", pages)
        val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
        android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
            assertEquals(3, renderer.pageCount)
            renderer.openPage(0).use { page ->
                val bitmap = android.graphics.Bitmap.createBitmap(1000, (1000f * page.height / page.width).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                fun dark(x: Int, y: Int) = android.graphics.Color.red(bitmap.getPixel(x, y)) < 100
                assertTrue("stroke middle is inked", dark(450, 700))
                assertFalse("ink is not drawn unscaled beyond the stroke", dark(700, 700))
                assertFalse("nothing far from the stroke", dark(450, 1100))
            }
        }
    }

    @Test fun importedPhotosAreDownscaledWebpInsideTheNote() {
        val big = android.graphics.Bitmap.createBitmap(4000, 3000, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val source = File(context.cacheDir, "photo.png").apply { outputStream().use { big.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        val image = files.importImage("photo-note", android.net.Uri.fromFile(source))
        assertTrue(maxOf(image.width, image.height) <= 2048)
        assertEquals(4f / 3f, image.width.toFloat() / image.height, 0.01f)
        assertTrue(image.path.startsWith(File(context.filesDir, "notes/photo-note").path) && image.path.endsWith(".webp"))
        files.deleteNote("photo-note"); source.delete()
    }

    @Test fun migrationFrom3To4KeepsBooksAndAddsNotes() {
        migrations.createDatabase(MIGRATION_DB, 3).use { db ->
            db.execSQL("INSERT INTO books (id, title, author, filePath, uriString, fileSizeBytes, pageCount, currentPage, readingProgress, isFavorite, category, collectionId, thumbnailPath, coverColorHex, lastReadTimestamp, addedTimestamp) " +
                "VALUES ('b1', 'Kept', 'A', '', NULL, 1, 10, 2, 0.3, 0, 'PDF', NULL, NULL, '#000000', 0, 0)")
        }
        migrations.runMigrationsAndValidate(MIGRATION_DB, 4, true).use { db ->
            db.query("SELECT title FROM books WHERE id = 'b1'").use { c -> assertTrue(c.moveToFirst()); assertEquals("Kept", c.getString(0)) }
            db.query("SELECT COUNT(*) FROM notes").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
        }
    }

    private companion object { const val MIGRATION_DB = "migration-test" }
}
