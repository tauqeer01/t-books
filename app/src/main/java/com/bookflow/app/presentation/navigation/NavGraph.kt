package com.bookflow.app.presentation.navigation

import com.bookflow.app.presentation.components.BookFlowNavigationRail
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.bookflow.app.BookFlowApplication
import com.bookflow.app.presentation.components.BookFlowBottomBar
import com.bookflow.app.presentation.screens.bookdetails.BookDetailsScreen
import com.bookflow.app.presentation.screens.bookdetails.BookDetailsViewModel
import com.bookflow.app.presentation.screens.collections.CollectionsScreen
import com.bookflow.app.presentation.screens.collections.CollectionsViewModel
import com.bookflow.app.presentation.screens.home.HomeScreen
import com.bookflow.app.presentation.screens.home.HomeViewModel
import com.bookflow.app.presentation.screens.library.LibraryScreen
import com.bookflow.app.presentation.screens.library.LibraryViewModel
import com.bookflow.app.presentation.screens.reader.ReaderScreen
import com.bookflow.app.presentation.screens.reader.ReaderViewModel
import com.bookflow.app.presentation.screens.search.SearchScreen
import com.bookflow.app.presentation.screens.settings.SettingsScreen
import com.bookflow.app.presentation.screens.settings.SettingsViewModel

