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
import androidx.compose.ui.graphics.luminance
import com.bookflow.app.domain.model.AppThemeMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
fun HomeScreen(viewModel: HomeViewModel, onBookClick: (String, Int) -> Unit, onNavigateToCollections: () -> Unit, onLibrary: (String) -> Unit, onBookInformation: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readingStats by viewModel.readingStats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var actions by remember { mutableStateOf<Book?>(null) }
    var managing by remember { mutableStateOf<Book?>(null) }
    var showReadingGoal by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) viewModel.importMultiplePdfs(it) }
    LaunchedEffect(state.toastMessage) { state.toastMessage?.let { snackbar.showSnackbar(it); viewModel.clearToast() } }
    Scaffold(
        topBar = {
            val today = remember { java.time.LocalDate.now() }
            BookFlowTopBar(
                title = greeting(),
                titleContent = {
                    Text(
                        today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase(),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(greeting(), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                },
                actions = {
                    ReadingGoalBadge(readingStats, onClick = { showReadingGoal = true })
                    // Quick light/dark switch; the full System/Light/Dark choice lives in Settings › App Theme
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    BookFlowTopBarAction(
                        icon = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                        onClick = { viewModel.setAppTheme(if (isDark) AppThemeMode.LIGHT else AppThemeMode.DARK) }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isImporting) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
            item { SectionHeader("Continue Reading") { onLibrary("Reading") } }
            item {
                if (state.continueReadingBooks.isEmpty()) EmptyShelf("Open a PDF to start your next chapter.")
                else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.continueReadingBooks, key = { it.id }) { book ->
                        CompactBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, width = 112.dp, showProgress = true)
                    }
                }
            }
            item { SectionHeader("Recently Opened") { onLibrary("Recent") } }
            item {
                if (state.recentlyOpenedBooks.isEmpty()) EmptyShelf("Your recently opened books will appear here.")
                else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recentlyOpenedBooks.take(12), key = { it.id }) { book ->
                        CompactBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, width = CompactCoverWidth)
                    }
                }
            }
            item { SectionHeader("My Library") { onLibrary("All") } }
            item {
                // allBooks, not myLibraryBooks: the Search tab shares this ViewModel's query, which must not filter Home
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.allBooks, key = { it.id }) { book ->
                        CompactBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, width = CompactCoverWidth)
                    }
                    item { ImportTile { importer.launch(arrayOf("application/pdf")) } }
                }
                if (state.allBooks.isEmpty()) EmptyShelf("Build your library. Import your first PDF.")
            }
            item { SectionHeader("Collections", onNavigateToCollections) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.collections, key = { it.id }) { collection -> CompactCollectionChip(collection, onNavigateToCollections) }
                    if (state.collections.isEmpty()) item { TextButton(onClick = onNavigateToCollections) { Text("Create a collection") } }
                }
            }
        }
    }
    if (showReadingGoal) ReadingGoalSheet(readingStats, onGoalChange = viewModel::setDailyReadingGoal, onDismiss = { showReadingGoal = false })
    actions?.let { book -> BookActionsDialog(book, { actions = null }, { onBookClick(book.id, book.currentPage) }, { viewModel.toggleFavorite(book) }, { managing = book }, { onBookInformation(book.id) }, { runCatching { shareBook(context, book) }.onFailure { android.widget.Toast.makeText(context, "Unable to share this file", android.widget.Toast.LENGTH_SHORT).show() } }, { viewModel.removeBook(book) }) }
    managing?.let { book -> BookCollectionsDialog(book, state.collections, { managing = null }, { viewModel.setCollections(book, it) }) }
}

private val CompactCoverWidth = 92.dp
private const val CoverAspectRatio = 0.7f

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/**
 * Compact shelf card: a portrait cover with a soft shadow, one-line title, and either a slim progress bar
 * or the page count. Tap opens; long-press or the ⋯ button shows book actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactBookCard(book: Book, onClick: () -> Unit, onActions: () -> Unit, width: androidx.compose.ui.unit.Dp, showProgress: Boolean = false) {
    Column(
        Modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onActions)
    ) {
        Box {
            BookArtwork(
                book,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(CoverAspectRatio)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            )
            if (book.isFavorite) Icon(Icons.Default.Star, "Favorite", tint = Color(0xFFFFD339), modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(16.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(book.title, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showProgress && book.readingProgress > 0f) {
                LinearProgressIndicator(
                    progress = { book.readingProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(3.dp),
                    color = BrandPurple,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
                Text("${(book.readingProgress * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            } else {
                Text(
                    if (book.readingProgress > 0f) "Page ${book.currentPage + 1}" else "${book.pageCount} pages",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(onClick = onActions, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreHoriz, "Options for ${book.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ImportTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = BrandPurple.copy(alpha = .06f),
        border = BorderStroke(1.dp, BrandPurple.copy(alpha = .2f)),
        modifier = Modifier.width(CompactCoverWidth).aspectRatio(CoverAspectRatio)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.AddCircle, null, tint = BrandPurple, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text("Import PDF", color = BrandPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Compact collection chip: tinted icon disc, name and book count on one pastel pill. */
@Composable
private fun CompactCollectionChip(collection: com.bookflow.app.domain.model.BookCollection, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(android.graphics.Color.parseColor(collection.pastelColorHex)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Color.White.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(collectionIcon(collection.iconName), null, tint = collectionTint(collection.iconName), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(collection.name, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF101326), maxLines = 1)
                Text(bookCountLabel(collection.bookCount), fontSize = 10.sp, lineHeight = 13.sp, color = Color(0xFF666B85))
            }
        }
    }
}

@Composable
private fun EmptyShelf(text: String) { Text(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
        TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.semantics { contentDescription = "See all $title" }) { Text("See All", fontSize = 12.sp); Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp)) }
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
