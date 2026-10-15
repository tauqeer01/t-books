package com.bookflow.app.presentation.screens.esign

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookflow.app.esign.domain.FieldKind
import com.bookflow.app.esign.domain.SignField
import com.bookflow.app.esign.domain.SignatureKind
import com.bookflow.app.presentation.components.BookFlowTopBar
import com.bookflow.app.presentation.components.BookFlowTopBarAction
import java.io.File
import kotlin.math.roundToInt

private val accent = Color(0xFF6D28D9)

@Composable
fun SignEditorScreen(viewModel: SignEditorViewModel, creator: SignatureCreatorViewModel, onClose: () -> Unit, onOpenSigned: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf<SignatureKind?>(null) }
    var editingText by remember { mutableStateOf<SignField?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val unsaved = state.fields.isNotEmpty() && state.savedFile == null

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }
    BackHandler(enabled = unsaved || state.placing != null) {
        if (state.placing != null) viewModel.cancelPlacing() else confirmLeave = true
    }

    fun choose(kind: FieldKind) {
        val needed = when (kind) { FieldKind.SIGNATURE -> SignatureKind.SIGNATURE; FieldKind.INITIALS -> SignatureKind.INITIALS; else -> null }
        if (needed != null && state.signature(needed) == null) creating = needed else viewModel.startPlacing(kind)
    }

    Scaffold(
        topBar = {
            BookFlowTopBar(title = state.title, subtitle = if (state.savedFile != null) "Signed copy saved" else "Fill & Sign",
                onBack = { if (unsaved) confirmLeave = true else onClose() }) {
                if (viewModel.canUndo) BookFlowTopBarAction(Icons.AutoMirrored.Outlined.Undo, "Undo", viewModel::undo)
                TextButton(onClick = viewModel::save, enabled = !state.saving && state.fields.isNotEmpty()) {
                    Text(if (state.saving) "Saving…" else "Save", fontWeight = FontWeight.Bold)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider()
                val selected = state.selected
                when {
                    state.savedFile != null -> SavedBar(
                        onOpen = { state.savedBookId?.let(onOpenSigned) },
                        onShare = { shareFile(context, state.savedFile!!) }
                    )
                    state.placing != null -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tap the page where the ${state.placing!!.title.lowercase()} should go", Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelPlacing) { Text("Cancel") }
                    }
                    selected != null -> Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Drag to move · corner to resize", Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selected.kind.isText) IconButton(onClick = { editingText = selected }) { Icon(Icons.Outlined.Edit, "Edit text") }
                        IconButton(onClick = viewModel::deleteSelected) { Icon(Icons.Outlined.Delete, "Delete") }
                        TextButton(onClick = { viewModel.select(null) }) { Text("Done") }
                    }
                    else -> ToolBar(::choose)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error!!, Modifier.align(Alignment.Center).padding(24.dp))
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.pages, key = { it.index }) { page ->
                        SignPageView(page.index, page.aspect, state, viewModel, onEditText = { editingText = it })
                    }
                }
            }
            if (state.saving) Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }

    creating?.let { kind ->
        SignatureCreatorSheet(kind, creator, onSaved = {
            creating = null
            viewModel.refreshSignatures()
            viewModel.startPlacing(if (kind == SignatureKind.SIGNATURE) FieldKind.SIGNATURE else FieldKind.INITIALS)
        }, onDismiss = { creating = null })
    }
    editingText?.let { field ->
        var text by remember(field.id) { mutableStateOf(field.text) }
        AlertDialog(
            onDismissRequest = { editingText = null },
            title = { Text(if (field.kind == FieldKind.DATE) "Date" else "Text") },
            text = { OutlinedTextField(text, { text = it.take(120) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.select(field.id); viewModel.setText(text); editingText = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { editingText = null }) { Text("Cancel") } }
        )
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Discard changes?") },
        text = { Text("The signatures and text you added haven't been saved.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep editing") } }
    )
}

@Composable
private fun ToolBar(onTool: (FieldKind) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Tool(Icons.Outlined.Draw, "Signature") { onTool(FieldKind.SIGNATURE) }
        Tool(null, "Initials", glyph = "AB") { onTool(FieldKind.INITIALS) }
        Tool(Icons.Outlined.TextFields, "Text") { onTool(FieldKind.TEXT) }
        Tool(Icons.Outlined.CalendarToday, "Date") { onTool(FieldKind.DATE) }
        Tool(Icons.Outlined.Check, "Check") { onTool(FieldKind.CHECK) }
        Tool(Icons.Outlined.Close, "Cross") { onTool(FieldKind.CROSS) }
    }
}

