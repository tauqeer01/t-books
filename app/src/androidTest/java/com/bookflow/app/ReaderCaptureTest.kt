package com.bookflow.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.notes.data.CaptureSource
import com.bookflow.app.notes.data.NoteCapture
import com.bookflow.app.notes.data.NoteFileStore
import com.bookflow.app.notes.data.NotesRepositoryImpl
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.pdf.engine.AndroidPdfRendererEngine
import com.bookflow.app.pdf.engine.PdfRect
import com.bookflow.app.pdf.engine.PdfSource
import com.bookflow.app.presentation.screens.notes.editor.NotePenPreferences
import com.bookflow.app.presentation.screens.reader.ReaderCaptureViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderCaptureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
    private val files = NoteFileStore(context)
    private val repository = NotesRepositoryImpl(db.notesDao(), files)
    private val capture = NoteCapture(files)

    @After fun tearDown() = db.close()

    @Test fun quoteBecomesEscapedClipWithAttributionAndSourcePage() {
        val element = capture.element("n", CaptureSource.Quote("  Lift <L>  & drag\n  ", "book-1", 41), "Flight & Theory")
        assertEquals(ElementKind.PDF_CLIP, element.kind)
        assertNull(element.imagePath)
        assertEquals("Lift <L> & drag", element.quoteText)
        assertTrue(element.html!!.contains("Lift &lt;L&gt; &amp; drag"))
        assertTrue(element.html!!.contains("— Flight &amp; Theory, p. 42"))
        assertEquals("book-1" to 41, element.sourceBookId to element.sourcePage)
        assertTrue(element.height in 0.06f..0.85f)
    }

    @Test fun clipIsSavedInTheNoteWithItsAspectRatio() {
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val element = capture.element("clip-note", CaptureSource.Clip(bitmap, "book-1", 2), "Book")
        assertTrue(File(element.imagePath!!).exists())
        // 2:1 image at 0.82 page width keeps its proportions on the A4 page
        assertEquals(0.82f * 1000f * 0.5f / 1414f, element.height, 1e-3f)
        files.deleteNote("clip-note")
    }

    @Test fun regionRenderIsSharpAndShowsOnlyThatRegion() = runBlocking {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "region.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(400f, 600f)); doc.addPage(page)
            // Black square in the top-left quarter only (PDF origin is bottom-left)
            PDPageContentStream(doc, page).use { it.addRect(0f, 300f, 200f, 300f); it.fill() }
            doc.save(file)
        }
        val engine = AndroidPdfRendererEngine(context)
        try {
            engine.openDocument(PdfSource.FileSource(file))
            val clip = requireNotNull(engine.renderRegion(0, PdfRect(0f, 0f, 0.5f, 0.5f), targetLongEdgePx = 1200))
            assertEquals(1200, maxOf(clip.width, clip.height))
            assertEquals(200f / 300f, clip.width.toFloat() / clip.height, 0.01f)
            assertTrue("region is the black square", Color.red(clip.getPixel(clip.width / 2, clip.height / 2)) < 50)
            val other = requireNotNull(engine.renderRegion(0, PdfRect(0.5f, 0.5f, 1f, 1f), targetLongEdgePx = 600))
            assertTrue("other quarter is blank", Color.red(other.getPixel(other.width / 2, other.height / 2)) > 200)
        } finally { engine.close(); file.delete() }
    }

    @Test fun captureToNewNoteAppendsAndUndoRemovesIt(): Unit = runBlocking(Dispatchers.Main) {
        val prefs = NotePenPreferences(context)
        // A real ViewModelStore so clearing it cancels the ViewModel's database collectors before the DB closes
        val store = androidx.lifecycle.ViewModelStore()
        val vm = androidx.lifecycle.ViewModelProvider(store, ReaderCaptureViewModel.Factory(repository, capture, prefs))[ReaderCaptureViewModel::class.java]
        try {
        vm.setBook("book-1", "Hydraulics")
        vm.capture(CaptureSource.Quote("Pascal's law", "book-1", 3))
        assertNotNull(vm.state.value.pending)
        vm.sendTo(null)
        val delivered = withTimeout(5_000) { vm.state.first { it.delivered != null }.delivered!! }
        assertEquals("Hydraulics", delivered.noteTitle)
        assertEquals(delivered.noteId, prefs.lastCaptureNoteId())
        val note = repository.loadNote(delivered.noteId)!!
        assertEquals("Pascal's law", note.pages.single().elements.single().quoteText)
        withTimeout(5_000) { vm.undoDelivered()!!.join() }
        assertTrue(repository.loadNote(delivered.noteId)!!.pages.single().elements.isEmpty())
        repository.deleteNote(delivered.noteId)
        } finally { store.clear() }
    }
}
