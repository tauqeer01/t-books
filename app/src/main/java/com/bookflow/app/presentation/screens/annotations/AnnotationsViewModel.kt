package com.bookflow.app.presentation.screens.annotations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.domain.repository.AnnotationRepository
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.usecase.DeleteAnnotationUseCase
import com.bookflow.app.domain.usecase.GetAnnotationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AnnotationsUiState(
    val annotations: List<BookAnnotation> = emptyList(),
    val booksMap: Map<String, Book> = emptyMap(),
    val selectedFilterType: AnnotationType? = null,
    val selectedColorHex: String? = null,
    val searchQuery: String = ""
)

class AnnotationsViewModel(
    private val getAnnotationsUseCase: GetAnnotationsUseCase,
    private val deleteAnnotationUseCase: DeleteAnnotationUseCase,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _selectedFilterType = MutableStateFlow<AnnotationType?>(null)
    private val _selectedColorHex = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<AnnotationsUiState> = combine(
        getAnnotationsUseCase.all(),
        bookRepository.getAllBooks(),
        _selectedFilterType,
        _selectedColorHex,
        _searchQuery
    ) { allAnns, books, filterType, colorHex, query ->
        val bookMap = books.associateBy { it.id }

        var filtered = allAnns
        if (filterType != null) {
            filtered = filtered.filter { it.type == filterType }
        }
        if (colorHex != null) {
            filtered = filtered.filter { it.colorHex.equals(colorHex, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.selectedText.contains(query, ignoreCase = true) ||
                        it.noteContent.contains(query, ignoreCase = true) ||
                        (bookMap[it.bookId]?.title?.contains(query, ignoreCase = true) == true)
            }
        }

        AnnotationsUiState(
            annotations = filtered,
            booksMap = bookMap,
            selectedFilterType = filterType,
            selectedColorHex = colorHex,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnnotationsUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectFilterType(type: AnnotationType?) {
        _selectedFilterType.value = type
    }

    fun selectColorFilter(colorHex: String?) {
        _selectedColorHex.value = colorHex
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch {
            deleteAnnotationUseCase(id)
        }
    }

    class Factory(
        private val getAnnotationsUseCase: GetAnnotationsUseCase,
        private val deleteAnnotationUseCase: DeleteAnnotationUseCase,
        private val bookRepository: BookRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnnotationsViewModel(getAnnotationsUseCase, deleteAnnotationUseCase, bookRepository) as T
        }
    }
}
