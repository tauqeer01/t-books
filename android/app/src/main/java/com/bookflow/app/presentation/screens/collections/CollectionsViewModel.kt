package com.bookflow.app.presentation.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.repository.CollectionRepository
import com.bookflow.app.domain.usecase.GetCollectionsUseCase
import com.bookflow.app.domain.usecase.SaveCollectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CollectionsUiState(
    val allBooks: List<Book> = emptyList(),
    val collections: List<BookCollection> = emptyList(),
    val selectedCollection: BookCollection? = null,
    val collectionBooks: List<Book> = emptyList(),
    val isCreateDialogOpen: Boolean = false,
    val searchQuery: String = ""
)

class CollectionsViewModel(
    private val getCollectionsUseCase: GetCollectionsUseCase,
    private val saveCollectionUseCase: SaveCollectionUseCase,
    private val collectionRepository: CollectionRepository,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _selectedCollection = MutableStateFlow<BookCollection?>(null)
    private val _isCreateDialogOpen = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<CollectionsUiState> = combine(
        getCollectionsUseCase(),
        bookRepository.getAllBooks(),
        _selectedCollection,
        _isCreateDialogOpen,
        _searchQuery
    ) { collections, allBooks, selected, isCreateOpen, query ->
        // Calculate book count dynamically
        val collectionsWithCounts = collections.map { col ->
            val count = allBooks.count { col.id in it.collectionIds }
            col.copy(bookCount = count)
        }

        val filteredCollections = if (query.isNotBlank()) {
            collectionsWithCounts.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        } else {
            collectionsWithCounts
        }

        val booksForSelected = if (selected != null) {
            allBooks.filter { selected.id in it.collectionIds }
        } else {
            emptyList()
        }

        CollectionsUiState(
            allBooks = allBooks,
            collections = filteredCollections,
            selectedCollection = selected,
            collectionBooks = booksForSelected,
            isCreateDialogOpen = isCreateOpen,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionsUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectCollection(collection: BookCollection?) {
        _selectedCollection.value = collection
    }

    fun openCreateDialog() {
        _isCreateDialogOpen.value = true
    }

    fun closeCreateDialog() {
        _isCreateDialogOpen.value = false
    }

    fun createCollection(name: String, description: String, pastelColorHex: String, iconName: String) {
        viewModelScope.launch {
            val newCol = BookCollection(
                id = "col_${UUID.randomUUID()}",
                name = name,
                description = description,
                pastelColorHex = pastelColorHex,
                iconName = iconName,
                bookCount = 0,
                createdAt = System.currentTimeMillis()
            )
            saveCollectionUseCase(newCol)
            _isCreateDialogOpen.value = false
        }
    }

    fun toggleFavorite(book: Book) { viewModelScope.launch { bookRepository.toggleFavorite(book.id, !book.isFavorite) } }
    fun removeBook(book: Book) { viewModelScope.launch { bookRepository.deleteBookFromLibrary(book.id) } }
    fun setCollections(book: Book, ids: List<String>) { viewModelScope.launch { bookRepository.setBookCollections(book.id, ids) } }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            collectionRepository.deleteCollection(collectionId)
            if (_selectedCollection.value?.id == collectionId) {
                _selectedCollection.value = null
            }
        }
    }

    class Factory(
        private val getCollectionsUseCase: GetCollectionsUseCase,
        private val saveCollectionUseCase: SaveCollectionUseCase,
        private val collectionRepository: CollectionRepository,
        private val bookRepository: BookRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CollectionsViewModel(getCollectionsUseCase, saveCollectionUseCase, collectionRepository, bookRepository) as T
        }
    }
}
