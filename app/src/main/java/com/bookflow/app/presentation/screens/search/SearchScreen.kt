package com.bookflow.app.presentation.screens.search

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.presentation.components.BookCoverCard
import com.bookflow.app.presentation.components.BookFlowTopBar
import androidx.compose.foundation.layout.WindowInsets
import com.bookflow.app.presentation.screens.home.HomeViewModel

@Composable
fun SearchScreen(
    homeViewModel: HomeViewModel,
    onBookClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    var searchScope by remember { mutableStateOf("All") }

    val recentSearches = listOf("Aircraft Systems", "Turbofan", "Hydraulics", "Power Plant", "Avionics")

    // Match books
    val matchedBooks = remember(state.allBooks, state.searchQuery) {
        if (state.searchQuery.isBlank()) emptyList()
        else state.allBooks.filter {
            it.title.contains(state.searchQuery, ignoreCase = true) ||
            it.author.contains(state.searchQuery, ignoreCase = true)
        }
    }

    // Match highlights (Highlights, Underlines, Strikethroughs)
    val matchedHighlights = remember(state.allAnnotations, state.searchQuery) {
        if (state.searchQuery.isBlank()) emptyList()
        else state.allAnnotations.filter { ann ->
            ann.type in listOf(AnnotationType.HIGHLIGHT, AnnotationType.UNDERLINE, AnnotationType.STRIKETHROUGH) &&
            (ann.selectedText.contains(state.searchQuery, ignoreCase = true) ||
             ann.noteContent.contains(state.searchQuery, ignoreCase = true))
        }
    }

    // Match notes
    val matchedNotes = remember(state.allAnnotations, state.searchQuery) {
        if (state.searchQuery.isBlank()) emptyList()
        else state.allAnnotations.filter { ann ->
            ann.type == AnnotationType.NOTE || ann.noteContent.isNotBlank()
        }.filter { ann ->
            ann.noteContent.contains(state.searchQuery, ignoreCase = true) ||
            ann.selectedText.contains(state.searchQuery, ignoreCase = true)
        }
    }

    // Reading history (Books previously opened, sorted by last read date)
    val readingHistoryBooks = remember(state.allBooks) {
        state.allBooks.filter { it.lastReadTimestamp > 0 }.sortedByDescending { it.lastReadTimestamp }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { BookFlowTopBar(title = "Search", subtitle = "Book titles, highlights and notes") },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = homeViewModel::onSearchQueryChanged,
                placeholder = {
                    Text("Search text in books, highlights, notes...", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8))
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = BrandPurple)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { homeViewModel.onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPurple,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("global_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Books", "Highlights", "Notes").forEach { scope ->
                    val isSelected = searchScope == scope
                    FilterChip(
                        selected = isSelected,
                        onClick = { searchScope = scope },
                        label = { Text(scope, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPurple,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (state.searchQuery.isBlank()) {
                // When blank: show Recent Searches & Reading History
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // 1. Recent Searches Chips
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(
                                text = "Popular Searches",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recentSearches) { term ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        shadowElevation = 1.dp,
                                        modifier = Modifier
                                            .clickable { homeViewModel.onSearchQueryChanged(term) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = BrandPurple,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = term,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Reading History Section
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = BrandPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reading History",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                                Text(
                                    text = "${readingHistoryBooks.size} books",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    items(readingHistoryBooks, key = { it.id }) { book ->
                        ReadingHistoryCard(
                            book = book,
                            onClick = { onBookClick(book.id, book.currentPage) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                // When query active: display filtered results
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val showBooks = searchScope in listOf("All", "Books") && matchedBooks.isNotEmpty()
                    val showHighlights = searchScope in listOf("All", "Highlights") && matchedHighlights.isNotEmpty()
                    val showNotes = searchScope in listOf("All", "Notes") && matchedNotes.isNotEmpty()

                    if (!showBooks && !showHighlights && !showNotes) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No matches found for \"${state.searchQuery}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Try searching for chapters, topics, or words in your highlights",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    // 1. Books Section
                    if (showBooks) {
                        item {
                            Text(
                                text = "Books (${matchedBooks.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        items(matchedBooks, key = { "book_${it.id}" }) { book ->
                            BookCoverCard(
                                book = book,
                                onClick = { onBookClick(book.id, book.currentPage) },
                                onToggleFavorite = {},
                                onRemoveFromLibrary = {}
                            )
                        }
                    }

                    // 2. Highlights Section
                    if (showHighlights) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Highlights & Quotes (${matchedHighlights.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        items(matchedHighlights, key = { "ann_${it.id}" }) { ann ->
                            val book = state.allBooks.find { it.id == ann.bookId }
                            SearchHighlightResultCard(
                                annotation = ann,
                                bookTitle = book?.title ?: "Document",
                                onClick = { onBookClick(ann.bookId, ann.pageIndex) }
                            )
                        }
                    }

                    // 3. Notes Section
                    if (showNotes) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Notes (${matchedNotes.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        items(matchedNotes, key = { "note_${it.id}" }) { ann ->
                            val book = state.allBooks.find { it.id == ann.bookId }
                            SearchNoteResultCard(
                                annotation = ann,
                                bookTitle = book?.title ?: "Document",
                                onClick = { onBookClick(ann.bookId, ann.pageIndex) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingHistoryCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book color tag
            val coverColor = try {
                Color(android.graphics.Color.parseColor(book.coverColorHex))
            } catch (_: Exception) {
                BrandPurple
            }

            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(coverColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { book.readingProgress },
                        color = BrandPurple,
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Page ${book.currentPage + 1} of ${book.pageCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = BrandPurple,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SearchHighlightResultCard(
    annotation: BookAnnotation,
    bookTitle: String,
    onClick: () -> Unit
) {
    val highlightColor = try {
        Color(android.graphics.Color.parseColor(annotation.colorHex))
    } catch (_: Exception) {
        Color(0xFFFFE066)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(highlightColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEF2FF)
                ) {
                    Text(
                        text = "Page ${annotation.pageIndex + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPurple,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "\"${annotation.selectedText}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF334155),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (annotation.noteContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = BrandPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = annotation.noteContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchNoteResultCard(
    annotation: BookAnnotation,
    bookTitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = BrandPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEF2FF)
                ) {
                    Text(
                        text = "Page ${annotation.pageIndex + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPurple,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = annotation.noteContent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF0F172A)
            )

            if (annotation.selectedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ref: \"${annotation.selectedText}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
