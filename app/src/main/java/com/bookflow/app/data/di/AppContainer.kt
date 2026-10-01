package com.bookflow.app.data.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.data.preferences.DataStorePreferencesRepository
import com.bookflow.app.data.repository.AnnotationRepositoryImpl
import com.bookflow.app.data.repository.BookRepositoryImpl
import com.bookflow.app.data.repository.CollectionRepositoryImpl
import com.bookflow.app.domain.repository.AnnotationRepository
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.domain.repository.CollectionRepository
import com.bookflow.app.domain.repository.PreferencesRepository
import com.bookflow.app.domain.usecase.BookmarkUseCase
import com.bookflow.app.domain.usecase.DeleteAnnotationUseCase
import com.bookflow.app.domain.usecase.GetAnnotationsUseCase
import com.bookflow.app.domain.usecase.GetBookByIdUseCase
import com.bookflow.app.domain.usecase.GetBooksUseCase
import com.bookflow.app.domain.usecase.GetCollectionsUseCase
import com.bookflow.app.domain.usecase.SaveAnnotationUseCase
import com.bookflow.app.domain.usecase.SaveBookUseCase
import com.bookflow.app.domain.usecase.SaveCollectionUseCase
import com.bookflow.app.domain.usecase.UpdateProgressUseCase
import com.bookflow.app.pdf.engine.PdfEngineFactory

class AppContainer(private val context: Context) {

    val database: BookFlowDatabase by lazy {
        BookFlowDatabase.getInstance(context)
    }

    val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(context)
    }

    val pdfEngineFactory: PdfEngineFactory by lazy {
        PdfEngineFactory(context)
    }

    val bookRepository: BookRepository by lazy {
        BookRepositoryImpl(
            context = context,
            bookDao = database.bookDao(),
            collectionDao = database.collectionDao(),
            annotationDao = database.annotationDao(),
            bookmarkDao = database.bookmarkDao(),
            pdfEngineFactory = pdfEngineFactory
        )
    }

    val collectionRepository: CollectionRepository by lazy {
        CollectionRepositoryImpl(
            collectionDao = database.collectionDao()
        )
    }

    val annotationRepository: AnnotationRepository by lazy {
        AnnotationRepositoryImpl(
            annotationDao = database.annotationDao(),
            bookmarkDao = database.bookmarkDao()
        )
    }

    // Use Cases
    val getBooksUseCase: GetBooksUseCase by lazy { GetBooksUseCase(bookRepository) }
    val getBookByIdUseCase: GetBookByIdUseCase by lazy { GetBookByIdUseCase(bookRepository) }
    val saveBookUseCase: SaveBookUseCase by lazy { SaveBookUseCase(bookRepository) }
    val updateProgressUseCase: UpdateProgressUseCase by lazy { UpdateProgressUseCase(bookRepository) }
    val getCollectionsUseCase: GetCollectionsUseCase by lazy { GetCollectionsUseCase(collectionRepository) }
    val saveCollectionUseCase: SaveCollectionUseCase by lazy { SaveCollectionUseCase(collectionRepository) }
    val getAnnotationsUseCase: GetAnnotationsUseCase by lazy { GetAnnotationsUseCase(annotationRepository) }
    val saveAnnotationUseCase: SaveAnnotationUseCase by lazy { SaveAnnotationUseCase(annotationRepository) }
    val deleteAnnotationUseCase: DeleteAnnotationUseCase by lazy { DeleteAnnotationUseCase(annotationRepository) }
    val bookmarkUseCase: BookmarkUseCase by lazy { BookmarkUseCase(annotationRepository) }
}
