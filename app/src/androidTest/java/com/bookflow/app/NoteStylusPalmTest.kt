package com.bookflow.app

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.notes.data.NoteFileStore
import com.bookflow.app.notes.data.NotesRepositoryImpl
import com.bookflow.app.presentation.screens.notes.editor.NoteEditorScreen
import com.bookflow.app.presentation.screens.notes.editor.NoteEditorViewModel
import com.bookflow.app.presentation.screens.notes.editor.NotePenPreferences
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A hand resting on a tablet while writing reaches the editor as a finger pointer beside the stylus.
 * The stylus must still ink, and its strokes must stay on the page after it lifts.
 */
class NoteStylusPalmTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
    private val files = NoteFileStore(context)
    private val repository = NotesRepositoryImpl(db.notesDao(), files)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = androidx.lifecycle.ViewModelStore()
    private lateinit var vm: NoteEditorViewModel

    @Before fun openEditor() = runBlocking {
        val noteId = repository.createNote()
        vm = withContext(Dispatchers.Main) {
            NoteEditorViewModel(noteId, repository, files, NotePenPreferences(context), appScope, context).also { store.put(noteId, it) }
        }
        withTimeout(5_000) { vm.state.first { !it.isLoading } }
        compose.setContent { NoteEditorScreen(vm, onBack = {}, onOpenSource = { _, _ -> }) }
        compose.waitForIdle()
    }

    @After fun tearDown() {
        runBlocking(Dispatchers.Main) { store.clear() }
        runBlocking { delay(300) }
        db.close()
    }

    private fun strokeCount() = vm.state.value.pages.first().strokes.size

    private fun stylusStrokeCount() = vm.state.value.pages.first().strokes.count { s ->
        val input = androidx.ink.strokes.StrokeInput()
        s.stroke.inputs.populate(0, input)
        input.toolType == androidx.ink.brush.InputToolType.STYLUS
    }

    /** Screen point at the given fraction of the editor, on the first page. */
    private fun at(fx: Float, fy: Float): Pair<Float, Float> {
        val node = compose.onRoot().fetchSemanticsNode()
        return (node.positionOnScreen.x + node.size.width * fx) to (node.positionOnScreen.y + node.size.height * fy)
    }

    private class Pointer(val id: Int, val toolType: Int, var x: Float, var y: Float)

    private var downTime = 0L

    private fun inject(action: Int, pointers: List<Pointer>) {
        val props = pointers.map { p -> MotionEvent.PointerProperties().apply { id = p.id; toolType = p.toolType } }.toTypedArray()
        val coords = pointers.map { p -> MotionEvent.PointerCoords().apply { x = p.x; y = p.y; pressure = 1f; size = 1f } }.toTypedArray()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, pointers.size, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS, 0)
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    private fun pointerAction(action: Int, index: Int) = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun palmDown(): Pointer {
        val (x, y) = at(0.8f, 0.45f)
        val palm = Pointer(0, MotionEvent.TOOL_TYPE_FINGER, x, y)
        downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, listOf(palm))
        return palm
    }

    /** One stylus stroke from left to right at height [fy], drawn while [palm] rests on the screen. */
    private fun stylusStrokeBesidePalm(palm: Pointer, fy: Float) {
        val (x0, y) = at(0.2f, fy)
        val (x1, _) = at(0.6f, fy)
        val pen = Pointer(1, MotionEvent.TOOL_TYPE_STYLUS, x0, y)
        val both = listOf(palm, pen)
        inject(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), both)
        for (i in 1..20) {
            pen.x = x0 + (x1 - x0) * i / 20
            palm.y += 0.5f // a resting hand still drifts a little
            SystemClock.sleep(8)
            inject(MotionEvent.ACTION_MOVE, both)
        }
        inject(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), both)
        compose.waitForIdle()
    }

    @Test fun loneStylusStrokeIsKept() {
        val (x0, y) = at(0.2f, 0.25f)
        val (x1, _) = at(0.6f, 0.25f)
        val pen = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, x0, y)
        downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, listOf(pen))
        for (i in 1..20) { pen.x = x0 + (x1 - x0) * i / 20; SystemClock.sleep(8); inject(MotionEvent.ACTION_MOVE, listOf(pen)) }
        inject(MotionEvent.ACTION_UP, listOf(pen))
        compose.waitForIdle()
        assertEquals(1, strokeCount())
    }

    @Test fun stylusInksWhileAPalmIsAlreadyDown() {
        val palm = palmDown()
        stylusStrokeBesidePalm(palm, 0.25f)
        inject(MotionEvent.ACTION_UP, listOf(palm))
        compose.waitForIdle()
        assertEquals("the stylus stroke is kept", 1, stylusStrokeCount())
        assertEquals("the palm leaves no ink", 1, strokeCount())
    }

    @Test fun everyStylusStrokeStaysWhileThePalmRestsBetweenThem() {
        val palm = palmDown()
        stylusStrokeBesidePalm(palm, 0.20f)
        stylusStrokeBesidePalm(palm, 0.28f)
        stylusStrokeBesidePalm(palm, 0.36f)
        inject(MotionEvent.ACTION_UP, listOf(palm))
        compose.waitForIdle()
        assertEquals(3, stylusStrokeCount())
        assertEquals(3, strokeCount())
    }
}
