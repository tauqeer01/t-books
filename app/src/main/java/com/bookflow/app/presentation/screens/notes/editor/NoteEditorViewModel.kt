package com.bookflow.app.presentation.screens.notes.editor

import android.content.Context
import android.net.Uri
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.notes.data.NoteFileStore
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.NotesRepository
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.InkGeometry
import com.bookflow.app.notes.ink.InkSerializer
import com.bookflow.app.notes.ink.InkStroke
import com.bookflow.app.notes.ink.PenPreset
import com.bookflow.app.notes.ink.PenType
import com.bookflow.app.presentation.screens.notes.export.NotePageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class NoteEditorViewModel(
    private val noteId: String,
    private val repository: NotesRepository,
    private val files: NoteFileStore,
    private val pens: NotePenPreferences,
    private val appScope: CoroutineScope,
    private val appContext: Context
) : ViewModel() {
    private val _state = MutableStateFlow(NoteEditorUiState())
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()

    private val history = EditorHistory()
    private val dirtyPages = mutableSetOf<String>()
    private val saveLock = Mutex()
    private var saveJob: Job? = null

    init {
        _state.update { it.copy(presets = pens.presets(), activePreset = pens.activeIndex(), fingerDrawing = pens.fingerDrawing()) }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val doc = repository.loadNote(noteId)
        if (doc == null) { _state.update { it.copy(isLoading = false, notFound = true) }; return }
        val pages = withContext(Dispatchers.IO) {
            doc.pages.map { page ->
                val ink = runCatching { InkSerializer.read(files.inkFile(noteId, page.id)) }
                EditorPage(page.id, page.background, page.elements, ink.getOrDefault(emptyList()), inkUnavailable = ink.isFailure)
            }
        }
        _state.update { it.copy(isLoading = false, title = doc.summary.title, pages = pages,
            message = if (pages.any { p -> p.inkUnavailable }) "Some handwriting could not be loaded" else null) }
    }

    // --- Tools -------------------------------------------------------------------------------------------

    fun selectTool(tool: EditorTool) = _state.update {
        it.copy(tool = tool, selection = if (tool == EditorTool.LASSO) it.selection else null, editingElementId = null)
    }

    fun selectPreset(index: Int) { pens.setActive(index); _state.update { it.copy(activePreset = index, tool = EditorTool.PEN) } }

    fun updateActivePreset(transform: (PenPreset) -> PenPreset) = _state.update { s ->
        val presets = s.presets.toMutableList()
        presets.getOrNull(s.activePreset)?.let { presets[s.activePreset] = transform(it) }
        pens.save(presets)
        s.copy(presets = presets)
    }

    fun addPreset() = _state.update { s ->
        if (s.presets.size >= MAX_PRESETS) return@update s.copy(message = "Up to $MAX_PRESETS pens")
        val presets = s.presets + (s.activePen ?: InkBrushes.defaultPresets.first())
        pens.save(presets); pens.setActive(presets.lastIndex)
        s.copy(presets = presets, activePreset = presets.lastIndex)
    }

    fun removeActivePreset() = _state.update { s ->
        if (s.presets.size <= 1) return@update s
        val presets = s.presets.toMutableList().apply { removeAt(s.activePreset) }
        val active = s.activePreset.coerceAtMost(presets.lastIndex)
        pens.save(presets); pens.setActive(active)
        s.copy(presets = presets, activePreset = active)
    }

    fun setEraserMode(mode: EraserMode) = _state.update { it.copy(eraserMode = mode, tool = EditorTool.ERASER) }
    fun setEraserSize(size: Float) = _state.update { it.copy(eraserSize = size) }
    fun setFingerDrawing(enabled: Boolean) { pens.setFingerDrawing(enabled); _state.update { it.copy(fingerDrawing = enabled, stylusSeen = if (enabled) false else it.stylusSeen) } }
    fun onStylusSeen() { if (!_state.value.stylusSeen) _state.update { it.copy(stylusSeen = true) } }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    // --- Ink ---------------------------------------------------------------------------------------------

    /** Finished strokes arrive in page coordinates for [pageId]. */
    fun addStrokes(pageId: String, strokes: List<Stroke>) {
        val pen = _state.value.activePen?.type ?: PenType.BALLPOINT
        editPage(pageId) { page -> page.copy(strokes = page.strokes + strokes.map { InkStroke.from(it, pen) }) }
    }

    private var eraseGesture: Pair<String, EditorPage>? = null

    /** Erases along the eraser path segment [a]→[b] (page units). Consecutive calls in one gesture form one undo step. */
    fun erase(pageId: String, a: InkGeometry.Point, b: InkGeometry.Point) {
        val page = _state.value.page(pageId) ?: return
        val radius = _state.value.eraserSize / 2
        val updated = when (_state.value.eraserMode) {
            EraserMode.STROKE -> page.strokes.filterNot { s -> near(s, a, b, radius) && InkGeometry.touches(s.points, a, b, radius + s.size / 2) }
            EraserMode.PARTIAL -> page.strokes.flatMap { s ->
                val runs = if (near(s, a, b, radius)) InkGeometry.splitRuns(s.points, a, b, radius + s.size / 2) else null
                if (runs == null) listOf(s) else s.slices(runs)
            }
        }
        if (updated.size == page.strokes.size && updated.zip(page.strokes).all { (x, y) -> x.id == y.id }) return
        if (eraseGesture?.first != pageId) eraseGesture = pageId to page
        replacePage(page.copy(strokes = updated), recordFrom = null)
    }

    /** Cheap bounding-box reject before the per-vertex test. */
    private fun near(stroke: InkStroke, a: InkGeometry.Point, b: InkGeometry.Point, radius: Float): Boolean {
        val box = stroke.bounds ?: return false
        return maxOf(a.x, b.x) + radius >= box.left && minOf(a.x, b.x) - radius <= box.right &&
            maxOf(a.y, b.y) + radius >= box.top && minOf(a.y, b.y) - radius <= box.bottom
    }

    fun endErase() {
        val (pageId, before) = eraseGesture ?: return
        eraseGesture = null
        _state.value.page(pageId)?.let { after -> history.record(before, after); markDirty(pageId); refreshHistory() }
    }

    // --- Lasso & selection -------------------------------------------------------------------------------

    fun lassoSelect(pageId: String, polygon: List<InkGeometry.Point>) {
        val page = _state.value.page(pageId) ?: return
        val strokes = page.strokes.filter { InkGeometry.lassoed(polygon, it.points) }.map { it.id }.toSet()
        val elements = page.elements.filter { InkGeometry.contains(polygon, InkGeometry.Point((it.x + it.width / 2) * PAGE_WIDTH, (it.y + it.height / 2) * PAGE_HEIGHT)) }
            .map { it.id }.toSet()
        val selection = EditorSelection(pageId, strokes, elements)
        _state.update { it.copy(selection = selection.takeUnless { s -> s.isEmpty }) }
    }

    fun selectElement(pageId: String, elementId: String) =
        _state.update { it.copy(selection = EditorSelection(pageId, emptySet(), setOf(elementId)), editingElementId = null) }

    fun clearSelection() = _state.update { it.copy(selection = null) }

    private var transformStart: EditorPage? = null

    /** Live move/scale of the selection; [commitTransform] records one undo step for the whole drag. */
    fun transformSelection(dx: Float, dy: Float, scale: Float, pivotX: Float, pivotY: Float) {
        val selection = _state.value.selection ?: return
        val page = _state.value.page(selection.pageId) ?: return
        if (transformStart == null) transformStart = page
        val map = { x: Float, y: Float -> (pivotX + (x - pivotX) * scale + dx) to (pivotY + (y - pivotY) * scale + dy) }
        val strokes = page.strokes.map { if (it.id in selection.strokeIds) it.transformed(map) else it }
        val elements = page.elements.map { e ->
            if (e.id !in selection.elementIds) e else {
                val (x, y) = map(e.x * PAGE_WIDTH, e.y * PAGE_HEIGHT)
                e.copy(x = x / PAGE_WIDTH, y = y / PAGE_HEIGHT, width = (e.width * scale).coerceIn(0.03f, 1.5f), height = (e.height * scale).coerceIn(0.02f, 1.5f))
            }
        }
        replacePage(page.copy(strokes = strokes, elements = elements), recordFrom = null)
    }

    fun commitTransform() {
        val before = transformStart ?: return
        transformStart = null
        _state.value.page(before.id)?.let { after -> history.record(before, after); markDirty(before.id); refreshHistory() }
    }

    fun deleteSelection() {
        val selection = _state.value.selection ?: return
        editPage(selection.pageId) { page ->
            page.copy(strokes = page.strokes.filterNot { it.id in selection.strokeIds }, elements = page.elements.filterNot { it.id in selection.elementIds })
        }
        _state.update { it.copy(selection = null) }
    }

    fun recolorSelection(colorArgb: Int) {
        val selection = _state.value.selection ?: return
        editPage(selection.pageId) { page -> page.copy(strokes = page.strokes.map { if (it.id in selection.strokeIds) it.recolored(colorArgb) else it }) }
    }

    fun duplicateSelection() {
        val selection = _state.value.selection ?: return
        val offset = 24f
        var newSelection: EditorSelection? = null
        editPage(selection.pageId) { page ->
            val strokes = page.strokes.filter { it.id in selection.strokeIds }.map { it.transformed { x, y -> (x + offset) to (y + offset) }.copy(id = UUID.randomUUID().toString()) }
            val elements = page.elements.filter { it.id in selection.elementIds }.map { e ->
                e.copy(id = UUID.randomUUID().toString(), x = e.x + offset / PAGE_WIDTH, y = e.y + offset / PAGE_HEIGHT, z = (page.elements.maxOfOrNull { it.z } ?: 0) + 1,
                    imagePath = e.imagePath?.let { files.duplicate(noteId, it) })
            }
            newSelection = EditorSelection(page.id, strokes.map { it.id }.toSet(), elements.map { it.id }.toSet())
            page.copy(strokes = page.strokes + strokes, elements = page.elements + elements)
        }
        _state.update { it.copy(selection = newSelection) }
    }

    fun bringSelectionToFront() {
        val selection = _state.value.selection ?: return
        editPage(selection.pageId) { page ->
            var z = page.elements.maxOfOrNull { it.z } ?: 0
            page.copy(elements = page.elements.map { if (it.id in selection.elementIds) it.copy(z = ++z) else it }.sortedBy { it.z })
        }
    }

    // --- Elements ----------------------------------------------------------------------------------------

    fun addTextBox(pageId: String, x: Float, y: Float) {
        val element = NoteElement(UUID.randomUUID().toString(), ElementKind.TEXT, x.coerceIn(0f, 0.9f), y.coerceIn(0f, 0.95f), 0.5f, 0.06f, html = "")
        editPage(pageId) { page -> page.copy(elements = page.elements + element.copy(z = (page.elements.maxOfOrNull { it.z } ?: 0) + 1)) }
        _state.update { it.copy(editingElementId = element.id, selection = null) }
    }

    fun startEditing(elementId: String) = _state.update { it.copy(editingElementId = elementId, selection = null) }

    /** Typing updates text without an undo step per keystroke; a step is recorded when editing ends. */
    private var textEditStart: EditorPage? = null

    fun updateText(pageId: String, elementId: String, html: String, heightFraction: Float?) {
        val page = _state.value.page(pageId) ?: return
        val element = page.elements.firstOrNull { it.id == elementId } ?: return
        if (element.html == html && (heightFraction == null || kotlin.math.abs(element.height - heightFraction) < 0.002f)) return
        if (textEditStart?.id != pageId) textEditStart = page
        val updated = element.copy(html = html, height = heightFraction?.coerceIn(0.03f, 1f) ?: element.height)
        replacePage(page.copy(elements = page.elements.map { if (it.id == elementId) updated else it }), recordFrom = null)
        markDirty(pageId)
    }

    /** Text boxes grow with their content; layout height is not an undoable edit. */
    fun updateElementHeight(pageId: String, elementId: String, heightFraction: Float) {
        val page = _state.value.page(pageId) ?: return
        replacePage(page.copy(elements = page.elements.map { if (it.id == elementId) it.copy(height = heightFraction.coerceIn(0.03f, 1f)) else it }), recordFrom = null)
        markDirty(pageId)
    }

    fun stopEditing() {
        val editing = _state.value.editingElementId
        _state.update { it.copy(editingElementId = null) }
        val before = textEditStart ?: return
        textEditStart = null
        val after = _state.value.page(before.id) ?: return
        // Drop empty text boxes left behind
        val empty = after.elements.firstOrNull { it.id == editing && it.kind == ElementKind.TEXT && com.bookflow.app.notes.domain.HtmlText.toPlain(it.html).isBlank() }
        val final = if (empty != null) after.copy(elements = after.elements - empty) else after
        replacePage(final, recordFrom = null)
        history.record(before, final); markDirty(before.id); refreshHistory()
    }

    fun insertImage(pageId: String, uri: Uri, centerX: Float = 0.5f, centerY: Float = 0.35f) {
        viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) { files.importImage(noteId, uri) }
                val width = 0.6f
                val height = width * PAGE_WIDTH * image.height / image.width / PAGE_HEIGHT
                val element = NoteElement(UUID.randomUUID().toString(), ElementKind.IMAGE, (centerX - width / 2).coerceIn(0f, 1f - width),
                    (centerY - height / 2).coerceIn(0f, (1f - height).coerceAtLeast(0f)), width, height.coerceAtMost(1f), imagePath = image.path)
                editPage(pageId) { page -> page.copy(elements = page.elements + element.copy(z = (page.elements.maxOfOrNull { it.z } ?: 0) + 1)) }
                _state.update { it.copy(selection = EditorSelection(pageId, emptySet(), setOf(element.id)), tool = EditorTool.HAND) }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Couldn't add this image") }
            }
        }
    }

    // --- Pages -------------------------------------------------------------------------------------------

    fun addPage(afterIndex: Int) {
        viewModelScope.launch {
            val background = _state.value.pages.getOrNull(afterIndex)?.background ?: PageBackground.LINED
            flush()
            val page = repository.addPage(noteId, afterIndex + 1, background)
            _state.update { s -> s.copy(pages = s.pages.toMutableList().apply { add((afterIndex + 1).coerceAtMost(size), EditorPage(page.id, background, emptyList(), emptyList())) }) }
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            flush()
            repository.deletePage(noteId, pageId)
            history.forgetPage(pageId)
            dirtyPages.remove(pageId)
            load()
            refreshHistory()
        }
    }

    fun setBackground(pageId: String, background: PageBackground, allPages: Boolean) {
        val targets = if (allPages) _state.value.pages.map { it.id } else listOf(pageId)
        _state.update { s -> s.copy(pages = s.pages.map { if (it.id in targets) it.copy(background = background) else it }) }
        viewModelScope.launch { targets.forEach { repository.setPageBackground(it, background) } }
    }

    fun movePage(pageId: String, delta: Int) {
        val pages = _state.value.pages.toMutableList()
        val from = pages.indexOfFirst { it.id == pageId }
        val to = (from + delta).coerceIn(0, pages.lastIndex)
        if (from < 0 || from == to) return
        pages.add(to, pages.removeAt(from))
        _state.update { it.copy(pages = pages) }
        viewModelScope.launch { repository.reorderPages(noteId, pages.map { it.id }) }
    }

    fun rename(title: String) {
        _state.update { it.copy(title = title) }
        viewModelScope.launch { repository.renameNote(noteId, title) }
    }

    // --- Undo / redo -------------------------------------------------------------------------------------

    fun undo() { history.undo()?.let { replacePage(it, recordFrom = null); markDirty(it.id) }; _state.update { it.copy(selection = null) }; refreshHistory() }
    fun redo() { history.redo()?.let { replacePage(it, recordFrom = null); markDirty(it.id) }; _state.update { it.copy(selection = null) }; refreshHistory() }

    private fun refreshHistory() = _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }

    private inline fun editPage(pageId: String, transform: (EditorPage) -> EditorPage) {
        val page = _state.value.page(pageId) ?: return
        replacePage(transform(page), recordFrom = page)
    }

    private fun replacePage(page: EditorPage, recordFrom: EditorPage?) {
        _state.update { s -> s.copy(pages = s.pages.map { if (it.id == page.id) page else it }) }
        if (recordFrom != null) { history.record(recordFrom, page); markDirty(page.id); refreshHistory() }
    }

    // --- Persistence -------------------------------------------------------------------------------------

    private fun markDirty(pageId: String) {
        synchronized(dirtyPages) { dirtyPages += pageId }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(SAVE_DEBOUNCE_MS); flush() }
    }

    private suspend fun flush() = saveLock.withLock {
        val ids = synchronized(dirtyPages) { dirtyPages.toList().also { dirtyPages.clear() } }
        if (ids.isEmpty()) return@withLock
        val pages = _state.value.pages.filter { it.id in ids }
        withContext(Dispatchers.IO) {
            pages.forEach { page ->
                try {
                    if (!page.inkUnavailable) InkSerializer.write(files.inkFile(noteId, page.id), page.strokes)
                    repository.saveElements(noteId, page.id, page.elements)
                } catch (e: Exception) {
                    synchronized(dirtyPages) { dirtyPages += page.id }
                    _state.update { it.copy(message = "Couldn't save changes: ${e.message.orEmpty()}") }
                }
            }
        }
    }

    /** Called when the app goes to the background: the process may be killed without [onCleared]. */
    fun saveNow() { persist(deleteIfEmpty = false) }

    override fun onCleared() {
        persist(deleteIfEmpty = true)
        super.onCleared()
    }

    /** Runs in the application scope so the last edits and the cover are written even as the screen closes. */
    private fun persist(deleteIfEmpty: Boolean) {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.notFound) return
        appScope.launch {
            flush()
            if (deleteIfEmpty && snapshot.title.isBlank() && snapshot.pages.all { it.strokes.isEmpty() && it.elements.isEmpty() }) {
                repository.deleteNote(noteId); return@launch
            }
            snapshot.pages.firstOrNull()?.let { first ->
                runCatching {
                    val bitmap = NotePageRenderer.renderBitmap(appContext, first, widthPx = 360)
                    repository.setThumbnail(noteId, files.saveThumbnail(noteId, bitmap))
                    bitmap.recycle()
                }
            }
        }
    }

    class Factory(
        private val noteId: String,
        private val repository: NotesRepository,
        private val files: NoteFileStore,
        private val pens: NotePenPreferences,
        private val appScope: CoroutineScope,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteEditorViewModel(noteId, repository, files, pens, appScope, appContext) as T
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
        const val MAX_PRESETS = 10
    }
}
