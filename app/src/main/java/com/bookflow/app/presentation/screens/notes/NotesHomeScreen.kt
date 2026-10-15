package com.bookflow.app.presentation.screens.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookflow.app.core.util.DateUtils
import com.bookflow.app.notes.domain.NoteFolder
import com.bookflow.app.notes.domain.NoteSort
import com.bookflow.app.notes.domain.NoteSummary
import com.bookflow.app.presentation.components.BookFlowTopBar
import com.bookflow.app.presentation.components.BookFlowTopBarAction
import kotlinx.coroutines.launch
import java.io.File

private val folderColors = listOf("#6D28D9", "#2563EB", "#059669", "#D97706", "#DC2626", "#DB2777")

@Composable
fun NotesHomeScreen(viewModel: NotesHomeViewModel, onOpenNote: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var folderAction by remember { mutableStateOf<NoteFolder?>(null) }
    var noteAction by remember { mutableStateOf<NoteSummary?>(null) }

    Scaffold(
        topBar = {
            BookFlowTopBar(title = state.currentFolder?.name ?: "Notes", subtitle = "${state.pinned.size + state.others.size} notes",
                onBack = state.currentFolder?.let { f -> { viewModel.openFolder(f.parentId) } }) {
                BookFlowTopBarAction(Icons.Outlined.Search, "Search notes", { searching = !searching; if (!searching) viewModel.search("") }, selected = searching)
                BookFlowTopBarAction(if (state.grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, "Change layout", viewModel::toggleGrid)
                Box {
                    BookFlowTopBarAction(Icons.AutoMirrored.Outlined.Sort, "Sort", { sortMenu = true })
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        NoteSort.entries.forEach { sort ->
                            DropdownMenuItem(text = { Text(sort.title) }, onClick = { viewModel.setSort(sort); sortMenu = false },
                                trailingIcon = { if (state.sort == sort) Text("✓") })
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { scope.launch { onOpenNote(viewModel.createNote()) } },
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New note") })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searching) {
                OutlinedTextField(state.query, viewModel::search, placeholder = { Text("Search titles, text, tags, and quotes") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (state.breadcrumb.size > 1) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.openFolder(null) }) { Text("All notes") }
                    state.breadcrumb.forEach { f -> Text("›", color = MaterialTheme.colorScheme.outline); TextButton(onClick = { viewModel.openFolder(f.id) }) { Text(f.name) } }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.childFolders, key = { it.id }) { folder ->
                    FolderChip(folder, onClick = { viewModel.openFolder(folder.id) }, onLongClick = { folderAction = folder })
                }
                item { AssistChip(onClick = { newFolder = true }, label = { Text("Folder") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(18.dp)) }) }
            }
            if (state.allTags.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.allTags) { tag -> FilterChip(selected = state.selectedTag == tag, onClick = { viewModel.selectTag(tag) }, label = { Text("#$tag") }) }
                }
            }
            if (state.isEmpty) EmptyNotes(Modifier.weight(1f))
            else if (state.pinned.isEmpty() && state.others.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No notes match", color = MaterialTheme.colorScheme.outline) }
            } else {
                LazyVerticalGrid(
                    columns = if (state.grid) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.pinned.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Pinned") }
                        items(state.pinned, key = { it.id }) { NoteCard(it, state.grid, { onOpenNote(it.id) }, { noteAction = it }) }
                        if (state.others.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Notes") }
                    }
                    items(state.others, key = { it.id }) { NoteCard(it, state.grid, { onOpenNote(it.id) }, { noteAction = it }) }
                }
            }
        }
    }

    if (newFolder) TextPrompt("New folder", "", "Create", onDismiss = { newFolder = false }) { name ->
        viewModel.createFolder(name, folderColors[(state.folders.size) % folderColors.size]); newFolder = false
    }
    folderAction?.let { folder -> FolderActions(folder, onDismiss = { folderAction = null }, onRename = { viewModel.renameFolder(folder.id, it) }, onDelete = { viewModel.deleteFolder(folder) }) }
    noteAction?.let { note -> NoteActions(note, state.folders, viewModel, onDismiss = { noteAction = null }) }
}

