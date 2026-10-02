package com.bookflow.app.presentation.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.domain.model.Bookmark
import com.bookflow.app.domain.model.PageScrollMode
import com.bookflow.app.domain.model.ReaderTheme
import com.bookflow.app.domain.model.UserReadingPreferences
import com.bookflow.app.domain.repository.PreferencesRepository
import com.bookflow.app.domain.usecase.BookmarkUseCase
import com.bookflow.app.domain.usecase.DeleteAnnotationUseCase
import com.bookflow.app.domain.usecase.GetAnnotationsUseCase
import com.bookflow.app.domain.usecase.GetBookByIdUseCase
import com.bookflow.app.domain.usecase.SaveAnnotationUseCase
import com.bookflow.app.domain.usecase.UpdateProgressUseCase
import com.bookflow.app.pdf.engine.PdfEngine
import com.bookflow.app.pdf.engine.PdfEngineFactory
import com.bookflow.app.pdf.engine.MarkupRects
import com.bookflow.app.pdf.engine.PdfTextSelection
import com.bookflow.app.pdf.engine.markupRects
import com.bookflow.app.pdf.engine.mergedIntoLines
import com.bookflow.app.pdf.engine.PdfOutlineItem
import com.bookflow.app.pdf.engine.PdfSearchResult
import com.bookflow.app.pdf.engine.PdfSource
import com.bookflow.app.pdf.engine.RenderQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.bookflow.app.pdf.drawing.DrawingStroke
import com.bookflow.app.pdf.drawing.DrawingTool
import com.bookflow.app.pdf.drawing.StrokeSerializer
import com.bookflow.app.pdf.exporter.PdfAnnotatedExporter

sealed class DrawingAction {
    data class AddStroke(val stroke: DrawingStroke) : DrawingAction()
    data class RemoveStrokes(val strokes: List<DrawingStroke>) : DrawingAction()
    data class ClearPage(val pageIndex: Int, val strokes: List<DrawingStroke>) : DrawingAction()
    data class AddAnnotation(val annotation: BookAnnotation) : DrawingAction()
    data class RemoveAnnotations(val annotations: List<BookAnnotation>) : DrawingAction()
}

enum class ReaderTool {
    PAN,
    HIGHLIGHT,
    UNDERLINE,
    PEN,
    NOTE
}

data class ReaderUiState(
    val book: Book? = null,
    val preferences: UserReadingPreferences = UserReadingPreferences(),
    val isDocumentReady: Boolean = false,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val navigationRequest: Int = 0,
    val showReadingPreferences: Boolean = false,
    val currentPage: Int = 0,
    val pageCount: Int = 1,
    val isLoadingPage: Boolean = false,
    val outline: List<PdfOutlineItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isCurrentPageBookmarked: Boolean = false,
    val pageAnnotations: List<BookAnnotation> = emptyList(),
    val allBookAnnotations: List<BookAnnotation> = emptyList(),
    val activeTool: ReaderTool = ReaderTool.PAN,
    val activeHighlightColorHex: String = "#FFE066",
    val readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    val scrollMode: PageScrollMode = PageScrollMode.HORIZONTAL_PAGING,
    val isPageNavigatorVisible: Boolean = false,
    val isDrawerOpen: Boolean = false,
    val activeDrawerTab: Int = 0, // 0: Outline, 1: Bookmarks, 2: Annotations
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PdfSearchResult> = emptyList(),
    val currentSearchMatchIndex: Int = 0,
    val activeSearchHighlight: PdfSearchResult? = null,
    val isSearchHighlightActive: Boolean = false,
    val readingHistory: List<Int> = emptyList(),
    val isMoreOptionsSheetOpen: Boolean = false,
    val pendingNoteDialogLocation: Pair<Float, Float>? = null, // Normalized x, y
    val isGoToPageDialogOpen: Boolean = false,
    val showThumbnailsDrawer: Boolean = false,
    val selectedTextSelection: com.bookflow.app.pdf.engine.PdfTextSelection? = null,
    val activeAnnotationMenu: BookAnnotation? = null,
    val editingNoteAnnotation: BookAnnotation? = null,
    val isDrawingModeActive: Boolean = false,
    val drawingTool: DrawingTool = DrawingTool.PEN,
    val drawingColorHex: String = "#4F46E5",
    val drawingStrokeWidth: Float = 3.5f,
    val isStylusOnlyDrawing: Boolean = false,
    val canUndoDrawing: Boolean = false,
    val canRedoDrawing: Boolean = false,
    val isExportingPdf: Boolean = false,
    val exportProgress: Pair<Int, Int>? = null,
    val exportedPdfFile: File? = null,
    val showExportDialog: Boolean = false,
    val toastMessage: String? = null,
    val errorMessage: String? = null
)

