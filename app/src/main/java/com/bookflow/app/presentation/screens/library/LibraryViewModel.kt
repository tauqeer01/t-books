package com.bookflow.app.presentation.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.model.BookSortOrder
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.repository.CollectionRepository
import com.bookflow.app.domain.usecase.GetBooksUseCase
import com.bookflow.app.domain.usecase.GetCollectionsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryViewMode {
    GRID,
    LIST
}

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val allCollections: List<BookCollection> = emptyList(),
    val continueReadingBook: Book? = null,
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val availableCategories: List<String> = listOf("All", "PDF", "Recent", "Favorites"),
    val sortOrder: BookSortOrder = BookSortOrder.RECENTLY_OPENED,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val bookToManageCollections: Book? = null,
    val selectedBookCollectionIds: List<String> = emptyList()
)

class LibraryViewModel(
    private val getBooksUseCase: GetBooksUseCase,
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val bookRepository: BookRepository,
    private val collectionRepository: CollectionRepository,
    private val preferencesRepository: com.bookflow.app.domain.repository.PreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow("All")
    private val _sortOrder = MutableStateFlow(BookSortOrder.RECENTLY_OPENED)
    private val _viewMode = MutableStateFlow(LibraryViewMode.GRID)
    private val _isImporting = MutableStateFlow(false)
    private val _importMessage = MutableStateFlow<String?>(null)
    private val _bookToManageCollections = MutableStateFlow<Book?>(null)
    private val _selectedBookCollectionIds = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(
        combine(getBooksUseCase(), _searchQuery, _selectedCategory) { books, query, category ->
            Triple(books, query, category)
        },
        combine(getCollectionsUseCase(), _sortOrder, _viewMode) { collections, sort, viewMode ->
            Triple(collections, sort, viewMode)
        },
        combine(_isImporting, _importMessage, _bookToManageCollections, _selectedBookCollectionIds) { importing, message, managingBook, collectionIds ->
            listOf(importing, message, managingBook, collectionIds)
        }
    ) { (allBooks, query, category), (collections, sort, viewMode), extras ->
        val importing = extras[0] as Boolean
        val message = extras[1] as String?
        val managingBook = extras[2] as Book?
        val collectionIds = extras[3] as List<String>

        val continueBook = allBooks.filter { it.readingProgress > 0f && it.readingProgress < 1f }.maxByOrNull { it.lastReadTimestamp }

        var filtered = if (query.isNotBlank()) {
            allBooks.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.author.contains(query, ignoreCase = true)
            }
        } else {
            allBooks
        }

        filtered = when (category) {
            "Favorites" -> filtered.filter { it.isFavorite }
            "PDF" -> filtered.filter { it.category == "PDF" }
            "Reading" -> filtered.filter { it.readingProgress > 0f && it.readingProgress < 1f }
            "Recent" -> filtered.filter { it.lastReadTimestamp > 0 }
            else -> filtered
        }

        filtered = when (sort) {
            BookSortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            BookSortOrder.RECENTLY_OPENED -> filtered.sortedByDescending { it.lastReadTimestamp }
            BookSortOrder.DATE_ADDED -> filtered.sortedByDescending { it.addedTimestamp }
            BookSortOrder.READING_PROGRESS -> filtered.sortedByDescending { it.readingProgress }
        }

        LibraryUiState(
            books = filtered,
            allCollections = collections,
            continueReadingBook = continueBook,
            searchQuery = query,
            selectedCategory = category,
            sortOrder = sort,
            viewMode = viewMode,
            isImporting = importing,
            importMessage = message,
            bookToManageCollections = managingBook,
            selectedBookCollectionIds = collectionIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    init {
        viewModelScope.launch { preferencesRepository.preferencesFlow.collect { prefs ->
            _viewMode.value = if (prefs.libraryGrid) LibraryViewMode.GRID else LibraryViewMode.LIST
            _sortOrder.value = prefs.librarySort
        } }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun onSortOrderChanged(sortOrder: BookSortOrder) {
        _sortOrder.value = sortOrder
        viewModelScope.launch { preferencesRepository.savePreferences(preferencesRepository.preferencesFlow.first().copy(librarySort = sortOrder)) }
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
        viewModelScope.launch { preferencesRepository.savePreferences(preferencesRepository.preferencesFlow.first().copy(libraryGrid = _viewMode.value == LibraryViewMode.GRID)) }
    }

    fun toggleFavorite(book: Book, isFavorite: Boolean) {
        viewModelScope.launch {
            bookRepository.toggleFavorite(book.id, isFavorite)
        }
    }

    // Deletion Option 1: Remove from library only (preserves original file)
    fun removeBookFromLibrary(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBookFromLibrary(bookId)
            _importMessage.value = "Removed book from library (file preserved on device)"
        }
    }

    fun deleteBook(bookId: String) = removeBookFromLibrary(bookId)

    // Deletion Option 2: Delete from device permanently
    fun deleteBookAndDeviceFile(bookId: String) {
        viewModelScope.launch {
            val fileDeleted = bookRepository.deleteBookAndDeviceFile(bookId)
            _importMessage.value = if (fileDeleted) {
                "Book and device file permanently deleted"
            } else {
                "Removed book from library"
            }
        }
    }

    // Import Single or Multiple PDFs via SAF
    fun importPdfFromUri(uri: Uri) {
        importMultiplePdfs(listOf(uri))
    }

    fun importMultiplePdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            val summary = bookRepository.importMultiplePdfs(uris)
            _isImporting.value = false

            _importMessage.value = when {
                summary.importedCount > 0 && summary.duplicateCount > 0 ->
                    "Imported ${summary.importedCount} PDF(s). Skipped ${summary.duplicateCount} duplicate(s)."
                summary.importedCount > 0 ->
                    "Successfully imported ${summary.importedCount} PDF(s)."
                summary.duplicateCount > 0 ->
                    "All ${summary.duplicateCount} selected PDF(s) are already in your library."
                else ->
                    "Failed to import selected files."
            }
        }
    }

    // Manage Collections for a Book
    fun openManageCollectionsDialog(book: Book) {
        _bookToManageCollections.value = book
        viewModelScope.launch {
            _selectedBookCollectionIds.value = bookRepository.getCollectionIdsForBook(book.id).first()
        }
    }

    fun closeManageCollectionsDialog() {
        _bookToManageCollections.value = null
    }

    fun toggleBookCollection(collectionId: String) {
        val current = _selectedBookCollectionIds.value.toMutableList()
        if (current.contains(collectionId)) {
            current.remove(collectionId)
        } else {
            current.add(collectionId)
        }
        _selectedBookCollectionIds.value = current
    }

    fun saveBookCollections() {
        val book = _bookToManageCollections.value ?: return
        viewModelScope.launch {
            bookRepository.setBookCollections(book.id, _selectedBookCollectionIds.value)
            _bookToManageCollections.value = null
            _importMessage.value = "Updated collections for \"${book.title}\""
        }
    }

    fun setCollections(book: Book, ids: List<String>) {
        viewModelScope.launch {
            bookRepository.setBookCollections(book.id, ids)
            closeManageCollectionsDialog()
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }

    class Factory(
        private val getBooksUseCase: GetBooksUseCase,
        private val getCollectionsUseCase: GetCollectionsUseCase,
        private val bookRepository: BookRepository,
        private val collectionRepository: CollectionRepository,
        private val preferencesRepository: com.bookflow.app.domain.repository.PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(getBooksUseCase, getCollectionsUseCase, bookRepository, collectionRepository, preferencesRepository) as T
        }
    }
}
