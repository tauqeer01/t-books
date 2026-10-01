package com.bookflow.app.presentation.screens.bookdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.model.Bookmark
import com.bookflow.app.domain.repository.AnnotationRepository
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AnnotationSortOrder(val displayName: String) {
    PAGE_ASC("Page (Low to High)"),
    PAGE_DESC("Page (High to Low)"),
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)")
}

data class BookDetailsUiState(
    val book: Book? = null,
    val bookCollections: List<BookCollection> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val allAnnotations: List<BookAnnotation> = emptyList(),
    val filteredHighlights: List<BookAnnotation> = emptyList(),
    val filteredNotes: List<BookAnnotation> = emptyList(),
    val selectedTab: Int = 0, // 0: Overview, 1: Bookmarks, 2: Highlights, 3: Notes
    val filterType: AnnotationType? = null,
    val filterColorHex: String? = null,
    val searchQuery: String = "",
    val sortOrder: AnnotationSortOrder = AnnotationSortOrder.PAGE_ASC,
    val editingAnnotation: BookAnnotation? = null,
    val editingBookmark: Bookmark? = null,
    val toastMessage: String? = null,
    val isLoading: Boolean = true
)

class BookDetailsViewModel(
    private val bookId: String,
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    private val _filterType = MutableStateFlow<AnnotationType?>(null)
    private val _filterColorHex = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(AnnotationSortOrder.PAGE_ASC)
    private val _editingAnnotation = MutableStateFlow<BookAnnotation?>(null)
    private val _editingBookmark = MutableStateFlow<Bookmark?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BookDetailsUiState> = combine(
        bookRepository.getBookById(bookId),
        collectionRepository.getAllCollections(),
        annotationRepository.getAnnotationsForBook(bookId),
        annotationRepository.getBookmarksForBook(bookId),
        _selectedTab,
        _filterType,
        _filterColorHex,
        _searchQuery,
        _sortOrder,
        _editingAnnotation,
        _editingBookmark,
        _toastMessage
    ) { params ->
        val book = params[0] as? Book
        @Suppress("UNCHECKED_CAST")
        val allCollections = params[1] as List<BookCollection>
        @Suppress("UNCHECKED_CAST")
        val annotations = params[2] as List<BookAnnotation>
        @Suppress("UNCHECKED_CAST")
        val bookmarks = params[3] as List<Bookmark>
        val tab = params[4] as Int
        val fType = params[5] as? AnnotationType
        val fColor = params[6] as? String
        val query = params[7] as String
        val sort = params[8] as AnnotationSortOrder
        val editAnn = params[9] as? BookAnnotation
        val editBm = params[10] as? Bookmark
        val toast = params[11] as? String

        // Find collections this book belongs to
        val belongingCols = if (book != null) {
            allCollections.filter { col ->
                book.collectionIds.contains(col.id) || book.collectionId == col.id
            }
        } else emptyList()

        // Filter & Sort Highlights (Highlights, Underlines, Strikethroughs)
        var highlights = annotations.filter {
            it.type in listOf(AnnotationType.HIGHLIGHT, AnnotationType.UNDERLINE, AnnotationType.STRIKETHROUGH)
        }
        if (fType != null) {
            highlights = highlights.filter { it.type == fType }
        }
        if (fColor != null) {
            highlights = highlights.filter { it.colorHex.equals(fColor, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            highlights = highlights.filter {
                it.selectedText.contains(query, ignoreCase = true) ||
                        it.noteContent.contains(query, ignoreCase = true)
            }
        }
        highlights = when (sort) {
            AnnotationSortOrder.PAGE_ASC -> highlights.sortedBy { it.pageIndex }
            AnnotationSortOrder.PAGE_DESC -> highlights.sortedByDescending { it.pageIndex }
            AnnotationSortOrder.DATE_DESC -> highlights.sortedByDescending { it.createdAt }
            AnnotationSortOrder.DATE_ASC -> highlights.sortedBy { it.createdAt }
        }

        // Filter & Sort Notes (Dedicated notes + annotations with linked notes)
        var notes = annotations.filter {
            it.type == AnnotationType.NOTE || it.noteContent.isNotBlank()
        }
        if (query.isNotBlank()) {
            notes = notes.filter {
                it.noteContent.contains(query, ignoreCase = true) ||
                        it.selectedText.contains(query, ignoreCase = true)
            }
        }
        notes = when (sort) {
            AnnotationSortOrder.PAGE_ASC -> notes.sortedBy { it.pageIndex }
            AnnotationSortOrder.PAGE_DESC -> notes.sortedByDescending { it.pageIndex }
            AnnotationSortOrder.DATE_DESC -> notes.sortedByDescending { it.createdAt }
            AnnotationSortOrder.DATE_ASC -> notes.sortedBy { it.createdAt }
        }

        // Filter Bookmarks
        var filteredBookmarks = bookmarks
        if (query.isNotBlank()) {
            filteredBookmarks = filteredBookmarks.filter {
                it.label.contains(query, ignoreCase = true) ||
                        "Page ${it.pageIndex + 1}".contains(query, ignoreCase = true)
            }
        }
        filteredBookmarks = filteredBookmarks.sortedBy { it.pageIndex }

        BookDetailsUiState(
            book = book,
            bookCollections = belongingCols,
            bookmarks = filteredBookmarks,
            allAnnotations = annotations,
            filteredHighlights = highlights,
            filteredNotes = notes,
            selectedTab = tab,
            filterType = fType,
            filterColorHex = fColor,
            searchQuery = query,
            sortOrder = sort,
            editingAnnotation = editAnn,
            editingBookmark = editBm,
            toastMessage = toast,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookDetailsUiState()
    )

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun setFilterType(type: AnnotationType?) {
        _filterType.value = type
    }

    fun setFilterColor(colorHex: String?) {
        _filterColorHex.value = colorHex
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: AnnotationSortOrder) {
        _sortOrder.value = order
    }

    fun startEditingNote(annotation: BookAnnotation) {
        _editingAnnotation.value = annotation
    }

    fun cancelEditingNote() {
        _editingAnnotation.value = null
    }

    fun saveAnnotationNote(annotationId: String, newNote: String) {
        viewModelScope.launch {
            val state = uiState.value
            val ann = state.allAnnotations.find { it.id == annotationId } ?: return@launch
            val updated = ann.copy(
                noteContent = newNote,
                updatedAt = System.currentTimeMillis()
            )
            annotationRepository.insertAnnotation(updated)
            _editingAnnotation.value = null
            _toastMessage.value = "Note updated successfully"
        }
    }

    fun deleteAnnotation(annotationId: String) {
        viewModelScope.launch {
            annotationRepository.deleteAnnotation(annotationId)
            _toastMessage.value = "Annotation deleted"
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            annotationRepository.deleteBookmarkById(bookmarkId)
            _toastMessage.value = "Bookmark removed"
        }
    }

    fun toggleFavorite() {
        val currentBook = uiState.value.book ?: return
        viewModelScope.launch {
            bookRepository.toggleFavorite(currentBook.id, !currentBook.isFavorite)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    class Factory(
        private val bookId: String,
        private val bookRepository: BookRepository,
        private val annotationRepository: AnnotationRepository,
        private val collectionRepository: CollectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BookDetailsViewModel(bookId, bookRepository, annotationRepository, collectionRepository) as T
        }
    }
}
