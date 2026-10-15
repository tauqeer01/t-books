package com.bookflow.app

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.notes.data.NoteFileStore
import com.bookflow.app.notes.data.NotesRepositoryImpl
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.InkGeometry.Point
import com.bookflow.app.notes.ink.PenType
import com.bookflow.app.presentation.screens.notes.editor.EditorTool
import com.bookflow.app.presentation.screens.notes.editor.EraserMode
import com.bookflow.app.presentation.screens.notes.editor.NoteClipboard
import com.bookflow.app.presentation.screens.notes.editor.NoteEditorViewModel
import com.bookflow.app.presentation.screens.notes.editor.NotePenPreferences
import com.bookflow.app.presentation.screens.notes.export.NotePageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The editor tools added after the first Notes release: tape, clipboard, highlighter eraser, and auto shapes. */
@RunWith(AndroidJUnit4::class)
class NoteToolsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
    private val files = NoteFileStore(context)
    private val repository = NotesRepositoryImpl(db.notesDao(), files)
    private val pens = NotePenPreferences(context)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // A real ViewModelStore so clearing it cancels each ViewModel before the database closes
    private val store = androidx.lifecycle.ViewModelStore()

    @After fun tearDown() {
        runBlocking(Dispatchers.Main) { store.clear() }
        runBlocking { delay(300) } // final saves run in the app scope
        db.close()
    }

    private suspend fun editor(noteId: String): NoteEditorViewModel = withContext(Dispatchers.Main) {
        val vm = NoteEditorViewModel(noteId, repository, files, pens, appScope, context)
        store.put(noteId, vm)
        withTimeout(5_000) { vm.state.first { !it.isLoading } }
        vm
    }

    private fun stroke(pen: PenType, vararg points: Point): Stroke {
        // Points every few units, like a real pen, so the eraser (which tests points) can hit the middle
        val dense = points.toList().zipWithNext().flatMap { (a, b) -> List(40) { i -> Point(a.x + (b.x - a.x) * i / 40, a.y + (b.y - a.y) * i / 40) } } + points.last()
        val batch = MutableStrokeInputBatch()
        dense.forEachIndexed { i, p -> batch.add(InputToolType.STYLUS, p.x, p.y, i * 10L) }
        return Stroke(InkBrushes.brush(pen, 0xFF111827.toInt(), 6f), batch)
    }

    @Test fun thinTapeDragMakesALineTallStripThatTogglesAndIsSaved(): Unit = runBlocking {
        val noteId = repository.createNote()
        val vm = editor(noteId)
        val page = vm.state.value.pages.single().id
        withContext(Dispatchers.Main) {
            vm.setTapeColor(0xFF60A5FA.toInt())
            vm.addTape(page, Point(100f, 300f), Point(600f, 305f))
        }
        val tape = vm.state.value.page(page)!!.elements.single()
        assertEquals(ElementKind.TAPE, tape.kind)
        assertEquals(EditorTool.TAPE, vm.state.value.tool)
        assertEquals("one ruled line tall", 40f, tape.height * PAGE_HEIGHT, 0.5f)
        assertEquals(0.1f, tape.x, 1e-4f)
        assertEquals(0xFF60A5FA.toInt(), tape.fillColor)

        withContext(Dispatchers.Main) { vm.toggleTape(tape.id) }
        assertTrue(tape.id in vm.state.value.revealedTapes)
        withContext(Dispatchers.Main) { vm.revealAllTapes(false) }
        assertTrue(vm.state.value.revealedTapes.isEmpty())

        withContext(Dispatchers.Main) { vm.saveNow() }
        withTimeout(5_000) {
            while (repository.loadNote(noteId)!!.pages.single().elements.none { it.kind == ElementKind.TAPE }) delay(50)
        }
    }

    @Test fun exportedPageKeepsTapeOverTheInk() {
        val ink = com.bookflow.app.notes.ink.InkStroke.from(stroke(PenType.MARKER, Point(100f, 400f), Point(900f, 400f)), PenType.MARKER)
        val tape = NoteElement("t", ElementKind.TAPE, 0.05f, 380f / PAGE_HEIGHT, 0.9f, 40f / PAGE_HEIGHT, fillColor = 0xFFF59E0B.toInt())
        val page = com.bookflow.app.presentation.screens.notes.editor.EditorPage("p", com.bookflow.app.notes.domain.PageBackground.BLANK, listOf(tape), listOf(ink))
        val bitmap = NotePageRenderer.renderBitmap(context, page, widthPx = 1000)
        val pixel = bitmap.getPixel(500, 400)
        assertTrue("tape covers the ink: ${Integer.toHexString(pixel)}", android.graphics.Color.red(pixel) > 200 && android.graphics.Color.blue(pixel) < 100)
    }

    @Test fun copiedSelectionPastesIntoAnotherNoteAtTheGivenSpot(): Unit = runBlocking {
        val source = repository.createNote()
        val target = repository.createNote()
        val vm = editor(source)
        val page = vm.state.value.pages.single().id
        val sticky = NoteElement("s", ElementKind.TEXT, 0.5f, 0.5f, 0.2f, 0.1f, html = "<p>hi</p>", fillColor = 0xFFFEF08A.toInt())
        withContext(Dispatchers.Main) {
            vm.addStrokes(page, listOf(stroke(PenType.BALLPOINT, Point(500f, 700f), Point(600f, 760f))))
            vm.addStickyNote(page, 0.5f, 0.5f, sticky.fillColor!!)
            vm.stopEditing()
            vm.selectTool(EditorTool.LASSO)
            vm.lassoSelect(page, listOf(Point(0f, 0f), Point(PAGE_WIDTH, 0f), Point(PAGE_WIDTH, PAGE_HEIGHT), Point(0f, PAGE_HEIGHT)))
        }
        val selection = vm.state.value.selection!!
        assertEquals(1, selection.strokeIds.size)
        withContext(Dispatchers.Main) { vm.copySelection() }
        assertTrue(vm.state.value.clipboardAvailable)

        val other = editor(target)
        val targetPage = other.state.value.pages.single().id
        withContext(Dispatchers.Main) { other.paste(targetPage, 0.1f, 0.2f) }
        withTimeout(5_000) { other.state.first { it.page(targetPage)!!.strokes.isNotEmpty() } }
        val pasted = other.state.value.page(targetPage)!!
        assertEquals(1, pasted.strokes.size)
        assertEquals("sticky note pasted with its color", 0xFFFEF08A.toInt(), pasted.elements.single().fillColor)
        val left = minOf(pasted.strokes.single().bounds!!.left, pasted.elements.single().x * PAGE_WIDTH)
        assertEquals("top-left lands where asked", 100f, left, 1f)
        assertEquals("pasted items are selected for moving", pasted.strokes.single().id, other.state.value.selection!!.strokeIds.single())
        assertFalse("source keeps its own copy", vm.state.value.page(page)!!.strokes.isEmpty())
        NoteClipboard.put(emptyList(), emptyList())
    }

    @Test fun highlighterEraserLeavesHandwritingAlone(): Unit = runBlocking {
        val vm = editor(repository.createNote())
        val page = vm.state.value.pages.single().id
        withContext(Dispatchers.Main) {
            vm.addStrokes(page, listOf(stroke(PenType.BALLPOINT, Point(100f, 500f), Point(800f, 500f))))
            vm.selectHighlighter()
            vm.addStrokes(page, listOf(stroke(PenType.HIGHLIGHTER, Point(100f, 505f), Point(800f, 505f))))
            vm.setEraserMode(EraserMode.HIGHLIGHTER)
            vm.erase(page, Point(450f, 400f), Point(450f, 600f))
            vm.endErase()
        }
        val left = vm.state.value.page(page)!!.strokes
        // Strokes take the active pen's type; whatever the handwriting pen was, only the highlight goes
        assertEquals(1, left.size)
        assertTrue(left.none { it.pen == PenType.HIGHLIGHTER })
        withContext(Dispatchers.Main) { vm.undo() }
        assertEquals("one undo step restores the highlight", 2, vm.state.value.page(page)!!.strokes.size)
    }

    @Test fun autoShapeCleansUpACircleButKeepsAScribble(): Unit = runBlocking {
        val vm = editor(repository.createNote())
        val page = vm.state.value.pages.single().id
        val rough = List(60) { i -> val t = 2 * PI * i / 57; Point(500 + 200 * cos(t).toFloat() + (i % 3) * 3f, 600 + 120 * sin(t).toFloat() - (i % 2) * 4f) }
        val scribble = List(20) { i -> Point(100f + i * 20, if (i % 2 == 0) 1000f else 1100f) }
        withContext(Dispatchers.Main) {
            vm.selectAutoShape()
            vm.addRecognizedShape(page, rough)
            vm.addRecognizedShape(page, scribble)
        }
        val (circle, kept) = vm.state.value.page(page)!!.strokes
        val b = circle.bounds!!
        assertEquals("ellipse spans the drawing", 400f, b.width - circle.size, 12f)
        assertTrue("clean outline has many evenly spaced points", circle.points.size > rough.size)
        assertEquals("unrecognized drawing stays as drawn", scribble, kept.points)
        clear(vm)
    }

    private suspend fun clear(vm: NoteEditorViewModel) = withContext(Dispatchers.Main) { vm.clearPageInk(vm.state.value.pages.single().id) }
}
