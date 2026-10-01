package com.bookflow.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bookflow.app.core.util.FileUtils
import com.bookflow.app.data.local.dao.AnnotationDao
import com.bookflow.app.data.local.dao.BookDao
import com.bookflow.app.data.local.dao.BookmarkDao
import com.bookflow.app.data.local.dao.CollectionDao
import com.bookflow.app.data.local.entity.AnnotationEntity
import com.bookflow.app.data.local.entity.BookCollectionCrossRef
import com.bookflow.app.data.local.entity.BookEntity
import com.bookflow.app.data.local.entity.BookmarkEntity
import com.bookflow.app.data.local.entity.CollectionEntity
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.repository.BatchImportSummary
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.pdf.engine.PdfEngineFactory
import com.bookflow.app.pdf.engine.PdfSource
import com.bookflow.app.pdf.engine.RenderQuality
import com.bookflow.app.pdf.generator.SamplePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BookRepositoryImpl(
    private val context: Context,
    private val bookDao: BookDao,
    private val collectionDao: CollectionDao,
    private val annotationDao: AnnotationDao,
    private val bookmarkDao: BookmarkDao,
    private val pdfEngineFactory: PdfEngineFactory
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getBookById(id: String): Flow<Book?> {
        return bookDao.getBookById(id).map { it?.toDomain() }
    }

    override fun getBooksByCollection(collectionId: String): Flow<List<Book>> {
        return bookDao.getBooksForCollection(collectionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getFavoriteBooks(): Flow<List<Book>> {
        return bookDao.getFavoriteBooks().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getRecentlyOpenedBooks(limit: Int): Flow<List<Book>> {
        return bookDao.getAllBooks().map { list ->
            list.sortedByDescending { it.lastReadTimestamp }.take(limit).map { it.toDomain() }
        }
    }

    override suspend fun insertOrUpdateBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.insertOrUpdateBook(BookEntity.fromDomain(book))
    }

    override suspend fun updateReadingProgress(bookId: String, currentPage: Int, totalPages: Int) = withContext(Dispatchers.IO) {
        val progress = if (totalPages > 0) ((currentPage + 1).toFloat() / totalPages).coerceIn(0f, 1f) else 0f
        bookDao.updateReadingProgress(bookId, currentPage, progress, System.currentTimeMillis())
    }

    override suspend fun toggleFavorite(bookId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        bookDao.updateFavorite(bookId, isFavorite)
    }

    // --- PHASE 2: DOCUMENT IMPORT & SAF PERSISTENCE ---

    override suspend fun importPdf(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        try {
            // 1. Persist URI permission so the app can access it across restarts without copying the file!
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Ignore if permission cannot be persisted (e.g. temporary third party intent)
            }

            // 2. Extract Document Metadata
            val metadata = FileUtils.extractDocumentMetadata(context, uri)
            val uriStr = uri.toString()

            // 3. Detect duplicate imports
            val existingByUri = bookDao.getBookByUri(uriStr)
            if (existingByUri != null) {
                return@withContext Result.failure(IllegalStateException("Book \"${existingByUri.title}\" is already in your library."))
            }

            val existingByNameAndSize = bookDao.findDuplicate(metadata.fileName.removeSuffix(".pdf"), metadata.fileSizeBytes)
            if (existingByNameAndSize != null && metadata.fileSizeBytes > 0) {
                return@withContext Result.failure(IllegalStateException("Duplicate document \"${metadata.fileName}\" detected."))
            }

            val bookId = UUID.randomUUID().toString()
            val bookTitle = metadata.fileName.removeSuffix(".pdf").replace("_", " ").replace("-", " ")

            // 4. Inspect PDF without copying to local disk
            val engine = pdfEngineFactory.createEngine()
            val source = PdfSource.UriSource(uri, context)
            val openResult = engine.openDocument(source)
            if (openResult is com.bookflow.app.pdf.engine.PdfDocumentResult.Error) {
                engine.close()
                return@withContext Result.failure(IllegalStateException(openResult.message))
            }

            val pageCount = engine.getPageCount().coerceAtLeast(1)

            // 5. Generate lightweight first-page thumbnail
            val thumbBitmap = engine.renderPage(0, scale = 0.4f, quality = RenderQuality.FAST)
            val thumbnailPath = if (thumbBitmap != null) {
                FileUtils.saveThumbnail(context, bookId, thumbBitmap)
            } else null

            engine.close()

            // 6. Save in Room
            val bookEntity = BookEntity(
                id = bookId,
                title = bookTitle,
                author = "Imported Document",
                filePath = "", // Kept empty because we read in-place from SAF URI
                uriString = uriStr,
                fileSizeBytes = metadata.fileSizeBytes,
                pageCount = pageCount,
                currentPage = 0,
                readingProgress = 0f,
                isFavorite = false,
                category = "PDF",
                collectionId = null,
                thumbnailPath = thumbnailPath,
                coverColorHex = "#4F46E5",
                lastReadTimestamp = System.currentTimeMillis(),
                addedTimestamp = System.currentTimeMillis()
            )

            bookDao.insertOrUpdateBook(bookEntity)
            Result.success(bookEntity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importMultiplePdfs(uris: List<Uri>): BatchImportSummary = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        var failed = 0
        val importedBooks = mutableListOf<Book>()

        for (uri in uris) {
            val result = importPdf(uri)
            if (result.isSuccess) {
                imported++
                importedBooks.add(result.getOrThrow())
            } else {
                val error = result.exceptionOrNull()
                if (error is IllegalStateException && error.message?.contains("already", ignoreCase = true) == true) {
                    duplicates++
                } else {
                    failed++
                }
            }
        }

        BatchImportSummary(
            importedCount = imported,
            duplicateCount = duplicates,
            failedCount = failed,
            importedBooks = importedBooks
        )
    }

    // Delete from library without deleting the user's original file
    override suspend fun deleteBookFromLibrary(bookId: String) = withContext(Dispatchers.IO) {
        val entity = bookDao.getBookByIdDirect(bookId)
        if (entity != null) {
            FileUtils.deleteThumbnail(entity.thumbnailPath)
            bookDao.deleteBookById(bookId)
            bookDao.clearCollectionsForBook(bookId)
            annotationDao.deleteAnnotationsForBook(bookId)
        }
    }

    // Optional explicit action: Delete book from library AND delete original file from device
    override suspend fun deleteBookAndDeviceFile(bookId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = bookDao.getBookByIdDirect(bookId) ?: return@withContext false
        var fileDeleted = false

        if (!entity.uriString.isNullOrBlank()) {
            val uri = Uri.parse(entity.uriString)
            fileDeleted = FileUtils.deleteDocumentSaf(context, uri)
        } else if (entity.filePath.isNotBlank()) {
            val f = File(entity.filePath)
            if (f.exists()) {
                fileDeleted = f.delete()
            }
        }

        FileUtils.deleteThumbnail(entity.thumbnailPath)
        bookDao.deleteBookById(bookId)
        bookDao.clearCollectionsForBook(bookId)
        annotationDao.deleteAnnotationsForBook(bookId)

        fileDeleted
    }

    // --- MANY-TO-MANY COLLECTIONS ---

    override suspend fun addBookToCollection(bookId: String, collectionId: String) = withContext(Dispatchers.IO) {
        bookDao.insertCrossRef(BookCollectionCrossRef(bookId, collectionId))
    }

    override suspend fun removeBookFromCollection(bookId: String, collectionId: String) = withContext(Dispatchers.IO) {
        bookDao.deleteCrossRef(bookId, collectionId)
    }

    override suspend fun setBookCollections(bookId: String, collectionIds: List<String>) = withContext(Dispatchers.IO) {
        bookDao.clearCollectionsForBook(bookId)
        val refs = collectionIds.map { BookCollectionCrossRef(bookId, it) }
        bookDao.insertAllCrossRefs(refs)
    }

    override fun getCollectionIdsForBook(bookId: String): Flow<List<String>> {
        return bookDao.getCollectionIdsForBook(bookId)
    }

    // --- SAMPLE DATA PRELOAD ---

    override suspend fun preloadSampleBooks() = withContext(Dispatchers.IO) {
        val existingCount = bookDao.getBookCount()
        if (existingCount > 0) return@withContext

        val collections = listOf(
            CollectionEntity("col_study", "Study Material", "Textbooks & core engineering", "#EDE9FE", "menu_book", System.currentTimeMillis() - 86400000 * 6),
            CollectionEntity("col_aviation", "Aviation", "Flight systems & aerodynamics", "#DCFCE7", "flight", System.currentTimeMillis() - 86400000 * 5),
            CollectionEntity("col_favorites", "Favorites", "Essential reference guides", "#FFE4E6", "favorite", System.currentTimeMillis() - 86400000 * 4),
            CollectionEntity("col_medical", "Medical", "Human anatomy & microbiology", "#D1FAE5", "add_box", System.currentTimeMillis() - 86400000 * 3),
            CollectionEntity("col_exam", "Exam Preparation", "FAA test banks & formulas", "#FEF3C7", "school", System.currentTimeMillis() - 86400000 * 2),
            CollectionEntity("col_research", "Research Papers", "Aerospace research preprints", "#FFEDD5", "description", System.currentTimeMillis() - 86400000 * 1)
        )
        collectionDao.insertAll(collections)

        val pdfFile = SamplePdfGenerator.generateAircraftTextbook(context)

        // Generate thumbnail for sample book
        val engine = pdfEngineFactory.createEngine()
        engine.openDocument(PdfSource.FileSource(pdfFile))
        val sampleThumbBmp = engine.renderPage(0, scale = 0.4f, quality = RenderQuality.FAST)
        val sampleThumbPath = if (sampleThumbBmp != null) {
            FileUtils.saveThumbnail(context, "book_aircraft_systems", sampleThumbBmp)
        } else null
        engine.close()

        val books = listOf(
            BookEntity(
                id = "book_aircraft_systems",
                title = "Aircraft Systems",
                author = "Fundamentals • 5th Edition",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = pdfFile.length().coerceAtLeast(24500000),
                pageCount = 1245,
                currentPage = 125,
                readingProgress = 0.10f,
                isFavorite = true,
                category = "PDF",
                collectionId = "col_aviation",
                thumbnailPath = sampleThumbPath,
                coverColorHex = "#0C2340",
                lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 10,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 6
            ),
            BookEntity(
                id = "book_human_anatomy",
                title = "Human Anatomy",
                author = "For Students • Netter Edition",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 18400000,
                pageCount = 870,
                currentPage = 319,
                readingProgress = 0.37f,
                isFavorite = false,
                category = "PDF",
                collectionId = "col_medical",
                thumbnailPath = null,
                coverColorHex = "#991B1B",
                lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 5
            ),
            BookEntity(
                id = "book_thermodynamics",
                title = "Thermodynamics",
                author = "Engineering Approach",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 12600000,
                pageCount = 560,
                currentPage = 44,
                readingProgress = 0.08f,
                isFavorite = false,
                category = "PDF",
                collectionId = "col_study",
                thumbnailPath = null,
                coverColorHex = "#0369A1",
                lastReadTimestamp = System.currentTimeMillis() - 1000 * 60 * 90,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 4
            ),
            BookEntity(
                id = "book_physics",
                title = "Physics",
                author = "Quantum Principles",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 9400000,
                pageCount = 740,
                currentPage = 51,
                readingProgress = 0.07f,
                isFavorite = false,
                category = "EPUB",
                collectionId = "col_study",
                thumbnailPath = null,
                coverColorHex = "#4338CA",
                lastReadTimestamp = System.currentTimeMillis() - 86400000,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 3
            ),
            BookEntity(
                id = "book_microbiology",
                title = "Microbiology",
                author = "Pathogens & Immunology",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 15200000,
                pageCount = 620,
                currentPage = 13,
                readingProgress = 0.02f,
                isFavorite = false,
                category = "PDF",
                collectionId = "col_medical",
                thumbnailPath = null,
                coverColorHex = "#6D28D9",
                lastReadTimestamp = System.currentTimeMillis() - 86400000 * 2,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 3
            ),
            BookEntity(
                id = "book_organic_chemistry",
                title = "Organic Chemistry",
                author = "Structure & Synthesis",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 22100000,
                pageCount = 980,
                currentPage = 209,
                readingProgress = 0.21f,
                isFavorite = false,
                category = "PDF",
                collectionId = "col_study",
                thumbnailPath = null,
                coverColorHex = "#334155",
                lastReadTimestamp = System.currentTimeMillis() - 86400000 * 3,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 2
            ),
            BookEntity(
                id = "book_mathematics",
                title = "Mathematics",
                author = "Calculus & Analysis",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 14300000,
                pageCount = 850,
                currentPage = 127,
                readingProgress = 0.15f,
                isFavorite = false,
                category = "PDF",
                collectionId = "col_study",
                thumbnailPath = null,
                coverColorHex = "#1E293B",
                lastReadTimestamp = System.currentTimeMillis() - 86400000 * 4,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 2
            ),
            BookEntity(
                id = "book_aviation_fundamentals",
                title = "Aviation Fundamentals",
                author = "Principles of Flight",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 31200000,
                pageCount = 1245,
                currentPage = 0,
                readingProgress = 0.0f,
                isFavorite = true,
                category = "PDF",
                collectionId = "col_aviation",
                thumbnailPath = null,
                coverColorHex = "#EA580C",
                lastReadTimestamp = System.currentTimeMillis() - 86400000 * 5,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 1
            ),
            BookEntity(
                id = "book_medical_microbiology",
                title = "Medical Microbiology",
                author = "Infectious Disease",
                filePath = pdfFile.absolutePath,
                uriString = null,
                fileSizeBytes = 18700000,
                pageCount = 870,
                currentPage = 0,
                readingProgress = 0.0f,
                isFavorite = false,
                category = "PDF",
                collectionId = "col_medical",
                thumbnailPath = null,
                coverColorHex = "#047857",
                lastReadTimestamp = System.currentTimeMillis() - 86400000 * 6,
                addedTimestamp = System.currentTimeMillis() - 86400000 * 1
            )
        )
        bookDao.insertAll(books)

        // Seed Many-to-Many Relationships:
        // A book can belong to multiple collections (e.g. Aircraft Systems belongs to Aviation AND Favorites!)
        val crossRefs = listOf(
            BookCollectionCrossRef("book_aircraft_systems", "col_aviation"),
            BookCollectionCrossRef("book_aircraft_systems", "col_favorites"),
            BookCollectionCrossRef("book_human_anatomy", "col_medical"),
            BookCollectionCrossRef("book_human_anatomy", "col_study"),
            BookCollectionCrossRef("book_thermodynamics", "col_study"),
            BookCollectionCrossRef("book_thermodynamics", "col_research"),
            BookCollectionCrossRef("book_physics", "col_study"),
            BookCollectionCrossRef("book_microbiology", "col_medical"),
            BookCollectionCrossRef("book_organic_chemistry", "col_study"),
            BookCollectionCrossRef("book_mathematics", "col_study"),
            BookCollectionCrossRef("book_aviation_fundamentals", "col_aviation"),
            BookCollectionCrossRef("book_aviation_fundamentals", "col_exam"),
            BookCollectionCrossRef("book_medical_microbiology", "col_medical")
        )
        bookDao.insertAllCrossRefs(crossRefs)

        // Annotations
        val annotations = listOf(
            AnnotationEntity("ann_126_yellow", "book_aircraft_systems", 125, AnnotationType.HIGHLIGHT.name, "#FFE600", "The primary function of the aircraft systems is to ensure a continuous and reliable supply of power and services under all operating conditions.", "Primary systems requirement.", 45f, 245f, 550f, 280f, null, System.currentTimeMillis() - 1000 * 60 * 30),
            AnnotationEntity("ann_126_pink", "book_aircraft_systems", 125, AnnotationType.HIGHLIGHT.name, "#F472B6", "The engine converts chemical energy from fuel into mechanical energy", "Important for exam!", 75f, 680f, 490f, 700f, null, System.currentTimeMillis() - 1000 * 60 * 20)
        )
        annotationDao.insertAll(annotations)

        bookmarkDao.insertBookmark(BookmarkEntity("bm_126", "book_aircraft_systems", 125, "Chapter 3: Aircraft Systems", System.currentTimeMillis() - 1000 * 60 * 15))
    }
}