@Composable
fun BookFlowNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    pendingPdfUri: android.net.Uri? = null,
    onPendingPdfHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as BookFlowApplication
    val container = app.container

    // "Open with BookFlow": import the PDF (or find the copy already in the library) and open it
    LaunchedEffect(pendingPdfUri) {
        val uri = pendingPdfUri ?: return@LaunchedEffect
        val result = container.bookRepository.importPdf(uri)
        val bookId = result.getOrNull()?.id
            ?: (result.exceptionOrNull() as? com.bookflow.app.domain.repository.DuplicateBookException)?.existingBookId
        if (bookId != null) {
            navController.navigate(Screen.Reader.createRoute(bookId)) { launchSingleTop = true }
        } else {
            android.widget.Toast.makeText(context, "Couldn't open this PDF", android.widget.Toast.LENGTH_LONG).show()
        }
        onPendingPdfHandled()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom navigation bar on all 5 main tabs
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.Collections.route,
        Screen.Search.route,
        Screen.Settings.route,
        Screen.Notes.route,
        Screen.Cards.route
    )

    // Tablets, foldables and landscape get a side rail instead of a stretched bottom bar
    val useNavigationRail = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val navigateToTab: (String) -> Unit = { route ->
        if (route == Screen.Home.route) {
            val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
            if (!popped) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.id) { inclusive = false }
                    launchSingleTop = true
                }
            }
        } else {
            navController.navigate(route) {
                popUpTo(Screen.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar && !useNavigationRail) {
                BookFlowBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateToTab
                )
            }
        }
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
        if (showBottomBar && useNavigationRail) {
            BookFlowNavigationRail(currentRoute = currentRoute, onNavigate = navigateToTab)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Scoped ViewModels: created once per NavGraph composition, stable across recompositions
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    context = context,
                    getBooksUseCase = container.getBooksUseCase,
                    getCollectionsUseCase = container.getCollectionsUseCase,
                    saveBookUseCase = container.saveBookUseCase,
                    bookRepository = container.bookRepository,
                    annotationRepository = container.annotationRepository,
                    pdfEngineFactory = container.pdfEngineFactory,
                    readingStatsRepository = container.readingStatsRepository,
                    preferencesRepository = container.preferencesRepository
                )
            )

            val libraryViewModel: LibraryViewModel = viewModel(
                factory = LibraryViewModel.Factory(
                    getBooksUseCase = container.getBooksUseCase,
                    getCollectionsUseCase = container.getCollectionsUseCase,
                    bookRepository = container.bookRepository,
                    collectionRepository = container.collectionRepository,
                    preferencesRepository = container.preferencesRepository
                )
            )

            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                // 1. Home Tab (Dashboard from Screenshot)
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onBookClick = { bookId, page ->
                            navController.navigate(Screen.Reader.createRoute(bookId, page))
                        },
                        onNavigateToCollections = {
                            navController.navigate(Screen.Collections.route)
                        },
                        onLibrary = { filter -> libraryViewModel.onCategorySelected(filter); navController.navigate(Screen.Library.route) },
                        onBookInformation = { navController.navigate(Screen.BookDetails.createRoute(it)) },
                        onSearch = { navController.navigate(Screen.Search.route) { launchSingleTop = true } }
                    )
                }

                // 2. Library Tab
                composable(Screen.Library.route) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onBookClick = { bookId, page ->
                            navController.navigate(Screen.Reader.createRoute(bookId, page))
                        },
                        onBookDetailsClick = { bookId ->
                            navController.navigate(Screen.BookDetails.createRoute(bookId))
                        }
                    )
                }

                // 3. Collections Tab (Matching Pastel Collections Screenshot)
                composable(Screen.Collections.route) {
                    val collectionsViewModel: CollectionsViewModel = viewModel(
                        factory = CollectionsViewModel.Factory(
                            getCollectionsUseCase = container.getCollectionsUseCase,
                            saveCollectionUseCase = container.saveCollectionUseCase,
                            collectionRepository = container.collectionRepository,
                            bookRepository = container.bookRepository
                        )
                    )
                    CollectionsScreen(
                        viewModel = collectionsViewModel,
                        onBookInformation = { navController.navigate(Screen.BookDetails.createRoute(it)) },
                        onBookClick = { bookId, page ->
                            navController.navigate(Screen.Reader.createRoute(bookId, page))
                        }
                    )
                }

                // 4. Search Tab
                composable(Screen.Search.route) {
                    SearchScreen(
                        homeViewModel = homeViewModel,
                        onBookClick = { bookId, page ->
                            navController.navigate(Screen.Reader.createRoute(bookId, page))
                        }
                    )
                }

                composable(Screen.Notes.route) {
                    val notesViewModel: com.bookflow.app.presentation.screens.notes.NotesHomeViewModel = viewModel(
                        factory = com.bookflow.app.presentation.screens.notes.NotesHomeViewModel.Factory(container.notesRepository, container.notePenPreferences)
                    )
                    com.bookflow.app.presentation.screens.notes.NotesHomeScreen(
                        viewModel = notesViewModel,
                        onOpenNote = { navController.navigate(Screen.NoteEditor.createRoute(it)) }
                    )
                }

                composable(
                    route = Screen.NoteEditor.route,
                    arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                ) { entry ->
                    val noteId = entry.arguments?.getString("noteId").orEmpty()
                    val editorViewModel: com.bookflow.app.presentation.screens.notes.editor.NoteEditorViewModel = viewModel(
                        factory = com.bookflow.app.presentation.screens.notes.editor.NoteEditorViewModel.Factory(
                            noteId, container.notesRepository, container.noteFileStore, container.notePenPreferences,
                            container.applicationScope, context.applicationContext
                        )
                    )
                    com.bookflow.app.presentation.screens.notes.editor.NoteEditorScreen(
                        vm = editorViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenSource = { bookId, page -> navController.navigate(Screen.Reader.createRoute(bookId, page)) }
                    )
                }

                composable(Screen.Cards.route) {
                    val vm: com.bookflow.app.presentation.screens.cards.CardsHomeViewModel = viewModel(
                        factory = com.bookflow.app.presentation.screens.cards.CardsHomeViewModel.Factory(container.flashcardRepository))
                    com.bookflow.app.presentation.screens.cards.CardsHomeScreen(vm,
                        onOpenDeck = { navController.navigate(Screen.Deck.createRoute(it)) },
                        onStudy = { navController.navigate(Screen.Review.createRoute(it)) },
                        onStats = { navController.navigate(Screen.CardStats.createRoute()) })
                }
                composable(Screen.Deck.route, arguments = listOf(navArgument("deckId") { type = NavType.StringType })) { entry ->
                    val deckId = entry.arguments?.getString("deckId").orEmpty()
                    val vm: com.bookflow.app.presentation.screens.cards.DeckViewModel = viewModel(
                        factory = com.bookflow.app.presentation.screens.cards.DeckViewModel.Factory(deckId, container.flashcardRepository))
                    com.bookflow.app.presentation.screens.cards.DeckScreen(vm,
                        onBack = { navController.popBackStack() },
                        onStudy = { navController.navigate(Screen.Review.createRoute(deckId)) },
                        onAddCard = { navController.navigate(Screen.CardEditor.createRoute(deckId = deckId)) },
                        onEditCard = { navController.navigate(Screen.CardEditor.createRoute(cardId = it)) },
                        onStats = { navController.navigate(Screen.CardStats.createRoute(deckId)) })
                }
                composable(Screen.CardEditor.route, arguments = listOf(
                    navArgument("cardId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("deckId") { type = NavType.StringType; defaultValue = "" }
                )) { entry ->
                    val cardId = entry.arguments?.getString("cardId")?.takeIf { it.isNotEmpty() }
                    val deckId = entry.arguments?.getString("deckId")?.takeIf { it.isNotEmpty() }
                    val vm: com.bookflow.app.presentation.screens.cards.CardEditorViewModel = viewModel(
                        factory = com.bookflow.app.presentation.screens.cards.CardEditorViewModel.Factory(cardId, deckId, container.flashcardRepository, container.cardDrafts))
                    com.bookflow.app.presentation.screens.cards.CardEditorScreen(vm, onClose = { navController.popBackStack() })
                }
                composable(Screen.Review.route, arguments = listOf(navArgument("deckId") { type = NavType.StringType })) { entry ->
                    val deckId = entry.arguments?.getString("deckId").orEmpty()
                    val vm: com.bookflow.app.presentation.screens.cards.ReviewViewModel = viewModel(
                        factory = com.bookflow.app.presentation.screens.cards.ReviewViewModel.Factory(deckId, container.flashcardRepository))
                    com.bookflow.app.presentation.screens.cards.ReviewScreen(vm,
                        onBack = { navController.popBackStack() },
                        onOpenSource = { book, page -> navController.navigate(Screen.Reader.createRoute(book, page)) })
                }
                composable(Screen.CardStats.route, arguments = listOf(navArgument("deckId") { type = NavType.StringType; defaultValue = "" })) { entry ->
                    val deckId = entry.arguments?.getString("deckId")?.takeIf { it.isNotEmpty() }
                    val vm: com.bookflow.app.presentation.screens.cards.StatsViewModel = viewModel(
                        key = "stats_${deckId.orEmpty()}",
                        factory = com.bookflow.app.presentation.screens.cards.StatsViewModel.Factory(deckId, container.flashcardRepository))
                    com.bookflow.app.presentation.screens.cards.CardStatsScreen(vm, onBack = { navController.popBackStack() })
                }

                // 5. Settings Tab (Matching Settings Screenshot)
                composable(Screen.Settings.route) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            context = context,
                            preferencesRepository = container.preferencesRepository,
                            bookRepository = container.bookRepository
                        )
                    )
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onCollections = { navController.navigate(Screen.Collections.route) },
                        onLibrary = { navController.navigate(Screen.Library.route) },
                        onOpenLegal = { navController.navigate(Screen.Legal.createRoute(it.route)) },
                        onOpenLicenses = { navController.navigate(Screen.Licenses.route) }
                    )
                }

                composable(Screen.Legal.route, arguments = listOf(navArgument("doc") { type = NavType.StringType })) { entry ->
                    com.bookflow.app.presentation.screens.legal.LegalDocumentScreen(
                        document = com.bookflow.app.presentation.screens.legal.LegalDocument.fromRoute(entry.arguments?.getString("doc")),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Licenses.route) {
                    com.bookflow.app.presentation.screens.legal.OpenSourceLicensesScreen(onBack = { navController.popBackStack() })
                }

                // PDF Reader Screen (Matching Aircraft Systems Screenshot)
                composable(
                    route = Screen.Reader.route,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("page") {
                            type = NavType.IntType
                            defaultValue = -1
                        }
                    )
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: "book_aircraft_systems"
                    val page = backStackEntry.arguments?.getInt("page") ?: -1

                    val readerViewModel: ReaderViewModel = viewModel(
                        factory = ReaderViewModel.Factory(
                            context = context,
                            bookId = bookId,
                            initialPage = page,
                            getBookByIdUseCase = container.getBookByIdUseCase,
                            updateProgressUseCase = container.updateProgressUseCase,
                            getAnnotationsUseCase = container.getAnnotationsUseCase,
                            saveAnnotationUseCase = container.saveAnnotationUseCase,
                            deleteAnnotationUseCase = container.deleteAnnotationUseCase,
                            bookmarkUseCase = container.bookmarkUseCase,
                            preferencesRepository = container.preferencesRepository,
                            pdfEngineFactory = container.pdfEngineFactory,
                            readingStatsRepository = container.readingStatsRepository
                        )
                    )

                    val captureViewModel: com.bookflow.app.presentation.screens.reader.ReaderCaptureViewModel = viewModel(
                        factory = com.bookflow.app.presentation.screens.reader.ReaderCaptureViewModel.Factory(
                            container.notesRepository, container.noteCapture, container.notePenPreferences
                        )
                    )
                    com.bookflow.app.presentation.screens.reader.ReaderCaptureHost(
                        readerViewModel = readerViewModel,
                        captureViewModel = captureViewModel,
                        onOpenNote = { navController.navigate(Screen.NoteEditor.createRoute(it)) },
                        onMakeCard = { draft -> container.cardDrafts.draft = draft; navController.navigate(Screen.CardEditor.createRoute()) },
                        notesPanel = { noteId, inbox, onClose, panelModifier ->
                            val panelViewModel: com.bookflow.app.presentation.screens.notes.editor.NoteEditorViewModel = viewModel(
                                key = "notes_panel_$noteId",
                                factory = com.bookflow.app.presentation.screens.notes.editor.NoteEditorViewModel.Factory(
                                    noteId, container.notesRepository, container.noteFileStore, container.notePenPreferences,
                                    container.applicationScope, context.applicationContext
                                )
                            )
                            LaunchedEffect(panelViewModel) { inbox.collect { panelViewModel.appendCaptured(it) } }
                            com.bookflow.app.presentation.screens.notes.editor.NoteEditorScreen(
                                vm = panelViewModel,
                                onBack = onClose,
                                // A clip from this book jumps the reader beside it; other books open normally
                                onOpenSource = { sourceBook, sourcePage ->
                                    if (sourceBook == bookId) readerViewModel.jumpToPage(sourcePage)
                                    else navController.navigate(Screen.Reader.createRoute(sourceBook, sourcePage))
                                },
                                modifier = panelModifier,
                                inPanel = true
                            )
                        }
                    ) { onNotesClick ->
                        ReaderScreen(
                            viewModel = readerViewModel,
                            onBackClick = { navController.popBackStack() },
                            onNotesClick = onNotesClick
                        )
                    }
                }

                // Book Details & Annotation Hub Screen (Phase 5)
                composable(
                    route = Screen.BookDetails.route,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: "book_aircraft_systems"
                    val detailsViewModel: BookDetailsViewModel = viewModel(
                        factory = BookDetailsViewModel.Factory(
                            bookId = bookId,
                            bookRepository = container.bookRepository,
                            annotationRepository = container.annotationRepository,
                            collectionRepository = container.collectionRepository
                        )
                    )
                    BookDetailsScreen(
                        viewModel = detailsViewModel,
                        onBackClick = { navController.popBackStack() },
                        onNavigateToReader = { bId, page ->
                            navController.navigate(Screen.Reader.createRoute(bId, page))
                        }
                    )
                }
            }
        }
        }
    }
}
