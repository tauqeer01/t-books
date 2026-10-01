package com.bookflow.app.presentation.screens.bookdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.core.util.FileUtils
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.BookAnnotation

private val DetailColorPalette = listOf(
    "All" to null,
    "Yellow" to "#FFE600",
    "Green" to "#4ADE80",
    "Blue" to "#38BDF8",
    "Pink" to "#F472B6",
    "Purple" to "#A78BFA"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    viewModel: BookDetailsViewModel,
    onBackClick: () -> Unit,
    onNavigateToReader: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick, modifier = Modifier.testTag("details_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Study Hub & Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (state.book?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (state.book?.isFavorite == true) Color(0xFFEF4444) else Color(0xFF64748B)
                            )
                        }

                        state.book?.let { b ->
                            Button(
                                onClick = { onNavigateToReader(b.id, b.currentPage) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 6.dp).testTag("details_open_reader_button")
                            ) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Read", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize().background(Color(0xFFF8FAFC))
    ) { paddingValues ->
        if (state.isLoading || state.book == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandPurple)
            }
        } else {
            val book = state.book!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8FAFC))
            ) {
                // --- HERO CARD ---
                Surface(
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Book Cover Placeholder / Card
                            Box(
                                modifier = Modifier
                                    .size(width = 64.dp, height = 86.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        try {
                                            Color(android.graphics.Color.parseColor(book.coverColorHex))
                                        } catch (_: Exception) {
                                            BrandPurple
                                        }
                                    )
                                    .padding(6.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = book.title.take(12),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Progress Bar
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { book.readingProgress },
                                        color = BrandPurple,
                                        trackColor = Color(0xFFE2E8F0),
                                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${(book.readingProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPurple
                                    )
                                }

                                Text(
                                    text = "Page ${book.currentPage + 1} of ${book.pageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Collection tags
                        if (state.bookCollections.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.bookCollections) { col ->
                                    val colColor = try {
                                        Color(android.graphics.Color.parseColor(col.pastelColorHex))
                                    } catch (_: Exception) {
                                        Color(0xFFEEF2FF)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colColor)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = col.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF1E293B),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 4 TABS (Overview, Bookmarks, Highlights, Notes) ---
                TabRow(
                    selectedTabIndex = state.selectedTab,
                    containerColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                            color = BrandPurple
                        )
                    }
                ) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text("Overview", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = {
                            Text(
                                "Bookmarks (${state.bookmarks.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                    Tab(
                        selected = state.selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        text = {
                            Text(
                                "Highlights (${state.filteredHighlights.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                    Tab(
                        selected = state.selectedTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        text = {
                            Text(
                                "Notes (${state.filteredNotes.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }

                // --- TAB CONTENT ---
                when (state.selectedTab) {
                    0 -> OverviewTabContent(
                        book = book,
                        state = state,
                        onReadClick = { onNavigateToReader(book.id, book.currentPage) }
                    )
                    1 -> BookmarksTabContent(
                        bookmarks = state.bookmarks,
                        onBookmarkClick = { bm -> onNavigateToReader(book.id, bm.pageIndex) },
                        onDeleteBookmark = { bm -> viewModel.deleteBookmark(bm.id) }
                    )
                    2 -> HighlightsTabContent(
                        highlights = state.filteredHighlights,
                        state = state,
                        sortMenuExpanded = sortMenuExpanded,
                        onSortMenuExpandChange = { sortMenuExpanded = it },
                        onSelectFilterType = viewModel::setFilterType,
                        onSelectColor = viewModel::setFilterColor,
                        onSearchChange = viewModel::setSearchQuery,
                        onSortChange = viewModel::setSortOrder,
                        onHighlightClick = { ann -> onNavigateToReader(book.id, ann.pageIndex) },
                        onEditNote = viewModel::startEditingNote,
                        onDeleteHighlight = { ann -> viewModel.deleteAnnotation(ann.id) }
                    )
                    3 -> NotesTabContent(
                        notes = state.filteredNotes,
                        searchQuery = state.searchQuery,
                        onSearchChange = viewModel::setSearchQuery,
                        onNoteClick = { ann -> onNavigateToReader(book.id, ann.pageIndex) },
                        onEditNote = viewModel::startEditingNote,
                        onDeleteNote = { ann -> viewModel.deleteAnnotation(ann.id) }
                    )
                }
            }
        }
    }

    // Dialog: Edit Note
    if (state.editingAnnotation != null) {
        val ann = state.editingAnnotation!!
        var noteText by remember { mutableStateOf(ann.noteContent) }

        AlertDialog(
            onDismissRequest = viewModel::cancelEditingNote,
            title = {
                Text("Edit Note", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    if (ann.selectedText.isNotBlank()) {
                        Text(
                            text = "\"${ann.selectedText.take(120)}...\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Write your thoughts, summary, or exam notes...") },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPurple),
                        modifier = Modifier.fillMaxWidth().testTag("details_edit_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveAnnotationNote(ann.id, noteText) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::cancelEditingNote) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// TAB 1: OVERVIEW
// ==========================================
@Composable
private fun OverviewTabContent(
    book: com.bookflow.app.domain.model.Book,
    state: BookDetailsUiState,
    onReadClick: () -> Unit
 ) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var filename by remember(book.id) { mutableStateOf(java.io.File(book.filePath).name.ifBlank { "Unavailable" }) }
    androidx.compose.runtime.LaunchedEffect(book.id) {
        if (book.uriString != null) filename = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            FileUtils.extractDocumentMetadata(context, android.net.Uri.parse(book.uriString)).fileName
        }
    }
    fun date(timestamp: Long) = if (timestamp <= 0) "Not opened yet" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(timestamp))
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Study Annotation Summary Grid
        item {
            Text(
                text = "Study Hub Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val highlightsCount = state.allAnnotations.count { it.type == AnnotationType.HIGHLIGHT }
            val underlinesCount = state.allAnnotations.count { it.type == AnnotationType.UNDERLINE }
            val strikethroughsCount = state.allAnnotations.count { it.type == AnnotationType.STRIKETHROUGH }
            val notesCount = state.allAnnotations.count { it.type == AnnotationType.NOTE || it.noteContent.isNotBlank() }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Highlights",
                    count = highlightsCount.toString(),
                    color = Color(0xFFFEF08A),
                    iconColor = Color(0xFFCA8A04),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Notes",
                    count = notesCount.toString(),
                    color = Color(0xFFE0E7FF),
                    iconColor = Color(0xFF4F46E5),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Underlines",
                    count = underlinesCount.toString(),
                    color = Color(0xFFBAE6FD),
                    iconColor = Color(0xFF0284C7),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Bookmarks",
                    count = state.bookmarks.size.toString(),
                    color = Color(0xFFFCE7F3),
                    iconColor = Color(0xFFDB2777),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Document Details Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Document Properties",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    DetailRow(label = "Title", value = book.title)
                    DetailRow(label = "Author", value = book.author)
                    DetailRow(label = "Filename", value = filename)
                    DetailRow(label = "Date Added", value = date(book.addedTimestamp))
                    DetailRow(label = "Last Opened", value = date(book.lastReadTimestamp))
                    DetailRow(label = "Reading Progress", value = "${(book.readingProgress * 100).toInt()}%")
                    DetailRow(label = "Format", value = book.category)
                    DetailRow(label = "Total Pages", value = "${book.pageCount} pages")
                    DetailRow(label = "Current Position", value = "Page ${book.currentPage + 1}")
                    DetailRow(label = "File Size", value = FileUtils.formatFileSize(book.fileSizeBytes))
                    DetailRow(
                        label = "Storage Source",
                        value = if (book.uriString != null) "Device Storage (SAF)" else "Local Application Cache"
                    )
                }
            }
        }

        // Action CTA
        item {
            Button(
                onClick = onReadClick,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue Reading from Page ${book.currentPage + 1}", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// TAB 2: BOOKMARKS
// ==========================================
@Composable
private fun BookmarksTabContent(
    bookmarks: List<com.bookflow.app.domain.model.Bookmark>,
    onBookmarkClick: (com.bookflow.app.domain.model.Bookmark) -> Unit,
    onDeleteBookmark: (com.bookflow.app.domain.model.Bookmark) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No bookmarks saved yet", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
                Text("Tap the bookmark icon while reading to save pages", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(bookmarks, key = { it.id }) { bm ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onBookmarkClick(bm) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEEF2FF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Page ${bm.pageIndex + 1}",
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPurple,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (bm.label.isNotBlank()) bm.label else "Saved Bookmark",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Tap to jump directly to page",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        IconButton(onClick = { onDeleteBookmark(bm) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: HIGHLIGHTS & ANNOTATIONS HUB
// ==========================================
@Composable
private fun HighlightsTabContent(
    highlights: List<BookAnnotation>,
    state: BookDetailsUiState,
    sortMenuExpanded: Boolean,
    onSortMenuExpandChange: (Boolean) -> Unit,
    onSelectFilterType: (AnnotationType?) -> Unit,
    onSelectColor: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (AnnotationSortOrder) -> Unit,
    onHighlightClick: (BookAnnotation) -> Unit,
    onEditNote: (BookAnnotation) -> Unit,
    onDeleteHighlight: (BookAnnotation) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Sort Bar
        Surface(color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search highlighted text or notes...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandPurple) },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPurple),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sort dropdown button
                    Box {
                        IconButton(onClick = { onSortMenuExpandChange(true) }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = BrandPurple)
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { onSortMenuExpandChange(false) }
                        ) {
                            AnnotationSortOrder.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.displayName) },
                                    onClick = {
                                        onSortChange(sort)
                                        onSortMenuExpandChange(false)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Type Filters: All, Highlight, Underline, Strikethrough
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = state.filterType == null,
                            onClick = { onSelectFilterType(null) },
                            label = { Text("All Types", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPurple, selectedLabelColor = Color.White)
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.filterType == AnnotationType.HIGHLIGHT,
                            onClick = { onSelectFilterType(AnnotationType.HIGHLIGHT) },
                            label = { Text("Highlights", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPurple, selectedLabelColor = Color.White)
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.filterType == AnnotationType.UNDERLINE,
                            onClick = { onSelectFilterType(AnnotationType.UNDERLINE) },
                            label = { Text("Underlines", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPurple, selectedLabelColor = Color.White)
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.filterType == AnnotationType.STRIKETHROUGH,
                            onClick = { onSelectFilterType(AnnotationType.STRIKETHROUGH) },
                            label = { Text("Strikethroughs", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandPurple, selectedLabelColor = Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Color Filters
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Color:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))

                    DetailColorPalette.forEach { (name, hex) ->
                        val isSelected = state.filterColorHex.equals(hex, ignoreCase = true)
                        if (hex == null) {
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectColor(null) },
                                label = { Text("All", fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        } else {
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color(0xFF0F172A) else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectColor(if (isSelected) null else hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Highlights List
        if (highlights.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No annotations match the current filters.",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(highlights, key = { it.id }) { ann ->
                    AnnotationHubItemCard(
                        annotation = ann,
                        onClick = { onHighlightClick(ann) },
                        onEditNote = { onEditNote(ann) },
                        onDelete = { onDeleteHighlight(ann) }
                    )
                }
            }
        }
    }
}

// ==========================================
// TAB 4: NOTES
// ==========================================
@Composable
private fun NotesTabContent(
    notes: List<BookAnnotation>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNoteClick: (BookAnnotation) -> Unit,
    onEditNote: (BookAnnotation) -> Unit,
    onDeleteNote: (BookAnnotation) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search your notes...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandPurple) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPurple),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            )
        }

        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NoteAlt, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No notes found", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
                    Text("Add notes by long-pressing text in the reader", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(notes, key = { it.id }) { ann ->
                    AnnotationHubItemCard(
                        annotation = ann,
                        onClick = { onNoteClick(ann) },
                        onEditNote = { onEditNote(ann) },
                        onDelete = { onDeleteNote(ann) }
                    )
                }
            }
        }
    }
}

/**
 * Annotation Hub Card matching user specification:
 * Example:
 * Page 23
 * "The electrical potential difference..."
 * Yellow Highlight
 * Note: Review before exam
 */
@Composable
private fun AnnotationHubItemCard(
    annotation: BookAnnotation,
    onClick: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit
) {
    val annColor = try {
        Color(android.graphics.Color.parseColor(annotation.colorHex))
    } catch (_: Exception) {
        Color(0xFFFFE600)
    }

    val typeLabel = when (annotation.type) {
        AnnotationType.HIGHLIGHT -> "Highlight"
        AnnotationType.UNDERLINE -> "Underline"
        AnnotationType.STRIKETHROUGH -> "Strikethrough"
        AnnotationType.NOTE -> "Text Note"
        AnnotationType.PEN_DRAW -> "Pen Drawing"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("annotation_hub_item_${annotation.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Page Badge, Color + Type Badge, Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Page indicator pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEF2FF))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Page ${annotation.pageIndex + 1}",
                            fontWeight = FontWeight.Bold,
                            color = BrandPurple,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Color indicator circle
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(annColor)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row {
                    IconButton(onClick = onEditNote, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Note", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Quoted Highlighted Text
            if (annotation.selectedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = annColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${annotation.selectedText}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            // Attached Note (Example: Note: Review before exam)
            if (annotation.noteContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAlt,
                        contentDescription = null,
                        tint = BrandPurple,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Note: ${annotation.noteContent}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    count: String,
    color: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = count, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = iconColor)
            Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
    }
}
