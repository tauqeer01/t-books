package com.bookflow.app.data.repository

import com.bookflow.app.data.local.dao.AnnotationDao
import com.bookflow.app.data.local.dao.BookmarkDao
import com.bookflow.app.data.local.entity.AnnotationEntity
import com.bookflow.app.data.local.entity.BookmarkEntity
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.domain.model.Bookmark
import com.bookflow.app.domain.repository.AnnotationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AnnotationRepositoryImpl(
    private val annotationDao: AnnotationDao,
    private val bookmarkDao: BookmarkDao
) : AnnotationRepository {

    override fun getAnnotationsForBook(bookId: String): Flow<List<BookAnnotation>> {
        return annotationDao.getAnnotationsForBook(bookId).map { list -> list.map { it.toDomain() } }
    }

    override fun getAnnotationsForPage(bookId: String, pageIndex: Int): Flow<List<BookAnnotation>> {
        return annotationDao.getAnnotationsForPage(bookId, pageIndex).map { list -> list.map { it.toDomain() } }
    }

    override fun getAllAnnotations(): Flow<List<BookAnnotation>> {
        return annotationDao.getAllAnnotations().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun insertAnnotation(annotation: BookAnnotation) = withContext(Dispatchers.IO) {
        annotationDao.insertAnnotation(AnnotationEntity.fromDomain(annotation))
    }

    override suspend fun deleteAnnotation(id: String) = withContext(Dispatchers.IO) {
        annotationDao.deleteAnnotationById(id)
    }

    override fun getBookmarksForBook(bookId: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForBook(bookId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun toggleBookmark(bookId: String, pageIndex: Int, label: String) = withContext(Dispatchers.IO) {
        val existing = bookmarkDao.getBookmark(bookId, pageIndex)
        if (existing != null) {
            bookmarkDao.deleteBookmark(bookId, pageIndex)
        } else {
            val bm = BookmarkEntity(
                id = "bm_${bookId}_$pageIndex",
                bookId = bookId,
                pageIndex = pageIndex,
                label = if (label.isNotBlank()) label else "Page ${pageIndex + 1}",
                createdAt = System.currentTimeMillis()
            )
            bookmarkDao.insertBookmark(bm)
        }
    }

    override suspend fun isPageBookmarked(bookId: String, pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        bookmarkDao.getBookmark(bookId, pageIndex) != null
    }

    override suspend fun deleteBookmarkById(id: String) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmarkById(id)
    }
}
