package com.bookflow.app.domain.repository

import android.net.Uri
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.model.Bookmark
import com.bookflow.app.domain.model.UserReadingPreferences
import kotlinx.coroutines.flow.Flow

data class BatchImportSummary(
    val importedCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
    val importedBooks: List<Book> = emptyList()
)

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getBookById(id: String): Flow<Book?>
    fun getBooksByCollection(collectionId: String): Flow<List<Book>>
    fun getFavoriteBooks(): Flow<List<Book>>
    fun getRecentlyOpenedBooks(limit: Int = 10): Flow<List<Book>>
    suspend fun insertOrUpdateBook(book: Book)
    suspend fun updateReadingProgress(bookId: String, currentPage: Int, totalPages: Int)
    suspend fun toggleFavorite(bookId: String, isFavorite: Boolean)

    // Document Management (Phase 2)
    suspend fun importPdf(uri: Uri): Result<Book>
    suspend fun importMultiplePdfs(uris: List<Uri>): BatchImportSummary
    suspend fun deleteBookFromLibrary(bookId: String)
    suspend fun deleteBookAndDeviceFile(bookId: String): Boolean

    // Collections (Many-to-Many)
    suspend fun addBookToCollection(bookId: String, collectionId: String)
    suspend fun removeBookFromCollection(bookId: String, collectionId: String)
    suspend fun setBookCollections(bookId: String, collectionIds: List<String>)
    fun getCollectionIdsForBook(bookId: String): Flow<List<String>>

    suspend fun preloadSampleBooks()
}

interface CollectionRepository {
    fun getAllCollections(): Flow<List<BookCollection>>
    fun getCollectionById(id: String): Flow<BookCollection?>
    suspend fun insertCollection(collection: BookCollection)
    suspend fun deleteCollection(id: String)
    suspend fun preloadDefaultCollections()
}

interface AnnotationRepository {
    fun getAnnotationsForBook(bookId: String): Flow<List<BookAnnotation>>
    fun getAnnotationsForPage(bookId: String, pageIndex: Int): Flow<List<BookAnnotation>>
    fun getAllAnnotations(): Flow<List<BookAnnotation>>
    suspend fun insertAnnotation(annotation: BookAnnotation)
    suspend fun deleteAnnotation(id: String)

    fun getBookmarksForBook(bookId: String): Flow<List<Bookmark>>
    suspend fun toggleBookmark(bookId: String, pageIndex: Int, label: String = "")
    suspend fun isPageBookmarked(bookId: String, pageIndex: Int): Boolean
    suspend fun deleteBookmarkById(id: String)
}

interface PreferencesRepository {
    val preferencesFlow: Flow<UserReadingPreferences>
    fun preferencesForBook(bookId: String): Flow<UserReadingPreferences>
    suspend fun savePreferences(preferences: UserReadingPreferences, bookId: String? = null)
    suspend fun clearBookPreferences(bookId: String)
    suspend fun updateTheme(theme: com.bookflow.app.domain.model.ReaderTheme)
    suspend fun updateScrollMode(mode: com.bookflow.app.domain.model.PageScrollMode)
    suspend fun updateHighResRendering(enabled: Boolean)
    suspend fun updateKeepScreenOn(enabled: Boolean)
    val appThemeFlow: Flow<com.bookflow.app.domain.model.AppThemeMode>
    suspend fun setAppTheme(mode: com.bookflow.app.domain.model.AppThemeMode)
}

/** Time spent reading per day and the daily reading goal. */
interface ReadingStatsRepository {
    val stats: Flow<com.bookflow.app.domain.model.ReadingStats>

    /** Adds reading time to today. Fire-and-forget so it still lands when the reader is closing. */
    fun addReadingTime(seconds: Long)

    suspend fun setDailyGoal(minutes: Int)
}

/** The PDF is already in the library; [existingBookId] identifies that copy. */
class DuplicateBookException(val existingBookId: String, message: String) : IllegalStateException(message)
