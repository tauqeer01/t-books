package com.bookflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bookflow.app.data.local.entity.AnnotationEntity
import com.bookflow.app.data.local.entity.BookCollectionCrossRef
import com.bookflow.app.data.local.entity.BookEntity
import com.bookflow.app.data.local.entity.BookmarkEntity
import com.bookflow.app.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun getBookById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookByIdDirect(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE uriString = :uriString LIMIT 1")
    suspend fun getBookByUri(uriString: String): BookEntity?

    @Query("SELECT * FROM books WHERE title = :title AND fileSizeBytes = :fileSizeBytes LIMIT 1")
    suspend fun findDuplicate(title: String, fileSizeBytes: Long): BookEntity?

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadTimestamp DESC")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Query("UPDATE books SET currentPage = :currentPage, readingProgress = :progress, lastReadTimestamp = :timestamp WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: String, currentPage: Int, progress: Float, timestamp: Long)

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun updateFavorite(bookId: String, isFavorite: Boolean)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    @Query("SELECT * FROM book_collection_cross_ref")
    fun getAllCrossRefs(): Flow<List<BookCollectionCrossRef>>

    @Transaction
    suspend fun replaceCollections(bookId: String, collectionIds: List<String>) {
        clearCollectionsForBook(bookId)
        insertAllCrossRefs(collectionIds.distinct().map { BookCollectionCrossRef(bookId, it) })
    }

    // Cross-ref (Many-to-Many with Collections)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: BookCollectionCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllCrossRefs(crossRefs: List<BookCollectionCrossRef>)

    @Query("DELETE FROM book_collection_cross_ref WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun deleteCrossRef(bookId: String, collectionId: String)

    @Query("DELETE FROM book_collection_cross_ref WHERE bookId = :bookId")
    suspend fun clearCollectionsForBook(bookId: String)

    @Query("SELECT collectionId FROM book_collection_cross_ref WHERE bookId = :bookId")
    fun getCollectionIdsForBook(bookId: String): Flow<List<String>>

    @Query("""
        SELECT b.* FROM books b
        INNER JOIN book_collection_cross_ref ref ON b.id = ref.bookId
        WHERE ref.collectionId = :collectionId
        ORDER BY b.lastReadTimestamp DESC
    """)
    fun getBooksForCollection(collectionId: String): Flow<List<BookEntity>>
}

@Dao
interface CollectionDao {
    @Transaction
    suspend fun removeCollection(id: String) {
        deleteCrossRefsForCollection(id)
        deleteCollectionById(id)
    }

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    fun getCollectionById(id: String): Flow<CollectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(collections: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionById(id: String)

    @Query("DELETE FROM book_collection_cross_ref WHERE collectionId = :collectionId")
    suspend fun deleteCrossRefsForCollection(collectionId: String)

    @Query("SELECT COUNT(*) FROM book_collection_cross_ref WHERE collectionId = :collectionId")
    fun getBookCountForCollection(collectionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun getCollectionCount(): Int
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY pageIndex ASC, createdAt ASC")
    fun getAnnotationsForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND pageIndex = :pageIndex ORDER BY createdAt ASC")
    fun getAnnotationsForPage(bookId: String, pageIndex: Int): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY createdAt DESC")
    fun getAllAnnotations(): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(annotations: List<AnnotationEntity>)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: String)

    @Query("DELETE FROM annotations WHERE bookId = :bookId")
    suspend fun deleteAnnotationsForBook(bookId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY pageIndex ASC")
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getBookmark(bookId: String, pageIndex: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND pageIndex = :pageIndex")
    suspend fun deleteBookmark(bookId: String, pageIndex: Int)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksForBook(bookId: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)
}
