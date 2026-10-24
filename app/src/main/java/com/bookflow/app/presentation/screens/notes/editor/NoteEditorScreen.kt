package com.bookflow.app.presentation.screens.notes.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.presentation.screens.notes.PageTemplatePicker
import com.bookflow.app.presentation.screens.notes.export.NoteExporter
import kotlinx.coroutines.launch
import java.io.File

private enum class TemplateSheet { ADD, ADD_AT_END, CHANGE }

/** Vertical position (page fraction) near the top of what is on screen, for inserting new items in view. */
private fun visiblePageY(viewport: NoteViewport): Float {
    val index = viewport.currentPage()
    val docTop = -viewport.offsetY / viewport.pxPerUnit
    return ((docTop - viewport.pageTop(index)) / com.bookflow.app.notes.domain.PAGE_HEIGHT + 0.1f).coerceIn(0.05f, 0.8f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    vm: NoteEditorViewModel,
    onBack: () -> Unit,
    onOpenSource: (bookId: String, page: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Shown beside a book: the back arrow becomes a close button. */
    inPanel: Boolean = false
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gutter = with(LocalDensity.current) { 12.dp.toPx() }
    val viewport = remember { NoteViewport(gutter) }
    val textStates = remember { RichTextStates() }
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var editingPen by remember { mutableStateOf(false) }
    var confirmDeletePage by remember { mutableStateOf<String?>(null) }
    /** Template sheet: adding a page after the current one, or changing the current page's template. */
    var templateSheet by remember { mutableStateOf<TemplateSheet?>(null) }
    var pagesSheet by remember { mutableStateOf(false) }
    /** Page to bring into view once it exists (after adding a page). */
    var pendingScroll by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.pages.size, pendingScroll) {
        val target = pendingScroll ?: return@LaunchedEffect
        if (target < state.pages.size) { viewport.pageCount = state.pages.size; viewport.scrollToPage(target); pendingScroll = null }
    }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    fun currentPageId() = state.pages.getOrNull(viewport.currentPage())?.id
    val paste = { currentPageId()?.let { vm.paste(it, 0.08f, visiblePageY(viewport)) }; Unit }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val pageId = currentPageId()
        if (uri != null && pageId != null) vm.insertImage(pageId, uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFile?.let(::File)
        val pageId = currentPageId()
        if (saved && file != null && pageId != null) vm.insertImage(pageId, Uri.fromFile(file))
        cameraFile = null
    }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(state.revealPageId, state.pages.size) {
        val index = state.pages.indexOfFirst { it.id == state.revealPageId }
        if (index >= 0) { viewport.pageCount = state.pages.size; viewport.scrollToPage(index, state.revealY); vm.consumeReveal() }
    }
    BackHandler(enabled = state.editingElementId != null || state.selection != null) {
        if (state.editingElementId != null) vm.stopEditing() else vm.clearSelection()
    }
    DisposableEffect(Unit) { onDispose { if (vm.state.value.editingElementId != null) vm.stopEditing() } }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.saveNow() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (inPanel) androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (state.editingElementId != null) vm.stopEditing(); onBack() }) {
                        if (inPanel) Icon(Icons.Outlined.Close, "Close notes panel") else Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                windowInsets = if (inPanel) androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) else androidx.compose.material3.TopAppBarDefaults.windowInsets,
                title = {
                    var title by remember(state.isLoading) { mutableStateOf(state.title) }
                    TextField(
                        value = title, onValueChange = { title = it; vm.rename(it) }, singleLine = true,
                        placeholder = { Text("Untitled note") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        textStyle = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(onClick = { pagesSheet = true }) { Icon(Icons.Outlined.GridView, "All pages") }
                    IconButton(onClick = vm::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo") }
                    IconButton(onClick = vm::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Redo") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val pageIndex = viewport.currentPage()
                            DropdownMenuItem(text = { Text("Add page…") }, onClick = { menuOpen = false; templateSheet = TemplateSheet.ADD })
                            DropdownMenuItem(text = { Text("Change page template…") }, onClick = { menuOpen = false; templateSheet = TemplateSheet.CHANGE })
                            DropdownMenuItem(text = { Text("All pages") }, onClick = { menuOpen = false; pagesSheet = true })
                            DropdownMenuItem(text = { Text("Move page up") }, enabled = pageIndex > 0, onClick = { menuOpen = false; currentPageId()?.let { vm.movePage(it, -1) } })
                            DropdownMenuItem(text = { Text("Move page down") }, enabled = pageIndex < state.pages.lastIndex, onClick = { menuOpen = false; currentPageId()?.let { vm.movePage(it, 1) } })
                            DropdownMenuItem(text = { Text("Delete page ${pageIndex + 1}") }, onClick = { menuOpen = false; confirmDeletePage = currentPageId() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Share as PDF") }, onClick = {
                                menuOpen = false
                                scope.launch {
                                    runCatching { NoteExporter.exportPdf(context, state.title, state.pages) }
                                        .onSuccess { context.startActivity(NoteExporter.shareIntent(it, "application/pdf")) }
                                        .onFailure { snackbar.showSnackbar("Couldn't export: ${it.message.orEmpty()}") }
                                }
                            })
                            DropdownMenuItem(text = { Text("Share page as image") }, onClick = {
                                menuOpen = false
                                val page = state.pages.getOrNull(pageIndex) ?: return@DropdownMenuItem
                                scope.launch {
                                    runCatching { NoteExporter.exportPng(context, state.title, page, pageIndex + 1) }
                                        .onSuccess { context.startActivity(NoteExporter.shareIntent(it, "image/png")) }
                                        .onFailure { snackbar.showSnackbar("Couldn't export: ${it.message.orEmpty()}") }
                                }
                            })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Floating toolbar") },
                                trailingIcon = { Switch(checked = state.floatingToolbar, onCheckedChange = null) },
                                onClick = { vm.setFloatingToolbar(!state.floatingToolbar) }
                            )
                            DropdownMenuItem(
                                text = { Text("Draw with finger") },
                                trailingIcon = { Switch(checked = state.fingerInks, onCheckedChange = null) },
                                onClick = { vm.setFingerDrawing(!state.fingerInks) }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.imePadding().navigationBarsPadding()) {
                val editing = state.editingElementId?.let { id -> state.pages.flatMap { it.elements }.firstOrNull { it.id == id } }
                val selection = state.selection
                when {
                    editing != null -> {
                        val rich = textStates.get(editing)
                        TextFormatBar(rich) { color -> rich.toggleSpanStyle(SpanStyle(color = color)) }
                    }
                    selection != null -> {
                        val page = state.page(selection.pageId)
                        val source = page?.elements?.firstOrNull { it.id in selection.elementIds && it.sourceBookId != null && it.sourcePage != null }
                        SelectionBar(
                            canRecolor = selection.strokeIds.isNotEmpty(),
                            sourceAvailable = source != null,
                            isSticky = page?.elements?.any { it.id in selection.elementIds && it.fillColor != null } == true,
                            onStickyColor = vm::setStickyColor,
                            onDelete = vm::deleteSelection,
                            onDuplicate = vm::duplicateSelection,
                            onCopy = vm::copySelection,
                            onCut = vm::cutSelection,
                            onFront = vm::bringSelectionToFront,
                            onRecolor = vm::recolorSelection,
                            onOpenSource = { source?.let { onOpenSource(it.sourceBookId!!, it.sourcePage!!) } }
                        )
                    }
                    state.tool == EditorTool.PEN -> PenPresetBar(state, vm::selectPreset, onEditActive = { editingPen = true }, onAdd = vm::addPreset)
                    state.tool == EditorTool.SHAPE -> ShapeBar(state, vm::selectShape, onAuto = vm::selectAutoShape, onEditPen = { editingPen = true })
                    state.tool == EditorTool.ERASER -> EraserBar(state, vm::setEraserMode, vm::setEraserSize, onClearPage = { currentPageId()?.let(vm::clearPageInk) })
                    state.tool == EditorTool.TAPE -> TapeBar(state, vm::setTapeColor, vm::revealAllTapes)
                    state.tool == EditorTool.LASER -> HintBar("Point with your finger or stylus; the trail fades and is never saved.")
                }
                EditorToolBar(state, ToolbarActions(
                    onTool = vm::selectTool,
                    onSticky = { currentPageId()?.let { vm.addStickyNote(it, 0.34f, visiblePageY(viewport), StickyColors.first()) } },
                    onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCamera = {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "note_${System.currentTimeMillis()}.jpg")
                        cameraFile = file.absolutePath
                        runCatching { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) }
                            .onFailure { scope.launch { snackbar.showSnackbar("No camera app available") } }
                    },
                    onAddPage = { templateSheet = TemplateSheet.ADD },
                    onPaste = paste
                ))
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notFound -> Text("This note no longer exists", Modifier.align(Alignment.Center))
                else -> NoteCanvas(state, viewport, textStates, vm, onOpenSource, onAddPage = { templateSheet = TemplateSheet.ADD_AT_END })
            }
            if (state.floatingToolbar && !state.isLoading && !state.notFound && state.editingElementId == null) {
                val position = remember { vm.floatingPosition() }
                NoteFloatingToolbar(state, FloatingToolbarActions(
                    onTool = vm::selectTool,
                    onPreset = vm::selectPreset,
                    onEditPen = { editingPen = true },
                    onAddPen = vm::addPreset,
                    onShape = vm::selectShape,
                    onAutoShape = vm::selectAutoShape,
                    onPaste = paste,
                    initialPosition = position,
                    onMoved = vm::setFloatingPosition
                ))
            }
        }
    }

    if (editingPen) {
        state.activePen?.let { pen ->
            ModalBottomSheet(onDismissRequest = { editingPen = false }) {
                PenEditor(pen, canRemove = state.presets.size > 1, recentColors = state.recentColors, onChange = vm::updateActivePreset,
                    onCustomColor = vm::useCustomColor, onRemove = { vm.removeActivePreset(); editingPen = false }, onDone = { editingPen = false })
            }
        }
    }

    templateSheet?.let { mode ->
        ModalBottomSheet(onDismissRequest = { templateSheet = null }) {
            var applyAll by remember { mutableStateOf(false) }
            val pageIndex = viewport.currentPage()
            val current = state.pages.getOrNull(if (mode == TemplateSheet.ADD_AT_END) state.pages.lastIndex else pageIndex)?.background
            Text(if (mode == TemplateSheet.CHANGE) "Change page ${pageIndex + 1}" else "Add a page", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp))
            if (mode == TemplateSheet.CHANGE) {
                DropdownMenuItem(text = { Text("Apply to all pages") }, trailingIcon = { Switch(checked = applyAll, onCheckedChange = null) }, onClick = { applyAll = !applyAll })
            }
            PageTemplatePicker(selected = current, onPick = { template ->
                templateSheet = null
                when (mode) {
                    TemplateSheet.CHANGE -> currentPageId()?.let { vm.setBackground(it, template, applyAll) }
                    TemplateSheet.ADD -> { vm.addPage(pageIndex, template); pendingScroll = pageIndex + 1 }
                    TemplateSheet.ADD_AT_END -> { val last = state.pages.lastIndex; vm.addPage(last, template); pendingScroll = last + 1 }
                }
            })
        }
    }

    if (pagesSheet) {
        ModalBottomSheet(onDismissRequest = { pagesSheet = false }) {
            PageOverview(state.pages, viewport.currentPage(), onOpen = { index -> pagesSheet = false; viewport.scrollToPage(index) }, onMove = vm::movePage)
        }
    }

    confirmDeletePage?.let { pageId ->
        AlertDialog(
            onDismissRequest = { confirmDeletePage = null },
            title = { Text("Delete this page?") },
            text = { Text("Its handwriting, text, and images are removed. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.deletePage(pageId); confirmDeletePage = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDeletePage = null }) { Text("Cancel") } }
        )
    }
}
