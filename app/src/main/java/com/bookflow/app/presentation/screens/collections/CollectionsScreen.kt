package com.bookflow.app.presentation.screens.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.*
import com.bookflow.app.presentation.components.*
import com.bookflow.app.presentation.screens.home.collectionTint
import com.bookflow.app.presentation.screens.home.collectionIcon

@Composable
fun CollectionsScreen(viewModel: CollectionsViewModel, onBookClick: (String, Int) -> Unit, onBookInformation: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var descending by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<BookCollection?>(null) }
    var actions by remember { mutableStateOf<Book?>(null) }
    var managing by remember { mutableStateOf<Book?>(null) }
    val context = LocalContext.current
    BackHandler(state.selectedCollection != null) { viewModel.selectCollection(null) }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val columns = if (maxWidth >= 1000.dp) 4 else if (maxWidth >= 700.dp) 3 else 2
        LazyVerticalGrid(columns = GridCells.Fixed(columns), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.selectedCollection != null) IconButton(onClick = { viewModel.selectCollection(null) }) { Icon(Icons.Default.ArrowBack, "Back to collections") }
                        Text(state.selectedCollection?.name ?: "Collections", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        IconButton(onClick = { searching = !searching }) { Icon(Icons.Default.Search, "Search collections") }
                    }
                    Text(state.selectedCollection?.description ?: "Organize your books with collections", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (searching) OutlinedTextField(state.searchQuery, viewModel::onSearchQueryChanged, placeholder = { Text("Search collections") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (state.selectedCollection == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = viewModel::openCreateDialog, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("New Collection", fontSize = 12.sp) }
                    }
                }
            }
            if (state.selectedCollection != null) {
                if (state.collectionBooks.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("This collection is empty. Long-press a book in your library to add it.", Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.collectionBooks, key = { it.id }) { book -> DashboardBookCard(book, { onBookClick(book.id, book.currentPage) }, { actions = book }, detailed = true) }
            } else {
                val collections = if (descending) state.collections.sortedByDescending { it.name.lowercase() } else state.collections.sortedBy { it.name.lowercase() }
                if (collections.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Create a collection to organize your PDFs.", Modifier.padding(vertical = 32.dp)) }
                items(collections, key = { "quick_${it.id}" }) { collection ->
                    val tint = Color(android.graphics.Color.parseColor(collection.pastelColorHex))
                    Surface(onClick = { viewModel.selectCollection(collection) }, shape = RoundedCornerShape(12.dp), color = tint) {
                        Row(Modifier.padding(8.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(collectionIcon(collection.iconName), null, tint = collectionTint(collection.iconName), modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(collection.name, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF101326))
                                Text("${collection.bookCount} books", fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFF666B85))
                            }
                            IconButton(onClick = { delete = collection }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, "Manage ${collection.name}", Modifier.size(18.dp), tint = Color(0xFF101326)) }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("All Collections", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { descending = !descending }) { Text(if (descending) "Sort: Z to A" else "Sort: A to Z", fontSize = 11.sp); Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp)) }
                    }
                }
                items(collections, key = { "shelf_${it.id}" }) { collection ->
                    Surface(onClick = { viewModel.selectCollection(collection) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(collectionIcon(collection.iconName), null, tint = collectionTint(collection.iconName), modifier = Modifier.size(26.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 7.dp)) {
                                    Text(collection.name, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("${collection.bookCount} books", fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val books = state.allBooks.filter { collection.id in it.collectionIds }.take(3)
                                books.forEach { book -> BookArtwork(book, Modifier.weight(1f).aspectRatio(.7f)) }
                                repeat(3 - books.size) { Box(Modifier.weight(1f).aspectRatio(.7f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MenuBook, null, tint = BrandPurple.copy(alpha = .25f), modifier = Modifier.size(22.dp)) } }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.isCreateDialogOpen) CreateCollectionDialog(viewModel::closeCreateDialog, viewModel::createCollection)
    delete?.let { collection -> AlertDialog(onDismissRequest = { delete = null }, title = { Text(collection.name) }, text = { Text("Remove this collection? Books and annotations will remain in your library.") }, confirmButton = { TextButton(onClick = { viewModel.deleteCollection(collection.id); delete = null }) { Text("Remove collection") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
    actions?.let { book -> BookActionsDialog(book, { actions = null }, { onBookClick(book.id, book.currentPage) }, { viewModel.toggleFavorite(book) }, { managing = book }, { onBookInformation(book.id) }, { runCatching { shareBook(context, book) }.onFailure { android.widget.Toast.makeText(context, "Unable to share this file", android.widget.Toast.LENGTH_SHORT).show() } }, { viewModel.removeBook(book) }) }
    managing?.let { book -> BookCollectionsDialog(book, state.collections, { managing = null }, { viewModel.setCollections(book, it) }) }
}
@Composable
fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, pastelColorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#DCEBFA") }
    var selectedIcon by remember { mutableStateOf("folder") }

    val pastelColors = listOf(
        "#DCEBFA",
        "#ECE3FA",
        "#FFE6DC",
        "#DFF3E4",
        "#FFF0D0",
        "#FDE2EC"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Collection",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Collection Name") },
                    placeholder = { Text("e.g. Modern Physics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Short shelf summary") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pastel Palette",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    pastelColors.forEach { hex ->
                        val isSelected = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), description.trim(), selectedColorHex, selectedIcon)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
