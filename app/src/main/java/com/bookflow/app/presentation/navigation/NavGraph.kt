package com.bookflow.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.bookflow.app.presentation.screens.splash.SplashScreen

@Composable
fun BookFlowNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as BookFlowApplication
    val container = app.container

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom navigation bar on all 5 main tabs
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.Collections.route,
        Screen.Search.route,
        Screen.Settings.route
    )

    // Shared HomeViewModel across Home and Search for synchronized state
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            context = context,
            getBooksUseCase = container.getBooksUseCase,
            getCollectionsUseCase = container.getCollectionsUseCase,
            saveBookUseCase = container.saveBookUseCase,
            bookRepository = container.bookRepository,
            annotationRepository = container.annotationRepository,
            pdfEngineFactory = container.pdfEngineFactory
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BookFlowBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(if (showBottomBar) Modifier.statusBarsPadding() else Modifier)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route
            ) {
                // Splash Screen
                composable(Screen.Splash.route) {
                    SplashScreen(
                        onSplashFinished = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }

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
                        onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                        onLibrary = { filter -> libraryViewModel.onCategorySelected(filter); navController.navigate(Screen.Library.route) },
                        onBookInformation = { navController.navigate(Screen.BookDetails.createRoute(it)) },
                        onSettings = { navController.navigate(Screen.Settings.route) }
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

                // 5. Settings Tab (Matching Settings Screenshot)
                composable(Screen.Settings.route) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            context = context,
                            preferencesRepository = container.preferencesRepository,
                            bookRepository = container.bookRepository
                        )
                    )
                    SettingsScreen(viewModel = settingsViewModel, onCollections = { navController.navigate(Screen.Collections.route) }, onLibrary = { navController.navigate(Screen.Library.route) })
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
                            pdfEngineFactory = container.pdfEngineFactory
                        )
                    )

                    ReaderScreen(
                        viewModel = readerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
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