@Composable
private fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(folder: NoteFolder, onClick: () -> Unit, onLongClick: () -> Unit) {
    val color = runCatching { Color(android.graphics.Color.parseColor(folder.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    Row(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Folder, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(folder.name, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: NoteSummary, grid: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (grid) Column {
            Thumbnail(note, Modifier.fillMaxWidth().aspectRatio(0.78f))
            NoteMeta(note, Modifier.padding(10.dp))
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(note, Modifier.width(64.dp).height(84.dp).padding(6.dp))
            NoteMeta(note, Modifier.padding(10.dp).weight(1f))
        }
    }
}

@Composable
private fun Thumbnail(note: NoteSummary, modifier: Modifier) {
    val path = note.thumbnailPath
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        if (path != null && File(path).exists()) {
            // updatedAt in the key reloads the cover after edits
            AsyncImage(model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(File(path))
                .memoryCacheKey("${note.id}_${note.updatedAt}").build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else Icon(Icons.Outlined.Description, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun NoteMeta(note: NoteSummary, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) { Icon(Icons.Outlined.PushPin, "Pinned", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
            Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(DateUtils.formatRelative(note.updatedAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        if (note.tags.isNotEmpty()) Text(note.tags.joinToString(" ") { "#$it" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyNotes(modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Description, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text("No notes yet", style = MaterialTheme.typography.titleMedium)
        Text("Write by hand, type, add photos, or send text and clips from any book.", color = MaterialTheme.colorScheme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun FolderActions(folder: NoteFolder, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    if (renaming) TextPrompt("Rename folder", folder.name, "Save", onDismiss) { onRename(it); onDismiss() }
    else AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name) },
        text = { Text("Deleting a folder keeps its notes; they move up one level.") },
        confirmButton = { TextButton(onClick = { renaming = true }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete folder") } }
    )
}

@Composable
private fun NoteActions(note: NoteSummary, folders: List<NoteFolder>, viewModel: NotesHomeViewModel, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf("menu") }
    when (step) {
        "rename" -> TextPrompt("Rename note", note.title, "Save", onDismiss) { viewModel.rename(note, it); onDismiss() }
        "tags" -> TextPrompt("Tags (separate with spaces)", note.tags.joinToString(" "), "Save", onDismiss) { text ->
            viewModel.setTags(note, text.split(Regex("[\\s,]+")).map { it.removePrefix("#") }); onDismiss()
        }
        "move" -> AlertDialog(
            onDismissRequest = onDismiss, title = { Text("Move to folder") },
            text = {
                Column {
                    TextButton(onClick = { viewModel.move(note, null); onDismiss() }) { Text("All notes (no folder)") }
                    folders.forEach { f -> TextButton(onClick = { viewModel.move(note, f.id); onDismiss() }) { Text(f.name) } }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        "delete" -> AlertDialog(
            onDismissRequest = onDismiss, title = { Text("Delete note?") }, text = { Text("\"${note.title.ifBlank { "Untitled note" }}\" and its pages will be deleted.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(note); onDismiss() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss, title = { Text(note.title.ifBlank { "Untitled note" }) },
            text = {
                Column {
                    TextButton(onClick = { viewModel.togglePin(note); onDismiss() }) { Text(if (note.pinned) "Unpin" else "Pin to top") }
                    TextButton(onClick = { step = "rename" }) { Text("Rename") }
                    TextButton(onClick = { step = "tags" }) { Text("Edit tags") }
                    TextButton(onClick = { step = "move" }) { Text("Move to folder") }
                    TextButton(onClick = { step = "delete" }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}

@Composable
private fun TextPrompt(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank() || initial.isNotEmpty()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
