package com.bookflow.app.presentation.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.Book
import com.bookflow.app.presentation.components.*

@Composable
fun HomeScreen(viewModel: HomeViewModel, onBookClick: (String, Int) -> Unit, onNavigateToCollections: () -> Unit, onNavigateToSearch: () -> Unit, onLibrary: (String) -> Unit, onBookInformation: (String) -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var actions by remember { mutableStateOf<Book?>(null) }
    var managing by remember { mutableStateOf<Book?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) viewModel.importMultiplePdfs(it) }
    LaunchedEffect(state.toastMessage) { state.toastMessage?.let { snackbar.showSnackbar(it); viewModel.clearToast() } }
    Scaffold(
        topBar = {
            BookFlowTopBar(
                title = "BookFlow",
                subtitle = "Your Reading Companion",
                titleContent = {
                    Text(buildAnnotatedString { append("Book"); withStyle(SpanStyle(color = BrandPurple)) { append("Flow") } }, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                },
                actions = {
                    BookFlowTopBarAction(Icons.Default.Search, "Search", onNavigateToSearch)
                    BookFlowTopBarAction(Icons.Default.History, "Reading history", { onLibrary("Recent") })
                    BookFlowTopBarAction(Icons.Default.AccountCircle, "Preferences", onSettings, tint = BrandPurple)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val cardWidth = ((maxWidth - 56.dp) / 3).coerceIn(108.dp, 190.dp)
            val smallWidth = ((maxWidth - 70.dp) / 4).coerceIn(88.dp, 155.dp)
            LazyColumn(contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(state.searchQuery, viewModel::onSearchQueryChanged, placeholder = { Text("Search your books, notes, highlights…", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = CircleShape, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = BrandPurple, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.filterPills.forEach { pill ->
                            val selected = state.selectedFilterPill == pill
                            Surface(onClick = { if (pill == "Collections") onNavigateToCollections() else viewModel.onFilterPillSelected(pill) }, shape = CircleShape, color = if (selected) BrandPurple else MaterialTheme.colorScheme.surfaceVariant) {
                                Text(pill, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontSize = 12.sp, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    if (state.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                if (state.selectedFilterPill == "All" && state.searchQuery.isBlank()) {
                    item { SectionHeader("Continue Reading") { onLibrary("Reading") } }
                    item {
                        if (state.continueReadingBooks.isEmpty()) EmptyShelf("Open a PDF to start your next chapter.")
                        else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.continueReadingBooks, key = { it.id }) { book -> DashboardBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, Modifier.width(cardWidth), detailed = true) }
                        }
                    }
                    item { SectionHeader("Recently Opened") { onLibrary("Recent") } }
                    item {
                        if (state.recentlyOpenedBooks.isEmpty()) EmptyShelf("Your recently opened books will appear here.")
                        else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.recentlyOpenedBooks.take(12), key = { it.id }) { book -> DashboardBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, Modifier.width(smallWidth)) }
                        }
                    }
                }
                item { SectionHeader(if (state.selectedFilterPill == "Favorites") "Favorites" else "My Library") { onLibrary(state.selectedFilterPill) } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.myLibraryBooks, key = { it.id }) { book -> DashboardBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, Modifier.width(smallWidth)) }
                        item {
                            Surface(onClick = { importer.launch(arrayOf("application/pdf")) }, shape = RoundedCornerShape(12.dp), color = BrandPurple.copy(alpha = .06f), border = BorderStroke(1.dp, BrandPurple.copy(alpha = .2f)), modifier = Modifier.width(smallWidth).height(156.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.AddCircle, null, tint = BrandPurple, modifier = Modifier.size(36.dp))
                                    Text("Import", color = BrandPurple, fontWeight = FontWeight.Bold)
                                    Text("PDF", color = BrandPurple, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (state.myLibraryBooks.isEmpty()) EmptyShelf(if (state.searchQuery.isBlank()) "Build your library. Import your first PDF." else "No books match your search.")
                }
                item { SectionHeader("Collections", onNavigateToCollections) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.collections, key = { it.id }) { collection ->
                            Surface(onClick = onNavigateToCollections, color = Color(android.graphics.Color.parseColor(collection.pastelColorHex)), shape = RoundedCornerShape(12.dp), modifier = Modifier.width(130.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Icon(collectionIcon(collection.iconName), null, tint = collectionTint(collection.iconName), modifier = Modifier.size(27.dp))
                                    Text(collection.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101326))
                                    Text("${collection.bookCount} books", fontSize = 11.sp, color = Color(0xFF666B85))
                                }
                            }
                        }
                        if (state.collections.isEmpty()) item { TextButton(onClick = onNavigateToCollections) { Text("Create a collection") } }
                    }
                }
            }
        }
    }
    actions?.let { book -> BookActionsDialog(book, { actions = null }, { onBookClick(book.id, book.currentPage) }, { viewModel.toggleFavorite(book) }, { managing = book }, { onBookInformation(book.id) }, { runCatching { shareBook(context, book) }.onFailure { android.widget.Toast.makeText(context, "Unable to share this file", android.widget.Toast.LENGTH_SHORT).show() } }, { viewModel.removeBook(book) }) }
    managing?.let { book -> BookCollectionsDialog(book, state.collections, { managing = null }, { viewModel.setCollections(book, it) }) }
}

@Composable
private fun EmptyShelf(text: String) { Text(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
        TextButton(onClick = onSeeAll, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(32.dp)) { Text("See All", fontSize = 12.sp); Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp)) }
    }
}

fun collectionIcon(name: String) = when (name) {
    "flight" -> Icons.Default.Flight
    "favorite" -> Icons.Default.Favorite
    "school" -> Icons.Default.School
    "add_box" -> Icons.Default.AddBox
    "description" -> Icons.Default.Description
    "menu_book" -> Icons.Default.MenuBook
    else -> Icons.Default.FolderOpen
}

fun collectionTint(name: String) = when (name) {
    "flight" -> Color(0xFF1471FF)
    "favorite" -> Color(0xFFFF183E)
    "school" -> Color(0xFFFFB800)
    "add_box" -> Color(0xFF00B889)
    "description" -> Color(0xFFFF652A)
    else -> BrandPurple
}
