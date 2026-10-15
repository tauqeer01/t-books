package com.bookflow.app.presentation.screens.reader

import android.content.ClipDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.util.DateUtils
import com.bookflow.app.notes.data.CaptureSource
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.NoteSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Renders the note editor inside the reader; captures arrive through [inbox]. */
typealias NotesPanelContent = @Composable (noteId: String, inbox: SharedFlow<NoteElement>, onClose: () -> Unit, modifier: Modifier) -> Unit

/**
 * Wraps the reader with capture features: routes quotes and clips to the open notes panel or a destination
 * sheet, hosts the side-by-side panel (beside the reader on wide screens, below it on phones), accepts text
 * dragged onto the panel, and confirms deliveries.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderCaptureHost(
    readerViewModel: ReaderViewModel,
    captureViewModel: ReaderCaptureViewModel,
    notesPanel: NotesPanelContent,
    onOpenNote: (String) -> Unit,
    reader: @Composable (onNotesClick: () -> Unit) -> Unit
) {
    val readerState by readerViewModel.uiState.collectAsStateWithLifecycle()
    val capture by captureViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val inbox = remember { MutableSharedFlow<NoteElement>(extraBufferCapacity = 8) }
    var choosingPanelNote by remember { mutableStateOf(false) }
    val panelOpen = capture.panelNoteId != null

    LaunchedEffect(readerState.book?.id, readerState.book?.title) {
        readerState.book?.let { captureViewModel.setBook(it.id, it.title) }
    }
    LaunchedEffect(panelOpen) { readerViewModel.setNotesPanelOpen(panelOpen) }

    fun deliver(source: CaptureSource) {
        if (captureViewModel.state.value.panelNoteId != null) scope.launch { captureViewModel.elementForPanel(source)?.let { inbox.emit(it) } }
        else captureViewModel.capture(source)
    }
    LaunchedEffect(readerViewModel) { readerViewModel.captureRequests.collect(::deliver) }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clip = event.toAndroidDragEvent().clipData ?: return false
                val text = (0 until clip.itemCount).joinToString(" ") { clip.getItemAt(it).text?.toString().orEmpty() }.trim()
                if (text.isEmpty()) return false
                val state = readerViewModel.uiState.value
                deliver(CaptureSource.Quote(text, state.book?.id.orEmpty(), state.selectedTextSelection?.pageIndex ?: state.currentPage))
                readerViewModel.clearSelection()
                return true
            }
        }
    }

    val onNotesClick = { if (panelOpen) captureViewModel.closePanel() else choosingPanelNote = true }
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    Box(Modifier.fillMaxSize()) {
        val noteId = capture.panelNoteId
        if (noteId == null) reader(onNotesClick)
        else SplitPane(wide = wide, first = { reader(onNotesClick) }) {
            Box(Modifier.fillMaxSize().dragAndDropTarget(
                shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                target = dropTarget
            )) {
                // Beside the reader the panel reaches the top of the screen; below it, it does not
                val panelModifier = if (wide) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars) else Modifier.fillMaxSize()
                notesPanel(noteId, inbox, captureViewModel::closePanel, panelModifier)
            }
        }

        capture.delivered?.let { delivered ->
            LaunchedEffect(delivered) { delay(5_000); captureViewModel.consumeDelivered() }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                action = {
                    Row {
                        TextButton(onClick = captureViewModel::undoDelivered) { Text("Undo") }
                        TextButton(onClick = { captureViewModel.consumeDelivered(); onOpenNote(delivered.noteId) }) { Text("Open") }
                    }
                }
            ) { Text("Added to ${delivered.noteTitle}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        capture.error?.let { error ->
            LaunchedEffect(error) { delay(4_000); captureViewModel.consumeError() }
            Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) { Text(error) }
        }
    }

    // Composed after the reader so this back handler takes precedence while the panel is open
    BackHandler(enabled = panelOpen) { captureViewModel.closePanel() }

    capture.pending?.let { pending ->
        ModalBottomSheet(onDismissRequest = captureViewModel::dismiss) {
            NoteChooser(
                title = if (pending is CaptureSource.Clip) "Add clip to…" else "Add quote to…",
                lastNote = capture.lastNote, notes = capture.notes,
                onPick = captureViewModel::sendTo, onNew = { captureViewModel.sendTo(null) }
            )
        }
    }
    if (choosingPanelNote) {
        ModalBottomSheet(onDismissRequest = { choosingPanelNote = false }) {
            NoteChooser(
                title = "Open a note beside the book",
                lastNote = capture.lastNote, notes = capture.notes,
                onPick = { choosingPanelNote = false; captureViewModel.openPanel(it) },
                onNew = { choosingPanelNote = false; captureViewModel.openPanel(null) }
            )
        }
    }
}

/** Reader and panel with a draggable divider: side by side when [wide], stacked otherwise. */
@Composable
private fun SplitPane(wide: Boolean, first: @Composable () -> Unit, second: @Composable () -> Unit) {
    var fraction by remember { mutableFloatStateOf(if (wide) 0.55f else 0.5f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = if (wide) constraints.maxWidth.toFloat() else constraints.maxHeight.toFloat()
        val divider = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(wide, total) {
                detectDragGestures { change, drag ->
                    change.consume()
                    fraction = (fraction + (if (wide) drag.x else drag.y) / total).coerceIn(0.25f, 0.8f)
                }
            }
            .semantics { contentDescription = "Resize notes panel" }
        if (wide) Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(fraction).fillMaxHeight()) { first() }
            Box(divider.width(14.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { Handle(vertical = true) }
            Box(Modifier.weight(1f - fraction).fillMaxHeight()) { second() }
        } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(fraction).fillMaxWidth()) { first() }
            Box(divider.height(18.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Handle(vertical = false) }
            Box(Modifier.weight(1f - fraction).fillMaxWidth()) { second() }
        }
    }
}

@Composable
private fun Handle(vertical: Boolean) {
    Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.outline,
        modifier = if (vertical) Modifier.size(width = 4.dp, height = 36.dp) else Modifier.size(width = 36.dp, height = 4.dp)) {}
}

@Composable
private fun NoteChooser(title: String, lastNote: NoteSummary?, notes: List<NoteSummary>, onPick: (String) -> Unit, onNew: () -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        lastNote?.let { note ->
            ListItem(
                headlineContent = { Text(note.title.ifBlank { "Untitled note" }) },
                supportingContent = { Text("Last used") },
                leadingContent = { Icon(Icons.Outlined.History, null) },
                modifier = Modifier.padding(horizontal = 4.dp).clickable { onPick(note.id) }
            )
        }
        ListItem(
            headlineContent = { Text("New note") },
            supportingContent = { Text("Named after this book") },
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null) },
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onNew)
        )
        val others = notes.filter { it.id != lastNote?.id }
        if (others.isNotEmpty()) {
            Text("All notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.height((others.size * 64).coerceAtMost(320).dp)) {
                items(others, key = { it.id }) { note ->
                    ListItem(
                        headlineContent = { Text(note.title.ifBlank { "Untitled note" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(DateUtils.formatRelative(note.updatedAt)) },
                        leadingContent = { Icon(Icons.Outlined.Description, null) },
                        modifier = Modifier.clickable { onPick(note.id) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
