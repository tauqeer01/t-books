package com.bookflow.app.domain.model

enum class ReaderTheme(val displayName: String, val bgHex: String, val inkHex: String) {
    LIGHT("Classic Light", "#FFFFFF", "#1E1E24"),
    SEPIA("Warm Paper", "#F5EFE1", "#4A3E31"),
    DARK("Night OLED", "#121214", "#EDEDF0"),
    MINT("Pastel Mint", "#EAF4EE", "#1F3B29")
}

enum class PageScrollMode(val displayName: String) {
    HORIZONTAL_PAGING("Horizontal Flip"),
    CONTINUOUS_VERTICAL("Continuous Scroll")
}

enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    NOTE,
    PEN_DRAW
}

enum class BookSortOrder(val displayName: String) {
    TITLE("Title (A-Z)"),
    RECENTLY_OPENED("Recently Opened"),
    DATE_ADDED("Date Added"),
    READING_PROGRESS("Reading Progress")
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val uriString: String? = null,
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val currentPage: Int = 0,
    val readingProgress: Float = 0f,
    val isFavorite: Boolean = false,
    val category: String = "All",
    val collectionId: String? = null,
    val collectionIds: List<String> = emptyList(),
    val thumbnailPath: String? = null,
    val coverColorHex: String = "#4F46E5",
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val addedTimestamp: Long = System.currentTimeMillis()
)

data class BookCollection(
    val id: String,
    val name: String,
    val description: String,
    val pastelColorHex: String,
    val iconName: String = "folder",
    val bookCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class BookAnnotation(
    val id: String,
    val bookId: String,
    val pageIndex: Int,
    val type: AnnotationType = AnnotationType.HIGHLIGHT,
    val colorHex: String = "#FFE066",
    val selectedText: String = "",
    val noteContent: String = "",
    val rectLeft: Float = 0f,
    val rectTop: Float = 0f,
    val rectRight: Float = 0f,
    val rectBottom: Float = 0f,
    val strokePathData: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Bookmark(
    val id: String,
    val bookId: String,
    val pageIndex: Int,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class UserReadingPreferences(
    val readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    val scrollMode: PageScrollMode = PageScrollMode.HORIZONTAL_PAGING,
    val highResolutionRendering: Boolean = true,
    val keepScreenOn: Boolean = true,
    val defaultEngine: String = "Android Native PdfRenderer"
)
