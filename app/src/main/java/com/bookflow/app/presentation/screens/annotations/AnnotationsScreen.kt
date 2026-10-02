package com.bookflow.app.presentation.screens.annotations

import com.bookflow.app.presentation.components.BookFlowTopBar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.AccentCoral
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.presentation.components.AnnotationItemCard

@Composable
fun AnnotationsScreen(
    viewModel: AnnotationsViewModel,
    onNavigateToPage: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filterColors = listOf(
        "#FFE066" to "Yellow",
        "#74C69D" to "Green",
        "#72EFDD" to "Blue",
        "#FFAFCC" to "Pink",
        "#BDB2FF" to "Purple"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BookFlowTopBar(title = "Annotations", subtitle = "Quotes, highlights and notes across your library") {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCoral.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${state.annotations.size} total",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentCoral
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Spacer(Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Search quotes, highlights, thoughts...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCoral,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("annotations_search_input")
            )

            // Filter Chips Row (Type and Color)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All Types"
                FilterChip(
                    selected = state.selectedFilterType == null && state.selectedColorHex == null,
                    onClick = {
                        viewModel.selectFilterType(null)
                        viewModel.selectColorFilter(null)
                    },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                // Highlight Type
                FilterChip(
                    selected = state.selectedFilterType == AnnotationType.HIGHLIGHT,
                    onClick = {
                        viewModel.selectFilterType(
                            if (state.selectedFilterType == AnnotationType.HIGHLIGHT) null else AnnotationType.HIGHLIGHT
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Highlights") },
                    shape = RoundedCornerShape(20.dp)
                )

                // Note Type
                FilterChip(
                    selected = state.selectedFilterType == AnnotationType.NOTE,
                    onClick = {
                        viewModel.selectFilterType(
                            if (state.selectedFilterType == AnnotationType.NOTE) null else AnnotationType.NOTE
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Notes") },
                    shape = RoundedCornerShape(20.dp)
                )

                // Underline Type
                FilterChip(
                    selected = state.selectedFilterType == AnnotationType.UNDERLINE,
                    onClick = {
                        viewModel.selectFilterType(
                            if (state.selectedFilterType == AnnotationType.UNDERLINE) null else AnnotationType.UNDERLINE
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.FormatUnderlined, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Underlines") },
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Color filter swatches
                filterColors.forEach { (hex, name) ->
                    val isSelected = state.selectedColorHex == hex
                    val color = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                viewModel.selectColorFilter(if (isSelected) null else hex)
                            }
                    )
                }
            }

            // List of Annotations
            if (state.annotations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NoteAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No annotations found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Highlight text and add sticky notes inside any PDF",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("annotations_list")
                ) {
                    items(state.annotations, key = { it.id }) { ann ->
                        val book = state.booksMap[ann.bookId]
                        AnnotationItemCard(
                            annotation = ann,
                            bookTitle = book?.title,
                            onClick = { onNavigateToPage(ann.bookId, ann.pageIndex) },
                            onDelete = { viewModel.deleteAnnotation(ann.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