@Composable
private fun Tool(icon: ImageVector?, label: String, glyph: String? = null, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).background(accent.copy(alpha = .1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = accent) else Text(glyph.orEmpty(), color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(label, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SavedBar(onOpen: () -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open signed copy") }
        TextButton(onClick = onShare) { Text("Share") }
    }
}

@Composable
private fun SignPageView(index: Int, aspect: Float, state: SignEditorState, viewModel: SignEditorViewModel, onEditText: (SignField) -> Unit) {
    val bitmap by produceState<Bitmap?>(null, index) { value = viewModel.renderPage(index) }
    val latest by rememberUpdatedState(state)
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(aspect).shadow(2.dp, RoundedCornerShape(2.dp)).background(Color.White)
            .pointerInput(index) {
                detectTapGestures { offset ->
                    val fx = offset.x / size.width
                    val fy = offset.y / size.height
                    if (latest.placing != null) viewModel.place(index, fx, fy)
                    else viewModel.select(latest.fields.lastOrNull { it.page == index && fx in it.x..it.right && fy in it.y..it.bottom }?.id)
                }
            }
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        val pageHeightPx = constraints.maxHeight.toFloat()
        bitmap?.let { Image(it.asImageBitmap(), "Page ${index + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
            ?: CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
        state.fields.filter { it.page == index }.forEach { field ->
            FieldView(field, selected = field.id == state.selectedId, pageWidthPx, pageHeightPx, viewModel, onEditText)
        }
    }
}

@Composable
private fun FieldView(field: SignField, selected: Boolean, pageW: Float, pageH: Float, viewModel: SignEditorViewModel, onEditText: (SignField) -> Unit) {
    val density = LocalDensity.current
    // Gesture handlers outlive recompositions; read the field's current size, not the one from the first frame
    val current by rememberUpdatedState(field)
    val w = with(density) { (field.width * pageW).toDp() }
    val h = with(density) { (field.height * pageH).toDp() }
    Box(
        Modifier.offset { IntOffset((field.x * pageW).roundToInt(), (field.y * pageH).roundToInt()) }.size(w, h)
            .then(if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(2.dp)) else Modifier)
            .pointerInput(field.id) {
                detectTapGestures(onTap = { viewModel.select(field.id) }, onDoubleTap = { if (current.kind.isText) onEditText(current) })
            }
            .pointerInput(field.id) {
                detectDragGestures(
                    onDragStart = { viewModel.select(field.id) },
                    onDragEnd = viewModel::commitMove, onDragCancel = viewModel::commitMove
                ) { change, drag -> change.consume(); viewModel.moveSelected(drag.x / pageW, drag.y / pageH) }
            }
    ) {
        when {
            field.kind.isImage -> AsyncImage(File(field.imagePath.orEmpty()), field.kind.title, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            field.kind.isText -> Text(
                field.text, color = Color(field.colorArgb), maxLines = 1, softWrap = false,
                // Same proportions as the written PDF: glyphs about 80% of the field height
                fontSize = with(density) { (field.height * pageH * 0.8f).toSp() },
                modifier = Modifier.align(Alignment.BottomStart)
            )
            else -> Canvas(Modifier.fillMaxSize()) {
                val stroke = minOf(size.width, size.height) * 0.12f
                val c = Color(field.colorArgb)
                if (field.kind == FieldKind.CHECK) {
                    drawLine(c, Offset(size.width * .12f, size.height * .55f), Offset(size.width * .4f, size.height * .85f), stroke, StrokeCap.Round)
                    drawLine(c, Offset(size.width * .4f, size.height * .85f), Offset(size.width * .9f, size.height * .15f), stroke, StrokeCap.Round)
                } else {
                    drawLine(c, Offset(size.width * .15f, size.height * .15f), Offset(size.width * .85f, size.height * .85f), stroke, StrokeCap.Round)
                    drawLine(c, Offset(size.width * .85f, size.height * .15f), Offset(size.width * .15f, size.height * .85f), stroke, StrokeCap.Round)
                }
            }
        }
        if (selected) {
            // Corner handle: drag to resize, keeping the field's proportions
            Box(
                Modifier.align(Alignment.BottomEnd).offset(10.dp, 10.dp).size(22.dp).clip(CircleShape).background(Color.White).border(2.dp, accent, CircleShape)
                    .pointerInput(field.id) {
                        detectDragGestures(onDragEnd = viewModel::commitMove, onDragCancel = viewModel::commitMove) { change, drag ->
                            change.consume()
                            val widthPx = current.width * pageW
                            viewModel.moveSelected(0f, 0f, scale = ((widthPx + drag.x) / widthPx).coerceIn(0.8f, 1.25f))
                        }
                    }
            )
        }
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share signed PDF"))
    }
}