class ReaderViewModel(
    private val context: Context,
    private val bookId: String,
    private val initialPage: Int,
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val updateProgressUseCase: UpdateProgressUseCase,
    private val getAnnotationsUseCase: GetAnnotationsUseCase,
    private val saveAnnotationUseCase: SaveAnnotationUseCase,
    private val deleteAnnotationUseCase: DeleteAnnotationUseCase,
    private val bookmarkUseCase: BookmarkUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val pdfEngineFactory: PdfEngineFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(currentPage = initialPage))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var pdfEngine: PdfEngine? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        loadBookAndInitializeEngine()
        observePreferences()
        observeAnnotationsAndBookmarks()
    }

    private fun loadBookAndInitializeEngine() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPage = true)
            val book = getBookByIdUseCase(bookId).first()
            if (book == null) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPage = false,
                    errorMessage = "Book not found"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                book = book,
                currentPage = if (initialPage in 0 until book.pageCount) initialPage else book.currentPage,
                pageCount = book.pageCount
            )

            // Initialize PDF Engine
            val engine = pdfEngineFactory.createEngine()
            pdfEngine = engine

            val source = if (book.uriString != null) {
                PdfSource.UriSource(Uri.parse(book.uriString), context)
            } else {
                PdfSource.FileSource(File(book.filePath))
            }

            val result = engine.openDocument(source)
            if (result is com.bookflow.app.pdf.engine.PdfDocumentResult.Success) {
                val pageCount = engine.getPageCount()
                val page = _uiState.value.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                _uiState.value = _uiState.value.copy(pageCount = pageCount, currentPage = page, isDocumentReady = true, isLoadingPage = false)
                refreshPageAnnotationsAndBookmark(page)
                updateBookProgress(page)
                try {
                    val outline = engine.getTableOfContents()
                    _uiState.value = _uiState.value.copy(outline = outline)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* Rendering remains available for image-only or unsupported text. */ }
            } else if (result is com.bookflow.app.pdf.engine.PdfDocumentResult.Error) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPage = false,
                    errorMessage = result.message
                )
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.preferencesForBook(bookId).collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    preferences = prefs,
                    activeHighlightColorHex = prefs.defaultHighlightColor,
                    drawingColorHex = prefs.penColor,
                    drawingStrokeWidth = prefs.penWidth,
                    isStylusOnlyDrawing = prefs.stylusOnly,
                    readerTheme = prefs.readerTheme,
                    scrollMode = prefs.scrollMode
                )
            }
        }
    }

    private fun observeAnnotationsAndBookmarks() {
        viewModelScope.launch {
            getAnnotationsUseCase.forBook(bookId).collect { annotations ->
                val currentPage = _uiState.value.currentPage
                val pageAnns = annotations.filter { it.pageIndex == currentPage }
                _uiState.value = _uiState.value.copy(
                    allBookAnnotations = annotations,
                    pageAnnotations = pageAnns
                )
            }
        }

        viewModelScope.launch {
            bookmarkUseCase.forBook(bookId).collect { bookmarks ->
                val currentPage = _uiState.value.currentPage
                val isBookmarked = bookmarks.any { it.pageIndex == currentPage }
                _uiState.value = _uiState.value.copy(
                    bookmarks = bookmarks,
                    isCurrentPageBookmarked = isBookmarked
                )
            }
        }
    }

    fun jumpToPage(page: Int) {
        val target = page.coerceIn(0, (_uiState.value.pageCount - 1).coerceAtLeast(0))
        if (target != _uiState.value.currentPage) {
            val history = (_uiState.value.readingHistory + _uiState.value.currentPage)
                .distinct()
                .takeLast(20)
            _uiState.value = _uiState.value.copy(
                currentPage = target,
                readingHistory = history,
                selectedTextSelection = null,
                activeAnnotationMenu = null,
                navigationRequest = _uiState.value.navigationRequest + 1
            )
            updateBookProgress(target)
            refreshPageAnnotationsAndBookmark(target)
        }
    }

    fun nextPage() {
        if (_uiState.value.currentPage < _uiState.value.pageCount - 1) {
            jumpToPage(_uiState.value.currentPage + 1)
        }
    }

    fun previousPage() {
        if (_uiState.value.currentPage > 0) {
            jumpToPage(_uiState.value.currentPage - 1)
        }
    }

    private fun updateBookProgress(currentPage: Int) {
        viewModelScope.launch {
            val total = _uiState.value.pageCount
            updateProgressUseCase(bookId, currentPage, total)
        }
    }

    private fun refreshPageAnnotationsAndBookmark(page: Int) {
        val pageAnns = _uiState.value.allBookAnnotations.filter { it.pageIndex == page }
        val isBm = _uiState.value.bookmarks.any { it.pageIndex == page }
        _uiState.value = _uiState.value.copy(
            pageAnnotations = pageAnns,
            isCurrentPageBookmarked = isBm
        )
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val page = _uiState.value.currentPage
            bookmarkUseCase.toggle(bookId, page, "Page ${page + 1}")
        }
    }

    fun selectTool(tool: ReaderTool) {
        _uiState.value = _uiState.value.copy(activeTool = tool)
    }

    fun selectHighlightColor(hex: String) {
        _uiState.value = _uiState.value.copy(activeHighlightColorHex = hex)
    }

    fun changeTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            preferencesRepository.updateTheme(theme)
        }
    }

    fun togglePageNavigator() {
        _uiState.value = _uiState.value.copy(
            isPageNavigatorVisible = !_uiState.value.isPageNavigatorVisible && !_uiState.value.isDrawingModeActive
        )
    }

    fun setPageNavigatorVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isPageNavigatorVisible = visible)
    }

    fun setScrollMode(mode: PageScrollMode) {
        if (mode == _uiState.value.scrollMode) return
        val preferences = _uiState.value.preferences.copy(scrollMode = mode)
        _uiState.value = _uiState.value.copy(scrollMode = mode, preferences = preferences)
        viewModelScope.launch { preferencesRepository.savePreferences(preferences, bookId) }
    }

    fun toggleScrollMode() {
        val modes = PageScrollMode.entries
        val newMode = modes[(modes.indexOf(_uiState.value.scrollMode) + 1) % modes.size]
        _uiState.value = _uiState.value.copy(scrollMode = newMode)
        viewModelScope.launch {
            preferencesRepository.savePreferences(_uiState.value.preferences.copy(scrollMode = newMode), bookId)
        }
    }

    fun openGoToPageDialog() {
        _uiState.value = _uiState.value.copy(isGoToPageDialogOpen = true)
    }

    fun closeGoToPageDialog() {
        _uiState.value = _uiState.value.copy(isGoToPageDialogOpen = false)
    }

    fun openThumbnailsDrawer() {
        _uiState.value = _uiState.value.copy(showThumbnailsDrawer = true)
    }

    fun closeThumbnailsDrawer() {
        _uiState.value = _uiState.value.copy(showThumbnailsDrawer = false)
    }

    fun onPageScrolled(pageIndex: Int) {
        if (pageIndex != _uiState.value.currentPage && pageIndex in 0 until _uiState.value.pageCount) {
            val history = (_uiState.value.readingHistory + _uiState.value.currentPage)
                .distinct()
                .takeLast(25)
            _uiState.value = _uiState.value.copy(
                currentPage = pageIndex,
                readingHistory = history
            )
            updateBookProgress(pageIndex)
            refreshPageAnnotationsAndBookmark(pageIndex)
        }
    }

    suspend fun loadPageBitmap(pageIndex: Int): Bitmap? {
        val engine = pdfEngine ?: return null
        return engine.renderPage(pageIndex, scale = 1.0f, quality = if (_uiState.value.preferences.highResolutionRendering) RenderQuality.HIGH_DPI else RenderQuality.STANDARD)
    }

    suspend fun loadThumbnailBitmap(pageIndex: Int): Bitmap? {
        val engine = pdfEngine ?: return null
        return engine.renderThumbnail(pageIndex)
    }

    suspend fun loadPageAspectRatio(pageIndex: Int): Float = pdfEngine?.loadPageDimensions(pageIndex)?.aspectRatio ?: .707f

    fun onPageTapped(pageIndex: Int, x: Float, y: Float) {
        viewModelScope.launch {
            val target = try { pdfEngine?.internalLinkAt(pageIndex, x, y) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            val paged = _uiState.value.scrollMode != PageScrollMode.CONTINUOUS_VERTICAL
            when {
                target != null -> jumpToPage(target)
                paged && x < EDGE_TAP_ZONE -> { clearSelection(); closeAnnotationMenu(); previousPage() }
                paged && x > 1f - EDGE_TAP_ZONE -> { clearSelection(); closeAnnotationMenu(); nextPage() }
                else -> { clearSelection(); closeAnnotationMenu(); togglePageNavigator() }
            }
        }
    }

    fun getPageAspectRatio(pageIndex: Int): Float {
        val engine = pdfEngine ?: return 0.707f
        return engine.getPageAspectRatio(pageIndex)
    }

    fun evictPage(pageIndex: Int) {
        pdfEngine?.evictPage(pageIndex)
    }

    fun setDrawerOpen(open: Boolean, tab: Int = _uiState.value.activeDrawerTab) {
        _uiState.value = _uiState.value.copy(
            isDrawerOpen = open,
            activeDrawerTab = tab
        )
    }

    fun setSearchOpen(open: Boolean) {
        if (!open) { searchJob?.cancel() }
        _uiState.value = _uiState.value.copy(
            isSearchOpen = open,
            isSearching = false,
            searchQuery = if (!open) "" else _uiState.value.searchQuery,
            searchResults = if (!open) emptyList() else _uiState.value.searchResults
        )
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(searchQuery = query, searchResults = emptyList(), currentSearchMatchIndex = 0,
            activeSearchHighlight = null, isSearchHighlightActive = false, isSearching = query.isNotBlank(), searchError = null)
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            try {
                val results = pdfEngine?.search(query) ?: emptyList()
                _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(isSearching = false, searchError = "Text search is unavailable for this document.") }
        }
    }

    fun showReadingPreferences(show: Boolean) { _uiState.value = _uiState.value.copy(showReadingPreferences = show) }
    fun saveReadingPreferences(prefs: UserReadingPreferences, forBook: Boolean) {
        viewModelScope.launch {
            preferencesRepository.savePreferences(prefs, if (forBook) bookId else null)
            if (!forBook) preferencesRepository.clearBookPreferences(bookId)
        }
    }
    fun useGlobalPreferences() { viewModelScope.launch { preferencesRepository.clearBookPreferences(bookId) } }

    fun jumpToSearchResult(result: PdfSearchResult) {
        jumpToPage(result.pageIndex)
        val idx = _uiState.value.searchResults.indexOf(result).takeIf { it >= 0 } ?: result.matchIndex
        _uiState.value = _uiState.value.copy(
            activeSearchHighlight = result,
            isSearchHighlightActive = true,
            currentSearchMatchIndex = idx
        )
        // Temporarily highlight the search result for 4 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            if (_uiState.value.activeSearchHighlight == result) {
                _uiState.value = _uiState.value.copy(isSearchHighlightActive = false)
            }
        }
    }

    fun nextSearchResult() {
        val results = _uiState.value.searchResults
        if (results.isEmpty()) return
        val nextIdx = (_uiState.value.currentSearchMatchIndex + 1) % results.size
        jumpToSearchResult(results[nextIdx])
    }

    fun previousSearchResult() {
        val results = _uiState.value.searchResults
        if (results.isEmpty()) return
        val prevIdx = if (_uiState.value.currentSearchMatchIndex > 0) {
            _uiState.value.currentSearchMatchIndex - 1
        } else {
            results.size - 1
        }
        jumpToSearchResult(results[prevIdx])
    }

    fun clearSearchHighlight() {
        _uiState.value = _uiState.value.copy(
            activeSearchHighlight = null,
            isSearchHighlightActive = false
        )
    }

    fun openMoreOptionsSheet(open: Boolean) {
        _uiState.value = _uiState.value.copy(isMoreOptionsSheetOpen = open)
    }

    fun onPageTappedForAnnotation(normX: Float, normY: Float) {
        when (_uiState.value.activeTool) {
            ReaderTool.NOTE -> {
                _uiState.value = _uiState.value.copy(pendingNoteDialogLocation = Pair(normX, normY))
            }
            ReaderTool.HIGHLIGHT -> {
                // Create a simulated highlight block around tapped paragraph
                createHighlightAt(normX, normY, AnnotationType.HIGHLIGHT)
            }
            ReaderTool.UNDERLINE -> {
                createHighlightAt(normX, normY, AnnotationType.UNDERLINE)
            }
            else -> {
                // In pan mode, single tap toggles the page navigator
                togglePageNavigator()
            }
        }
    }

    private fun createHighlightAt(normX: Float, normY: Float, type: AnnotationType) {
        viewModelScope.launch {
            val page = _uiState.value.currentPage
            val left = (normX - 0.25f).coerceAtLeast(0.08f)
            val right = (normX + 0.25f).coerceAtMost(0.92f)
            val top = (normY - 0.02f).coerceAtLeast(0.05f)
            val bottom = (normY + 0.02f).coerceAtMost(0.95f)

            val annotation = BookAnnotation(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                pageIndex = page,
                type = type,
                colorHex = _uiState.value.activeHighlightColorHex,
                selectedText = "Highlighted passage on page ${page + 1}",
                noteContent = "",
                rectLeft = left,
                rectTop = top,
                rectRight = right,
                rectBottom = bottom,
                createdAt = System.currentTimeMillis()
            )
            saveAnnotationUseCase(annotation)
        }
    }

    fun submitNote(noteText: String) {
        val location = _uiState.value.pendingNoteDialogLocation ?: return
        viewModelScope.launch {
            val annotation = BookAnnotation(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                pageIndex = _uiState.value.currentPage,
                type = AnnotationType.NOTE,
                colorHex = _uiState.value.activeHighlightColorHex,
                selectedText = "Note marker",
                noteContent = noteText,
                rectLeft = location.first - 0.05f,
                rectTop = location.second - 0.05f,
                rectRight = location.first + 0.05f,
                rectBottom = location.second + 0.05f,
                createdAt = System.currentTimeMillis()
            )
            saveAnnotationUseCase(annotation)
            _uiState.value = _uiState.value.copy(pendingNoteDialogLocation = null)
        }
    }

    fun dismissNoteDialog() {
        _uiState.value = _uiState.value.copy(pendingNoteDialogLocation = null)
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch {
            deleteAnnotationUseCase(id)
            if (_uiState.value.activeAnnotationMenu?.id == id) {
                _uiState.value = _uiState.value.copy(activeAnnotationMenu = null)
            }
        }
    }

    // --- PHASE 4: TEXT SELECTION & CONTEXTUAL HIGHLIGHTING ---

    fun onPageLongPressed(pageIndex: Int, normX: Float, normY: Float) {
        viewModelScope.launch {
            val engine = pdfEngine ?: return@launch
            val selection = try { engine.selectTextAtPoint(pageIndex, normX, normY) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            if (selection != null) {
                _uiState.value = _uiState.value.copy(
                    selectedTextSelection = selection,
                    activeAnnotationMenu = null
                )
            }
        }
    }

    fun createAnnotationFromSelection(
        type: AnnotationType,
        colorHex: String? = null,
        noteContent: String = ""
    ) {
        val selection = _uiState.value.selectedTextSelection ?: return
        val lines = selection.highlightRects.mergedIntoLines()
        val rect = if (lines.isEmpty()) com.bookflow.app.pdf.engine.PdfRect(0.1f, 0.2f, 0.9f, 0.25f) else MarkupRects.union(lines)
        val color = colorHex ?: _uiState.value.activeHighlightColorHex

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val annotation = BookAnnotation(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                pageIndex = selection.pageIndex,
                type = type,
                colorHex = color,
                selectedText = selection.text,
                noteContent = noteContent,
                // Normalized PDF page coordinates (persisted independently from screen pixels!)
                rectLeft = rect.left,
                rectTop = rect.top,
                rectRight = rect.right,
                rectBottom = rect.bottom,
                strokePathData = if (lines.size > 1) MarkupRects.encode(lines) else null,
                createdAt = now,
                updatedAt = now
            )
            saveAnnotationUseCase(annotation)
            _uiState.value = _uiState.value.copy(
                selectedTextSelection = null,
                toastMessage = when (type) {
                    AnnotationType.HIGHLIGHT -> "Highlighted text"
                    AnnotationType.UNDERLINE -> "Underlined text"
                    AnnotationType.STRIKETHROUGH -> "Strikethrough applied"
                    AnnotationType.NOTE -> "Note added"
                    else -> "Annotation saved"
                }
            )
        }
    }

    fun copySelectionToClipboard() {
        val selection = _uiState.value.selectedTextSelection ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("PDF Selected Text", selection.text)
        clipboard?.setPrimaryClip(clip)

        _uiState.value = _uiState.value.copy(
            selectedTextSelection = null,
            toastMessage = "Copied to clipboard"
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedTextSelection = null)
    }

    fun onAnnotationTapped(annotation: BookAnnotation) {
        _uiState.value = _uiState.value.copy(
            activeAnnotationMenu = annotation,
            selectedTextSelection = null
        )
    }

    fun closeAnnotationMenu() {
        _uiState.value = _uiState.value.copy(activeAnnotationMenu = null)
    }

    fun updateAnnotationColor(annotationId: String, newColorHex: String) {
        viewModelScope.launch {
            val existing = _uiState.value.allBookAnnotations.find { it.id == annotationId } ?: return@launch
            val updated = existing.copy(
                colorHex = newColorHex,
                updatedAt = System.currentTimeMillis()
            )
            saveAnnotationUseCase(updated)
            _uiState.value = _uiState.value.copy(
                activeAnnotationMenu = updated,
                toastMessage = "Color updated"
            )
        }
    }

    fun startEditingNote(annotation: BookAnnotation) {
        _uiState.value = _uiState.value.copy(
            editingNoteAnnotation = annotation,
            activeAnnotationMenu = null
        )
    }

    fun saveAnnotationNote(annotationId: String, newNote: String) {
        viewModelScope.launch {
            val existing = _uiState.value.allBookAnnotations.find { it.id == annotationId } ?: return@launch
            val updated = existing.copy(
                noteContent = newNote,
                updatedAt = System.currentTimeMillis()
            )
            saveAnnotationUseCase(updated)
            if (annotationId == newStickyNoteId) newStickyNoteId = null
            _uiState.value = _uiState.value.copy(
                editingNoteAnnotation = null,
                toastMessage = "Note saved"
            )
        }
    }

    fun cancelEditingNote() {
        val editing = _uiState.value.editingNoteAnnotation
        _uiState.value = _uiState.value.copy(editingNoteAnnotation = null)
        if (editing != null && editing.id == newStickyNoteId && editing.noteContent.isBlank()) {
            newStickyNoteId = null
            viewModelScope.launch {
                deleteAnnotationUseCase(editing.id)
                undoStack.removeAll { it is DrawingAction.AddAnnotation && it.annotation.id == editing.id }
                _uiState.value = _uiState.value.copy(canUndoDrawing = undoStack.isNotEmpty())
            }
        }
    }

    // ==========================================
    // PHASE 6: PEN & FREEHAND DRAWING ANNOTATIONS
    // ==========================================
    private val undoStack = mutableListOf<DrawingAction>()
    private val redoStack = mutableListOf<DrawingAction>()

    fun setDrawingMode(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isDrawingModeActive = active,
            isPageNavigatorVisible = if (active) false else _uiState.value.isPageNavigatorVisible,
            activeAnnotationMenu = null,
            selectedTextSelection = null
        )
    }

    // Last color/width chosen for ink tools (pen + shapes) and the highlighter, kept across tool switches
    private var inkStyle: Pair<String, Float>? = null
    private var highlighterStyle: Pair<String, Float> = "#FFE066" to 14.0f

    fun setDrawingTool(tool: DrawingTool) {
        val current = _uiState.value
        val (color, width) = when {
            !tool.hasColor -> current.drawingColorHex to current.drawingStrokeWidth
            tool.usesHighlighterPalette -> highlighterStyle
            else -> inkStyle ?: (current.preferences.penColor to current.preferences.penWidth)
        }
        _uiState.value = current.copy(
            drawingTool = tool,
            drawingColorHex = color,
            drawingStrokeWidth = width
        )
    }

    private fun rememberToolStyle() {
        val s = _uiState.value
        when {
            !s.drawingTool.hasColor -> Unit
            s.drawingTool.usesHighlighterPalette -> highlighterStyle = s.drawingColorHex to s.drawingStrokeWidth
            else -> inkStyle = s.drawingColorHex to s.drawingStrokeWidth
        }
    }

    fun setDrawingColor(colorHex: String) {
        _uiState.value = _uiState.value.copy(drawingColorHex = colorHex)
        rememberToolStyle()
    }

    fun setStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.copy(drawingStrokeWidth = width)
        rememberToolStyle()
    }

    fun toggleStylusOnlyDrawing() {
        val next = !_uiState.value.isStylusOnlyDrawing
        _uiState.value = _uiState.value.copy(
            isStylusOnlyDrawing = next,
            toastMessage = if (next) "Stylus-only drawing enabled (Fingers scroll/zoom)" else "Touch & Stylus drawing enabled"
        )
    }

    fun onStrokeFinished(stroke: DrawingStroke) {
        viewModelScope.launch {
            val serialized = StrokeSerializer.serialize(stroke)
            val annotation = BookAnnotation(
                id = stroke.id,
                bookId = bookId,
                pageIndex = stroke.pageIndex,
                type = AnnotationType.PEN_DRAW,
                colorHex = stroke.colorHex,
                strokePathData = serialized,
                createdAt = stroke.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            saveAnnotationUseCase(annotation)

            undoStack.add(DrawingAction.AddStroke(stroke))
            redoStack.clear()
            _uiState.value = _uiState.value.copy(
                canUndoDrawing = undoStack.isNotEmpty(),
                canRedoDrawing = false
            )
        }
    }

    // Ids already being erased, so repeated eraser move events don't delete (and record undo for) them twice
    private val erasingIds = mutableSetOf<String>()

    /** Text selection between two normalized points on a page, for the text markup tools' live preview. */
    suspend fun selectTextRange(pageIndex: Int, startX: Float, startY: Float, endX: Float, endY: Float): PdfTextSelection? =
        try {
            pdfEngine?.selectTextRange(pageIndex, startX, startY, endX, endY)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }

    fun commitTextMarkup(selection: PdfTextSelection?, tool: DrawingTool) {
        if (selection == null || selection.highlightRects.isEmpty()) {
            _uiState.value = _uiState.value.copy(toastMessage = "No text there to mark")
            return
        }
        val type = when (tool) {
            DrawingTool.TEXT_UNDERLINE -> AnnotationType.UNDERLINE
            DrawingTool.TEXT_STRIKETHROUGH -> AnnotationType.STRIKETHROUGH
            else -> AnnotationType.HIGHLIGHT
        }
        val lines = selection.highlightRects
        val union = MarkupRects.union(lines)
        val now = System.currentTimeMillis()
        addAnnotationWithUndo(
            BookAnnotation(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                pageIndex = selection.pageIndex,
                type = type,
                colorHex = _uiState.value.drawingColorHex,
                selectedText = selection.text,
                rectLeft = union.left,
                rectTop = union.top,
                rectRight = union.right,
                rectBottom = union.bottom,
                strokePathData = MarkupRects.encode(lines),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private var newStickyNoteId: String? = null

    /** Drops a sticky note at a normalized point and opens the note editor for it. */
    fun placeStickyNote(pageIndex: Int, normX: Float, normY: Float) {
        val now = System.currentTimeMillis()
        val note = BookAnnotation(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            pageIndex = pageIndex,
            type = AnnotationType.NOTE,
            colorHex = _uiState.value.drawingColorHex,
            rectLeft = (normX - 0.025f).coerceIn(0f, 1f),
            rectTop = (normY - 0.018f).coerceIn(0f, 1f),
            rectRight = (normX + 0.025f).coerceIn(0f, 1f),
            rectBottom = (normY + 0.018f).coerceIn(0f, 1f),
            createdAt = now,
            updatedAt = now
        )
        newStickyNoteId = note.id
        addAnnotationWithUndo(note)
        _uiState.value = _uiState.value.copy(editingNoteAnnotation = note)
    }

    private fun addAnnotationWithUndo(annotation: BookAnnotation) {
        viewModelScope.launch {
            saveAnnotationUseCase(annotation)
            undoStack.add(DrawingAction.AddAnnotation(annotation))
            redoStack.clear()
            _uiState.value = _uiState.value.copy(canUndoDrawing = true, canRedoDrawing = false)
        }
    }

    fun eraseStrokesAt(pageIndex: Int, normX: Float, normY: Float, normRadius: Float = 0.035f) {
        val pageStrokes = _uiState.value.allBookAnnotations
            .filter { it.pageIndex == pageIndex && it.type == AnnotationType.PEN_DRAW }

        val erasedStrokes = mutableListOf<DrawingStroke>()
        for (ann in pageStrokes) {
            val data = ann.strokePathData ?: continue
            val stroke = StrokeSerializer.deserialize(ann.id, pageIndex, data, ann.colorHex) ?: continue
            if (stroke.intersectsPoint(normX, normY, normRadius)) {
                erasedStrokes.add(stroke)
            }
        }

        erasedStrokes.removeAll { it.id in erasingIds }
        val erasedMarkups = _uiState.value.allBookAnnotations.filter { ann ->
            ann.pageIndex == pageIndex && ann.type != AnnotationType.PEN_DRAW && ann.id !in erasingIds &&
                ann.markupRects().any { r ->
                    normX in (r.left - normRadius)..(r.right + normRadius) && normY in (r.top - normRadius)..(r.bottom + normRadius)
                }
        }
        if (erasedMarkups.isNotEmpty()) {
            erasingIds += erasedMarkups.map { it.id }
            viewModelScope.launch {
                erasedMarkups.forEach { deleteAnnotationUseCase(it.id) }
                undoStack.add(DrawingAction.RemoveAnnotations(erasedMarkups))
                redoStack.clear()
                _uiState.value = _uiState.value.copy(canUndoDrawing = true, canRedoDrawing = false)
            }
        }

        if (erasedStrokes.isNotEmpty()) {
            erasingIds += erasedStrokes.map { it.id }
            viewModelScope.launch {
                erasedStrokes.forEach { stroke ->
                    deleteAnnotationUseCase(stroke.id)
                }
                undoStack.add(DrawingAction.RemoveStrokes(erasedStrokes))
                redoStack.clear()
                _uiState.value = _uiState.value.copy(
                    canUndoDrawing = undoStack.isNotEmpty(),
                    canRedoDrawing = false
                )
            }
        }
    }

    fun undoDrawing() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeAt(undoStack.lastIndex)
        viewModelScope.launch {
            when (action) {
                is DrawingAction.AddStroke -> {
                    deleteAnnotationUseCase(action.stroke.id)
                    redoStack.add(action)
                }
                is DrawingAction.RemoveStrokes -> {
                    action.strokes.forEach { stroke ->
                        saveAnnotationUseCase(
                            BookAnnotation(
                                id = stroke.id,
                                bookId = bookId,
                                pageIndex = stroke.pageIndex,
                                type = AnnotationType.PEN_DRAW,
                                colorHex = stroke.colorHex,
                                strokePathData = StrokeSerializer.serialize(stroke),
                                createdAt = stroke.createdAt,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    redoStack.add(action)
                }
                is DrawingAction.AddAnnotation -> {
                    deleteAnnotationUseCase(action.annotation.id)
                    redoStack.add(action)
                }
                is DrawingAction.RemoveAnnotations -> {
                    action.annotations.forEach { saveAnnotationUseCase(it) }
                    redoStack.add(action)
                }
                is DrawingAction.ClearPage -> {
                    action.strokes.forEach { stroke ->
                        saveAnnotationUseCase(
                            BookAnnotation(
                                id = stroke.id,
                                bookId = bookId,
                                pageIndex = stroke.pageIndex,
                                type = AnnotationType.PEN_DRAW,
                                colorHex = stroke.colorHex,
                                strokePathData = StrokeSerializer.serialize(stroke),
                                createdAt = stroke.createdAt,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    redoStack.add(action)
                }
            }
            _uiState.value = _uiState.value.copy(
                canUndoDrawing = undoStack.isNotEmpty(),
                canRedoDrawing = redoStack.isNotEmpty()
            )
        }
    }

    fun redoDrawing() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeAt(redoStack.lastIndex)
        viewModelScope.launch {
            when (action) {
                is DrawingAction.AddStroke -> {
                    saveAnnotationUseCase(
                        BookAnnotation(
                            id = action.stroke.id,
                            bookId = bookId,
                            pageIndex = action.stroke.pageIndex,
                            type = AnnotationType.PEN_DRAW,
                            colorHex = action.stroke.colorHex,
                            strokePathData = StrokeSerializer.serialize(action.stroke),
                            createdAt = action.stroke.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    undoStack.add(action)
                }
                is DrawingAction.RemoveStrokes -> {
                    action.strokes.forEach { stroke ->
                        deleteAnnotationUseCase(stroke.id)
                    }
                    undoStack.add(action)
                }
                is DrawingAction.ClearPage -> {
                    action.strokes.forEach { stroke ->
                        deleteAnnotationUseCase(stroke.id)
                    }
                    undoStack.add(action)
                }
                is DrawingAction.AddAnnotation -> {
                    saveAnnotationUseCase(action.annotation)
                    undoStack.add(action)
                }
                is DrawingAction.RemoveAnnotations -> {
                    action.annotations.forEach { deleteAnnotationUseCase(it.id) }
                    undoStack.add(action)
                }
            }
            _uiState.value = _uiState.value.copy(
                canUndoDrawing = undoStack.isNotEmpty(),
                canRedoDrawing = redoStack.isNotEmpty()
            )
        }
    }

    fun clearCurrentPageDrawings() {
        val currentPage = _uiState.value.currentPage
        val pageStrokes = _uiState.value.allBookAnnotations
            .filter { it.pageIndex == currentPage && it.type == AnnotationType.PEN_DRAW }

        if (pageStrokes.isEmpty()) {
            _uiState.value = _uiState.value.copy(toastMessage = "No drawings on this page")
            return
        }

        viewModelScope.launch {
            val list = mutableListOf<DrawingStroke>()
            pageStrokes.forEach { ann ->
                val stroke = StrokeSerializer.deserialize(ann.id, currentPage, ann.strokePathData ?: "", ann.colorHex)
                if (stroke != null) list.add(stroke)
                deleteAnnotationUseCase(ann.id)
            }
            if (list.isNotEmpty()) {
                undoStack.add(DrawingAction.ClearPage(currentPage, list))
                redoStack.clear()
            }
            _uiState.value = _uiState.value.copy(
                canUndoDrawing = undoStack.isNotEmpty(),
                canRedoDrawing = false,
                toastMessage = "Page drawings cleared"
            )
        }
    }

    fun exportAnnotatedPdf() {
        val engine = pdfEngine ?: return
        val currentBook = _uiState.value.book ?: return
        val annotations = _uiState.value.allBookAnnotations

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExportingPdf = true,
                showExportDialog = true,
                exportProgress = Pair(0, currentBook.pageCount.coerceAtLeast(1))
            )

            try {
                val exportedFile = PdfAnnotatedExporter.exportAnnotatedPdf(
                    context = context,
                    pdfEngine = engine,
                    book = currentBook,
                    annotations = annotations,
                    onProgress = { current, total ->
                        _uiState.value = _uiState.value.copy(exportProgress = Pair(current, total))
                    }
                )

                _uiState.value = _uiState.value.copy(
                    isExportingPdf = false,
                    exportedPdfFile = exportedFile,
                    toastMessage = "Export complete: ${exportedFile.name}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExportingPdf = false,
                    errorMessage = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun dismissExportDialog() {
        _uiState.value = _uiState.value.copy(
            showExportDialog = false,
            exportedPdfFile = null,
            isExportingPdf = false
        )
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        val engine = pdfEngine
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching { engine?.close() }
        }
    }

    class Factory(
        private val context: Context,
        private val bookId: String,
        private val initialPage: Int,
        private val getBookByIdUseCase: GetBookByIdUseCase,
        private val updateProgressUseCase: UpdateProgressUseCase,
        private val getAnnotationsUseCase: GetAnnotationsUseCase,
        private val saveAnnotationUseCase: SaveAnnotationUseCase,
        private val deleteAnnotationUseCase: DeleteAnnotationUseCase,
        private val bookmarkUseCase: BookmarkUseCase,
        private val preferencesRepository: PreferencesRepository,
        private val pdfEngineFactory: PdfEngineFactory
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(
                context,
                bookId,
                initialPage,
                getBookByIdUseCase,
                updateProgressUseCase,
                getAnnotationsUseCase,
                saveAnnotationUseCase,
                deleteAnnotationUseCase,
                bookmarkUseCase,
                preferencesRepository,
                pdfEngineFactory
            ) as T
        }
    }
}

/** Fraction of the page width on each side where a tap turns the page in paged reading modes. */
private const val EDGE_TAP_ZONE = 0.2f
