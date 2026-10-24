package com.bookflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.domain.model.Bookmark

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["uriString"]),
        Index(value = ["title", "fileSizeBytes"])
    ]
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val uriString: String?,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val currentPage: Int,
    val readingProgress: Float,
    val isFavorite: Boolean,
    val category: String,
    val collectionId: String?,
    val thumbnailPath: String? = null,
    val coverColorHex: String,
    val lastReadTimestamp: Long,
    val addedTimestamp: Long
) {
    fun toDomain(collectionIds: List<String> = emptyList()): Book = Book(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        uriString = uriString,
        fileSizeBytes = fileSizeBytes,
        pageCount = pageCount,
        currentPage = currentPage,
        readingProgress = readingProgress,
        isFavorite = isFavorite,
        category = category,
        collectionId = collectionId,
        collectionIds = collectionIds,
        thumbnailPath = thumbnailPath,
        coverColorHex = coverColorHex,
        lastReadTimestamp = lastReadTimestamp,
        addedTimestamp = addedTimestamp
    )

    companion object {
        fun fromDomain(book: Book): BookEntity = BookEntity(
            id = book.id,
            title = book.title,
            author = book.author,
            filePath = book.filePath,
            uriString = book.uriString,
            fileSizeBytes = book.fileSizeBytes,
            pageCount = book.pageCount,
            currentPage = book.currentPage,
            readingProgress = book.readingProgress,
            isFavorite = book.isFavorite,
            category = book.category,
            collectionId = book.collectionId,
            thumbnailPath = book.thumbnailPath,
            coverColorHex = book.coverColorHex,
            lastReadTimestamp = book.lastReadTimestamp,
            addedTimestamp = book.addedTimestamp
        )
    }
}

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val pastelColorHex: String,
    val iconName: String,
    val createdAt: Long
) {
    fun toDomain(bookCount: Int = 0): BookCollection = BookCollection(
        id = id,
        name = name,
        description = description,
        pastelColorHex = pastelColorHex,
        iconName = iconName,
        bookCount = bookCount,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(col: BookCollection): CollectionEntity = CollectionEntity(
            id = col.id,
            name = col.name,
            description = col.description,
            pastelColorHex = col.pastelColorHex,
            iconName = col.iconName,
            createdAt = col.createdAt
        )
    }
}

/**
 * Junction table establishing Many-to-Many relationship between Books and Collections.
 * A book can belong to multiple collections, and a collection has multiple books.
 */
@Entity(
    tableName = "book_collection_cross_ref",
    primaryKeys = ["bookId", "collectionId"],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["collectionId"])
    ]
)
data class BookCollectionCrossRef(
    val bookId: String,
    val collectionId: String
)

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageIndex: Int,
    val type: String,
    val colorHex: String,
    val selectedText: String,
    val noteContent: String,
    val rectLeft: Float,
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float,
    val strokePathData: String?,
    val createdAt: Long,
    val updatedAt: Long = createdAt
) {
    fun toDomain(): BookAnnotation = BookAnnotation(
        id = id,
        bookId = bookId,
        pageIndex = pageIndex,
        type = try { AnnotationType.valueOf(type) } catch (_: Exception) { AnnotationType.HIGHLIGHT },
        colorHex = colorHex,
        selectedText = selectedText,
        noteContent = noteContent,
        rectLeft = rectLeft,
        rectTop = rectTop,
        rectRight = rectRight,
        rectBottom = rectBottom,
        strokePathData = strokePathData,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(ann: BookAnnotation): AnnotationEntity = AnnotationEntity(
            id = ann.id,
            bookId = ann.bookId,
            pageIndex = ann.pageIndex,
            type = ann.type.name,
            colorHex = ann.colorHex,
            selectedText = ann.selectedText,
            noteContent = ann.noteContent,
            rectLeft = ann.rectLeft,
            rectTop = ann.rectTop,
            rectRight = ann.rectRight,
            rectBottom = ann.rectBottom,
            strokePathData = ann.strokePathData,
            createdAt = ann.createdAt,
            updatedAt = ann.updatedAt
        )
    }
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pageIndex: Int,
    val label: String,
    val createdAt: Long
) {
    fun toDomain(): Bookmark = Bookmark(
        id = id,
        bookId = bookId,
        pageIndex = pageIndex,
        label = label,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(bm: Bookmark): BookmarkEntity = BookmarkEntity(
            id = bm.id,
            bookId = bm.bookId,
            pageIndex = bm.pageIndex,
            label = bm.label,
            createdAt = bm.createdAt
        )
    }
}
