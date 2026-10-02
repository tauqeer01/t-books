package com.bookflow.app.presentation.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.domain.model.*
import com.bookflow.app.presentation.components.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onBookClick: (String, Int) -> Unit, onBookDetailsClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var actions by remember { mutableStateOf<Book?>(null) }
    var sort by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) viewModel.importMultiplePdfs(it) }
    LaunchedEffect(state.importMessage) { state.importMessage?.let { snackbar.showSnackbar(it); viewModel.clearImportMessage() } }
    Scaffold(modifier, topBar = {
        BookFlowTopBar(title = "My Library", subtitle = "${state.books.size} books • Your reading collection")
    }, snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0, 0, 0, 0), floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { importer.launch(arrayOf("application/pdf")) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(if (state.isImporting) "Importing…" else "Import PDF") })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state.searchQuery, viewModel::onSearchQueryChanged, placeholder = { Text("Search books and authors") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = CircleShape, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (state.availableCategories + "Reading").forEach { category -> FilterChip(state.selectedCategory == category, { viewModel.onCategorySelected(category) }, label = { Text(category) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { sort = true }) { Text(state.sortOrder.displayName); Icon(Icons.Default.KeyboardArrowDown, null) }
                        DropdownMenu(sort, { sort = false }) { BookSortOrder.entries.forEach { order -> DropdownMenuItem(text = { Text(order.displayName) }, onClick = { viewModel.onSortOrderChanged(order); sort = false }) } }
                    }
                    IconButton(onClick = viewModel::toggleViewMode) { Icon(if (state.viewMode == LibraryViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView, "Toggle grid/list") }
                }
            }
            if (state.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.books.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.MenuBook, null, Modifier.size(48.dp)); Text("No books found", fontWeight = FontWeight.Bold); Text("Import a PDF or change your filters.") }
            } else if (state.viewMode == LibraryViewMode.GRID) LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.books, key = { it.id }) { book -> DashboardBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, detailed = true) }
            } else LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.books, key = { it.id }) { book ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onBookClick(book.id, book.currentPage) }, onLongClick = { actions = book }).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            BookArtwork(book, Modifier.width(58.dp).height(78.dp))
                            Column(Modifier.weight(1f).padding(12.dp)) {
                                Text(book.title, fontWeight = FontWeight.Bold)
                                Text(book.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${book.pageCount} pages • ${(book.readingProgress * 100).toInt()}% read", fontSize = 12.sp)
                                LinearProgressIndicator(progress = { book.readingProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            }
                            IconButton(onClick = { actions = book }) { Icon(Icons.Default.MoreVert, "Options for ${book.title}") }
                        }
                    }
                }
            }
        }
    }
    actions?.let { book -> BookActionsDialog(book, { actions = null }, { onBookClick(book.id, book.currentPage) }, { viewModel.toggleFavorite(book, !book.isFavorite) }, { viewModel.openManageCollectionsDialog(book) }, { onBookDetailsClick(book.id) }, { runCatching { shareBook(context, book) }.onFailure { android.widget.Toast.makeText(context, "Unable to share this file", android.widget.Toast.LENGTH_SHORT).show() } }, { viewModel.removeBookFromLibrary(book.id) }) }
    state.bookToManageCollections?.let { book -> BookCollectionsDialog(book, state.allCollections, viewModel::closeManageCollectionsDialog) { ids -> viewModel.setCollections(book, ids) } }
}
