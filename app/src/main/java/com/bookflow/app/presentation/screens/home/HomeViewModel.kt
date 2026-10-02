package com.bookflow.app.presentation.screens.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.core.util.FileUtils
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.repository.CollectionRepository
import com.bookflow.app.domain.usecase.GetBooksUseCase
import com.bookflow.app.domain.usecase.GetCollectionsUseCase
import com.bookflow.app.domain.usecase.SaveBookUseCase
import com.bookflow.app.pdf.engine.PdfEngineFactory
import com.bookflow.app.pdf.engine.PdfSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeUiState(
    val allBooks: List<Book> = emptyList(),
    val continueReadingBooks: List<Book> = emptyList(),
    val recentlyOpenedBooks: List<Book> = emptyList(),
    val myLibraryBooks: List<Book> = emptyList(),
    val collections: List<BookCollection> = emptyList(),
    val allAnnotations: List<com.bookflow.app.domain.model.BookAnnotation> = emptyList(),
    val selectedFilterPill: String = "All",
    val filterPills: List<String> = listOf("All", "PDF", "Recent", "Favorites", "Collections"),
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val toastMessage: String? = null
)

class HomeViewModel(
    private val context: Context,
    private val getBooksUseCase: GetBooksUseCase,
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val saveBookUseCase: SaveBookUseCase,
    private val bookRepository: BookRepository,
    private val annotationRepository: com.bookflow.app.domain.repository.AnnotationRepository,
    private val pdfEngineFactory: PdfEngineFactory
) : ViewModel() {

    private val _selectedFilterPill = MutableStateFlow("All")
    private val _searchQuery = MutableStateFlow("")
    private val _isImporting = MutableStateFlow(false)
    private val _toastMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        getBooksUseCase(),
        getCollectionsUseCase(),
        annotationRepository.getAllAnnotations(),
        _selectedFilterPill,
        _searchQuery,
        _isImporting,
        _toastMessage
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val allBooks = params[0] as List<Book>
        @Suppress("UNCHECKED_CAST")
        val collections = params[1] as List<BookCollection>
        @Suppress("UNCHECKED_CAST")
        val allAnnotations = params[2] as List<com.bookflow.app.domain.model.BookAnnotation>
        val pill = params[3] as String
        val query = params[4] as String
        val importing = params[5] as Boolean

        // Continue Reading: Books with progress > 0
        val continueReading = allBooks.filter { it.readingProgress > 0f && it.readingProgress < 1f }.sortedByDescending { it.lastReadTimestamp }

        // Recently Opened: Next batch of active books
        val recentlyOpened = allBooks.filter { it.lastReadTimestamp > 0 }.sortedByDescending { it.lastReadTimestamp }

        // My Library: filtered by search or pill
        var library = allBooks
        if (pill == "Favorites") library = library.filter { it.isFavorite }
        if (pill == "PDF") library = library.filter { it.category == "PDF" }
        if (pill == "Recent") library = library.filter { it.lastReadTimestamp > 0 }
        if (query.isNotBlank()) {
            // Pre-compute annotation content map to avoid O(n²) scan
            val annotationTextByBook = allAnnotations.groupBy(
                keySelector = { it.bookId },
                valueTransform = { "${it.selectedText} ${it.noteContent}" }
            )
            library = library.filter {
                it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) ||
                    annotationTextByBook[it.id]?.any { text -> text.contains(query, true) } == true
            }
        }

        HomeUiState(
            allBooks = allBooks,
            continueReadingBooks = continueReading,
            recentlyOpenedBooks = recentlyOpened,
            myLibraryBooks = library,
            collections = collections.map { collection -> collection.copy(bookCount = allBooks.count { collection.id in it.collectionIds }) },
            allAnnotations = allAnnotations,
            selectedFilterPill = pill,
            searchQuery = query,
            isImporting = importing,
            toastMessage = params[6] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onFilterPillSelected(pill: String) {
        _selectedFilterPill.value = pill
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun importPdfFromUri(uri: Uri) {
        importMultiplePdfs(listOf(uri))
    }

    fun importMultiplePdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            val summary = bookRepository.importMultiplePdfs(uris)
            _isImporting.value = false
            _toastMessage.value = when {
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

    fun toggleFavorite(book: Book) { viewModelScope.launch { bookRepository.toggleFavorite(book.id, !book.isFavorite) } }
    fun removeBook(book: Book) { viewModelScope.launch { bookRepository.deleteBookFromLibrary(book.id) } }
    fun setCollections(book: Book, ids: List<String>) { viewModelScope.launch { bookRepository.setBookCollections(book.id, ids) } }

    fun clearToast() {
        _toastMessage.value = null
    }

    class Factory(
        private val context: Context,
        private val getBooksUseCase: GetBooksUseCase,
        private val getCollectionsUseCase: GetCollectionsUseCase,
        private val saveBookUseCase: SaveBookUseCase,
        private val bookRepository: BookRepository,
        private val annotationRepository: com.bookflow.app.domain.repository.AnnotationRepository,
        private val pdfEngineFactory: PdfEngineFactory
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(context, getBooksUseCase, getCollectionsUseCase, saveBookUseCase, bookRepository, annotationRepository, pdfEngineFactory) as T
        }
    }
}
