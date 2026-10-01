package com.bookflow.app.domain.usecase

import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.model.Bookmark
import com.bookflow.app.domain.repository.AnnotationRepository
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow

class GetBooksUseCase(private val bookRepository: BookRepository) {
    operator fun invoke(): Flow<List<Book>> = bookRepository.getAllBooks()
}

class GetBookByIdUseCase(private val bookRepository: BookRepository) {
    operator fun invoke(id: String): Flow<Book?> = bookRepository.getBookById(id)
}

class SaveBookUseCase(private val bookRepository: BookRepository) {
    suspend operator fun invoke(book: Book) = bookRepository.insertOrUpdateBook(book)
}

class UpdateProgressUseCase(private val bookRepository: BookRepository) {
    suspend operator fun invoke(bookId: String, currentPage: Int, totalPages: Int) =
        bookRepository.updateReadingProgress(bookId, currentPage, totalPages)
}

class GetCollectionsUseCase(private val collectionRepository: CollectionRepository) {
    operator fun invoke(): Flow<List<BookCollection>> = collectionRepository.getAllCollections()
}

class SaveCollectionUseCase(private val collectionRepository: CollectionRepository) {
    suspend operator fun invoke(collection: BookCollection) =
        collectionRepository.insertCollection(collection)
}

class GetAnnotationsUseCase(private val annotationRepository: AnnotationRepository) {
    fun forBook(bookId: String): Flow<List<BookAnnotation>> =
        annotationRepository.getAnnotationsForBook(bookId)

    fun forPage(bookId: String, pageIndex: Int): Flow<List<BookAnnotation>> =
        annotationRepository.getAnnotationsForPage(bookId, pageIndex)

    fun all(): Flow<List<BookAnnotation>> =
        annotationRepository.getAllAnnotations()
}

class SaveAnnotationUseCase(private val annotationRepository: AnnotationRepository) {
    suspend operator fun invoke(annotation: BookAnnotation) =
        annotationRepository.insertAnnotation(annotation)
}

class DeleteAnnotationUseCase(private val annotationRepository: AnnotationRepository) {
    suspend operator fun invoke(id: String) =
        annotationRepository.deleteAnnotation(id)
}

class BookmarkUseCase(private val annotationRepository: AnnotationRepository) {
    fun forBook(bookId: String): Flow<List<Bookmark>> =
        annotationRepository.getBookmarksForBook(bookId)

    suspend fun toggle(bookId: String, pageIndex: Int, label: String = "") =
        annotationRepository.toggleBookmark(bookId, pageIndex, label)

    suspend fun isBookmarked(bookId: String, pageIndex: Int): Boolean =
        annotationRepository.isPageBookmarked(bookId, pageIndex)
}
