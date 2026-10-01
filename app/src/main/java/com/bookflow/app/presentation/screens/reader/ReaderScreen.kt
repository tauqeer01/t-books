package com.bookflow.app.presentation.screens.reader

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.AutoStories
import com.bookflow.app.domain.model.PageScrollMode
import com.bookflow.app.domain.model.ReaderTheme
import com.bookflow.app.pdf.drawing.DrawingTool
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.bookflow.app.presentation.screens.settings.ReadingPreferencesEditor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.BookAnnotation

// Highlight Colors matching Phase 4 Requirements: Yellow, Green, Blue, Pink, Purple
val HighlightColorPalette = listOf(
    "Yellow" to "#FFE600",
    "Green" to "#4ADE80",
    "Blue" to "#38BDF8",
    "Pink" to "#F472B6",
    "Purple" to "#A78BFA"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ReaderWindowEffects(state, viewModel)

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }


    // Toast notifications
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // Back handler: Close overlays or reset zoom first, else exit reader
    BackHandler {
        when {
            state.isMoreOptionsSheetOpen -> viewModel.openMoreOptionsSheet(false)
            state.showReadingPreferences -> viewModel.showReadingPreferences(false)
            state.isDrawingModeActive -> viewModel.setDrawingMode(false)
            state.selectedTextSelection != null -> viewModel.clearSelection()
            state.activeAnnotationMenu != null -> viewModel.closeAnnotationMenu()
            state.editingNoteAnnotation != null -> viewModel.cancelEditingNote()
            state.isSearchOpen -> viewModel.setSearchOpen(false)
            state.isDrawerOpen -> viewModel.setDrawerOpen(false)
            state.showThumbnailsDrawer -> viewModel.closeThumbnailsDrawer()
            state.isGoToPageDialogOpen -> viewModel.closeGoToPageDialog()
            scale > 1.05f -> {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            }
            else -> onBackClick()
        }
    }

    if (state.showReadingPreferences) {
        var scope by remember { mutableStateOf<Boolean?>(null) }
        if (scope == null) AlertDialog(
            onDismissRequest = { viewModel.showReadingPreferences(false) },
            title = { Text("Reading preferences") },
            text = { Column {
                TextButton(onClick = { scope = true }) { Text("Customize this book") }
                TextButton(onClick = { scope = false }) { Text("Change global defaults") }
                TextButton(onClick = { viewModel.useGlobalPreferences(); viewModel.showReadingPreferences(false) }) { Text("Use global defaults for this book") }
            } },
            confirmButton = { TextButton(onClick = { viewModel.showReadingPreferences(false) }) { Text("Cancel") } }
        ) else ReadingPreferencesEditor(state.preferences, onDismiss = { viewModel.showReadingPreferences(false) }, onSave = { viewModel.saveReadingPreferences(it, scope == true) })
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                Modifier.navigationBarsPadding().padding(
                    bottom = if (state.areControlsVisible || state.isDrawingModeActive) 164.dp else 12.dp
                )
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize().testTag("reader_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(android.graphics.Color.parseColor(state.readerTheme.bgHex)))
        ) {
            if (!state.isDocumentReady) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.errorMessage != null) Text(state.errorMessage!!, modifier = Modifier.padding(24.dp))
                    else CircularProgressIndicator()
                }
            }
            // --- CORE READING CANVAS (Lazy Loading & Dual Mode) ---
            if (state.isDocumentReady) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .pointerInput(state.isDrawingModeActive, state.isStylusOnlyDrawing) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val drawing = state.isDrawingModeActive && (!state.isStylusOnlyDrawing || event.changes.any { it.type == androidx.compose.ui.input.pointer.PointerType.Stylus })
                                if (pressed >= 2 || (scale > 1.05f && !drawing && event.changes.none { it.isConsumed })) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    val maxX = size.width * (scale - 1) / 2
                                    val maxY = size.height * (scale - 1) / 2
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                ) {
                    if (state.scrollMode == PageScrollMode.CONTINUOUS_VERTICAL) {
                        // MODE 1: Vertical Continuous Scrolling (Lazy Loaded Pages)
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)

                        val visiblePage by remember {
                            derivedStateOf { listState.firstVisibleItemIndex }
                        }
                        LaunchedEffect(visiblePage) {
                            viewModel.onPageScrolled(visiblePage)
                        }

                        LaunchedEffect(state.navigationRequest) {
                            if (listState.firstVisibleItemIndex != state.currentPage) {
                                listState.scrollToItem(state.currentPage)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                top = if (state.areControlsVisible) 72.dp else 16.dp,
                                bottom = if (state.areControlsVisible) 180.dp else 24.dp,
                                start = 12.dp,
                                end = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(state.preferences.pageSpacing.dp),
                            userScrollEnabled = scale <= 1.05f,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.pageCount, key = { it }) { pageIndex ->
                                val pageAnns = state.allBookAnnotations.filter { it.pageIndex == pageIndex }
                                PdfPageItem(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    aspectRatio = viewModel.getPageAspectRatio(pageIndex),
                                    annotations = pageAnns,
                                    state = state,
                                    activeSelection = if (state.selectedTextSelection?.pageIndex == pageIndex) state.selectedTextSelection else null,
                                    onAnnotationClick = { ann -> viewModel.onAnnotationTapped(ann) },
                                    onDoubleTap = { scale = if (scale > 1.2f) 1f else 2.4f; offsetX = 0f; offsetY = 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(8.dp, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                    } else {
                        // MODE 2: Horizontal Page Flip / Single Page Pager
                        val pagerState = rememberPagerState(
                            initialPage = state.currentPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)),
                            pageCount = { state.pageCount }
                        )

                        LaunchedEffect(pagerState.currentPage) {
                            viewModel.onPageScrolled(pagerState.currentPage)
                        }

                        LaunchedEffect(state.navigationRequest) {
                            if (pagerState.currentPage != state.currentPage) {
                                pagerState.scrollToPage(state.currentPage)
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = state.scrollMode != PageScrollMode.SINGLE_PAGE && scale <= 1.05f,
                            pageSpacing = state.preferences.pageSpacing.dp,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = if (state.areControlsVisible) 64.dp else 12.dp,
                                bottom = if (state.areControlsVisible) 160.dp else 16.dp,
                                start = 12.dp,
                                end = 12.dp
                            )
                        ) { pageIndex ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val ratio = viewModel.getPageAspectRatio(pageIndex)
                                val pageAnns = state.allBookAnnotations.filter { it.pageIndex == pageIndex }
                                PdfPageItem(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    aspectRatio = ratio,
                                    annotations = pageAnns,
                                    state = state,
                                    activeSelection = if (state.selectedTextSelection?.pageIndex == pageIndex) state.selectedTextSelection else null,
                                    onAnnotationClick = { ann -> viewModel.onAnnotationTapped(ann) },
                                    onDoubleTap = { scale = if (scale > 1.2f) 1f else 2.4f; offsetX = 0f; offsetY = 0f },
                                    fitPageToViewport = true,
                                    modifier = Modifier
                                        .shadow(12.dp, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }

            // --- PHASE 4: ANNOTATION EDITING MENU (When existing highlight is tapped) ---
            if (state.activeAnnotationMenu != null) {
                val activeAnn = state.activeAnnotationMenu!!
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                ) {
                    ExistingAnnotationActionMenu(
                        annotation = activeAnn,
                        onColorChange = { newHex ->
                            viewModel.updateAnnotationColor(activeAnn.id, newHex)
                        },
                        onEditNote = {
                            viewModel.startEditingNote(activeAnn)
                        },
                        onDelete = {
                            viewModel.deleteAnnotation(activeAnn.id)
                        },
                        onDismiss = {
                            viewModel.closeAnnotationMenu()
                        }
                    )
                }
            }

            // --- TOP APP BAR (Full-screen Show/Hide Animation) ---
            AnimatedVisibility(
                visible = state.areControlsVisible && state.selectedTextSelection == null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back + Title + Page count
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.size(38.dp).testTag("reader_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFF0F172A)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Column {
                                Text(
                                    text = state.book?.title ?: "PDF Reader",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Page ${state.currentPage + 1} of ${state.pageCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Actions: Scroll Mode Switcher, Outline/TOC, Search, Bookmark
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.setSearchOpen(!state.isSearchOpen) },
                                modifier = Modifier.size(38.dp).testTag("reader_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF334155)
                                )
                            }

                            IconButton(
                                onClick = viewModel::toggleBookmark,
                                modifier = Modifier.size(38.dp).testTag("reader_bookmark_toggle")
                            ) {
                                Icon(
                                    imageVector = if (state.isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (state.isCurrentPageBookmarked) BrandPurple else Color(0xFF334155)
                                )
                            }

                            IconButton(onClick = { viewModel.openMoreOptionsSheet(true) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, "Reader options", tint = Color(0xFF334155))
                            }
                        }
                    }
                }
            }

            // --- SEARCH OVERLAY BAR ---
            AnimatedVisibility(
                visible = state.isSearchOpen,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (state.areControlsVisible) 58.dp else 12.dp, start = 12.dp, end = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text("Search text, chapters, keywords in document...", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = BrandPurple)
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.onSearchQueryChanged("") },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = Color(0xFF64748B))
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.setSearchOpen(false) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Close Search", tint = Color(0xFF1E293B), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandPurple,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("pdf_search_input")
                        )

                        if (state.searchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Match Navigation Strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Match ${state.currentSearchMatchIndex + 1} of ${state.searchResults.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPurple
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = viewModel::previousSearchResult,
                                        modifier = Modifier.size(28.dp).testTag("search_prev_match")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", tint = Color(0xFF334155))
                                    }
                                    IconButton(
                                        onClick = viewModel::nextSearchResult,
                                        modifier = Modifier.size(28.dp).testTag("search_next_match")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", tint = Color(0xFF334155))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Matches list with: Matched text, Page number, Context around result
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(state.searchResults) { result ->
                                    val isCurrentMatch = state.activeSearchHighlight == result
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.jumpToSearchResult(result)
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrentMatch) Color(0xFFEEF2FF) else Color(0xFFF8FAFC)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (isCurrentMatch) BrandPurple else Color(0xFFE2E8F0)
                                                    ) {
                                                        Text(
                                                            text = "Page ${result.pageIndex + 1}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isCurrentMatch) Color.White else Color(0xFF334155),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "\"${result.matchedText}\"",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                }
                                                if (isCurrentMatch) {
                                                    Text(
                                                        text = "Active",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = BrandPurple,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Context around result
                                            Text(
                                                text = result.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF475569),
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (state.isSearching) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Searching document…", modifier = Modifier.padding(12.dp))
                        } else if (state.searchError != null) {
                            Text(state.searchError!!, modifier = Modifier.padding(12.dp))
                        } else if (state.searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No matches found for \"${state.searchQuery}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // --- FLOATING VERTICAL TOOLBAR (Right Side) ---
            AnimatedVisibility(
                visible = state.areControlsVisible && state.selectedTextSelection == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .shadow(8.dp, RoundedCornerShape(28.dp)),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VerticalToolbarIcon(
                            icon = Icons.Default.Create,
                            isActive = state.isDrawingModeActive && state.drawingTool == DrawingTool.PEN,
                            onClick = {
                                viewModel.setDrawingMode(true)
                                viewModel.setDrawingTool(DrawingTool.PEN)
                            }
                        )
                        VerticalToolbarIcon(
                            icon = Icons.Default.Highlight,
                            isActive = state.isDrawingModeActive && state.drawingTool == DrawingTool.HIGHLIGHTER,
                            onClick = {
                                viewModel.setDrawingMode(true)
                                viewModel.setDrawingTool(DrawingTool.HIGHLIGHTER)
                            }
                        )
                        VerticalToolbarIcon(
                            icon = Icons.Default.CropFree,
                            isActive = state.isDrawingModeActive && state.drawingTool == DrawingTool.ERASER,
                            onClick = {
                                viewModel.setDrawingMode(true)
                                viewModel.setDrawingTool(DrawingTool.ERASER)
                            }
                        )
                        VerticalToolbarIcon(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            isActive = false,
                            onClick = viewModel::undoDrawing
                        )
                        VerticalToolbarIcon(
                            icon = Icons.AutoMirrored.Filled.Redo,
                            isActive = false,
                            onClick = viewModel::redoDrawing
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.isDocumentReady && state.areControlsVisible && state.selectedTextSelection == null && !state.isDrawingModeActive,
                enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                    val thumbnailState = rememberLazyListState()
                    LaunchedEffect(state.currentPage) { thumbnailState.animateScrollToItem((state.currentPage - 2).coerceAtLeast(0)) }
                    LazyRow(state = thumbnailState, modifier = Modifier.fillMaxWidth().background(Color(0xFFE2E2E9)), contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.pageCount) { page ->
                            Column(Modifier.width(60.dp).clickable { viewModel.jumpToPage(page) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.height(70.dp).fillMaxWidth().border(if (page == state.currentPage) 2.dp else 1.dp, if (page == state.currentPage) BrandPurple else Color(0xFFCCCCD8), RoundedCornerShape(5.dp)).padding(3.dp).background(Color.White), contentAlignment = Alignment.Center) {
                                    MiniPageThumbnail(page, viewModel)
                                }
                                Text("${page + 1}", fontSize = 10.sp, color = Color(0xFF101326))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.setDrawerOpen(true, 0) }) { Icon(Icons.Default.Menu, "Contents, bookmarks and history") }
                        IconButton(onClick = viewModel::previousPage, enabled = state.currentPage > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            var scrubPage by remember(state.currentPage) { mutableFloatStateOf(state.currentPage.toFloat()) }
                            Slider(value = scrubPage, onValueChange = { scrubPage = it }, onValueChangeFinished = { viewModel.jumpToPage(scrubPage.toInt()) }, valueRange = 0f..(state.pageCount - 1).coerceAtLeast(1).toFloat(), enabled = state.pageCount > 1, modifier = Modifier.height(32.dp))
                            Text("${state.currentPage + 1} / ${state.pageCount}", fontSize = 11.sp, color = Color(0xFF101326), modifier = Modifier.clickable { viewModel.openGoToPageDialog() })
                        }
                        IconButton(onClick = viewModel::nextPage, enabled = state.currentPage < state.pageCount - 1) { Icon(Icons.Default.ChevronRight, "Next page") }
                        IconButton(onClick = viewModel::openGoToPageDialog) { Icon(Icons.Default.FindInPage, "Go to page") }
                    }
                }
            }

            if (state.isMoreOptionsSheetOpen) {
                ModalBottomSheet(onDismissRequest = { viewModel.openMoreOptionsSheet(false) }) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Reader options", style = MaterialTheme.typography.titleLarge)
                        listOf(
                            "Reading preferences" to { viewModel.showReadingPreferences(true) },
                            "Contents & navigation" to { viewModel.setDrawerOpen(true, 0) },
                            "Page thumbnails" to { viewModel.openThumbnailsDrawer() },
                            "Bookmarks" to { viewModel.setDrawerOpen(true, 1) },
                            "Annotations" to { viewModel.setDrawerOpen(true, 2) },
                            "Reading history" to { viewModel.setDrawerOpen(true, 3) },
                            "Export annotated copy" to { viewModel.exportAnnotatedPdf() }
                        ).forEach { (label, action) ->
                            TextButton(onClick = { viewModel.openMoreOptionsSheet(false); action() }, modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }

            // --- DIALOG: JUMP TO PAGE ---
            if (state.isGoToPageDialogOpen) {
                var inputPageText by remember { mutableStateOf("${state.currentPage + 1}") }
                var sliderPageValue by remember { mutableFloatStateOf((state.currentPage + 1).toFloat()) }

                AlertDialog(
                    onDismissRequest = viewModel::closeGoToPageDialog,
                    title = {
                        Text("Jump to Page", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column {
                            Text(
                                text = "Enter a page number between 1 and ${state.pageCount}:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = inputPageText,
                                onValueChange = { text ->
                                    inputPageText = text.filter { it.isDigit() }
                                    val p = inputPageText.toIntOrNull()
                                    if (p != null && p in 1..state.pageCount) {
                                        sliderPageValue = p.toFloat()
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        val p = inputPageText.toIntOrNull()
                                        if (p != null && p in 1..state.pageCount) {
                                            viewModel.jumpToPage(p - 1)
                                            viewModel.closeGoToPageDialog()
                                        }
                                    }
                                ),
                                label = { Text("Page Number") },
                                modifier = Modifier.fillMaxWidth().testTag("goto_page_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Slider(
                                value = sliderPageValue,
                                onValueChange = {
                                    sliderPageValue = it
                                    inputPageText = it.toInt().toString()
                                },
                                valueRange = 1f..state.pageCount.coerceAtLeast(1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = BrandPurple,
                                    activeTrackColor = BrandPurple
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val p = inputPageText.toIntOrNull()
                                if (p != null && p in 1..state.pageCount) {
                                    viewModel.jumpToPage(p - 1)
                                }
                                viewModel.closeGoToPageDialog()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                        ) {
                            Text("Jump")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = viewModel::closeGoToPageDialog) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- DIALOG: EDIT NOTE ---
            if (state.editingNoteAnnotation != null) {
                val ann = state.editingNoteAnnotation!!
                var noteInput by remember { mutableStateOf(ann.noteContent) }

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
                                value = noteInput,
                                onValueChange = { noteInput = it },
                                placeholder = { Text("Enter your personal note or thoughts...") },
                                minLines = 3,
                                maxLines = 6,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPurple),
                                modifier = Modifier.fillMaxWidth().testTag("edit_note_input")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.saveAnnotationNote(ann.id, noteInput) },
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

            // --- MODAL BOTTOM SHEET: FULL PAGE THUMBNAILS GRID ---
            if (state.showThumbnailsDrawer) {
                ModalBottomSheet(
                    onDismissRequest = viewModel::closeThumbnailsDrawer,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page Thumbnails",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.pageCount} pages",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        ) {
                            items(state.pageCount, key = { it }) { pIndex ->
                                val isCurrent = pIndex == state.currentPage
                                val isBm = state.bookmarks.any { it.pageIndex == pIndex }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.jumpToPage(pIndex)
                                            viewModel.closeThumbnailsDrawer()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.72f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isCurrent) 3.dp else 1.dp,
                                                color = if (isCurrent) BrandPurple else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .background(Color(0xFFF8FAFC)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MiniPageThumbnail(
                                            pageIndex = pIndex,
                                            viewModel = viewModel
                                        )

                                        if (isBm) {
                                            Icon(
                                                imageVector = Icons.Default.Bookmark,
                                                contentDescription = null,
                                                tint = BrandPurple,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Page ${pIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) BrandPurple else Color(0xFF334155),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- MODAL BOTTOM SHEET: TABLE OF CONTENTS, BOOKMARKS, ANNOTATIONS ---
            if (state.isDrawerOpen) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.setDrawerOpen(false) },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    var selectedTab by remember { mutableIntStateOf(state.activeDrawerTab) }

                    Column(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                        androidx.compose.material3.ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            edgePadding = 0.dp,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = BrandPurple
                                )
                            }
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Contents", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Bookmarks (${state.bookmarks.size})", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Highlights (${state.allBookAnnotations.size})", fontWeight = FontWeight.Bold) }
                            )
                            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("History") })
                        }

                        when (selectedTab) {
                            3 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                                if (state.readingHistory.isEmpty()) item { Text("Pages you visit appear here.") }
                                items(state.readingHistory.asReversed()) { page ->
                                    TextButton(onClick = { viewModel.jumpToPage(page); viewModel.setDrawerOpen(false) }, modifier = Modifier.fillMaxWidth()) { Text("Page ${page + 1}") }
                                }
                            }
                            0 -> {
                                if (state.outline.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No outline available for this document.", color = Color(0xFF64748B))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(state.outline) { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.jumpToPage(item.pageIndex)
                                                        viewModel.setDrawerOpen(false)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF0F172A),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${item.pageIndex + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = BrandPurple,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (state.bookmarks.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No bookmarks yet. Tap the bookmark icon to save pages.", color = Color(0xFF64748B))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(state.bookmarks) { bm ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.jumpToPage(bm.pageIndex)
                                                        viewModel.setDrawerOpen(false)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = BrandPurple)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = "Page ${bm.pageIndex + 1}",
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        if (bm.label.isNotBlank()) {
                                                            Text(text = bm.label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                                        }
                                                    }
                                                }
                                                IconButton(onClick = { viewModel.toggleBookmark() }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF94A3B8))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                if (state.allBookAnnotations.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No highlights or notes yet. Long-press text to annotate.", color = Color(0xFF64748B))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(state.allBookAnnotations) { ann ->
                                            val annColor = try {
                                                Color(android.graphics.Color.parseColor(ann.colorHex))
                                            } catch (_: Exception) {
                                                Color(0xFFFFE600)
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.jumpToPage(ann.pageIndex)
                                                        viewModel.setDrawerOpen(false)
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
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
                                                                    .size(12.dp)
                                                                    .clip(CircleShape)
                                                                    .background(annColor)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "Page ${ann.pageIndex + 1} • ${ann.type.name}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = BrandPurple,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Row {
                                                            IconButton(
                                                                onClick = { viewModel.startEditingNote(ann) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Edit, contentDescription = "Edit Note", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                                            }
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            IconButton(
                                                                onClick = { viewModel.deleteAnnotation(ann.id) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                    if (ann.selectedText.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "\"${ann.selectedText}\"",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color(0xFF0F172A),
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (ann.noteContent.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Surface(
                                                            color = Color.White,
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = "Note: ${ann.noteContent}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color(0xFF334155),
                                                                modifier = Modifier.padding(8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- PHASE 6: DRAWING & PEN ANNOTATION TOOLBAR ---
            AnimatedVisibility(
                visible = state.isDrawingModeActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                DrawingToolbar(
                    state = state,
                    viewModel = viewModel
                )
            }

            // --- PHASE 6: EXPORT ANNOTATED PDF DIALOG ---
            if (state.showExportDialog) {
                ExportAnnotatedPdfDialog(
                    state = state,
                    onDismiss = viewModel::dismissExportDialog
                )
            }
        }
    }
}

/**
 * Lazy Page Item: Renders a PDF page asynchronously when visible.
 * Integrates long-press selectable text and normalized highlight rendering!
 */
@Composable
fun PdfPageItem(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    aspectRatio: Float,
    annotations: List<BookAnnotation>,
    state: ReaderUiState,
    activeSelection: com.bookflow.app.pdf.engine.PdfTextSelection?,
    onAnnotationClick: (BookAnnotation) -> Unit,
    onDoubleTap: () -> Unit = {},
    fitPageToViewport: Boolean = false,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var actualRatio by remember(pageIndex) { mutableFloatStateOf(aspectRatio) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex, state.preferences.highResolutionRendering, state.isDocumentReady) {
        isLoading = true
        actualRatio = viewModel.loadPageAspectRatio(pageIndex)
        val bitmap = viewModel.loadPageBitmap(pageIndex)
        pageBitmap = bitmap
        isLoading = false
    }

    DisposableEffect(pageIndex) {
        onDispose {
            pageBitmap = null
            viewModel.evictPage(pageIndex)
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(actualRatio, matchHeightConstraintsFirst = fitPageToViewport)
            .pointerInput(pageIndex, annotations, state.isDrawingModeActive) {
                if (!state.isDrawingModeActive) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { tapOffset ->
                            val normX = tapOffset.x / size.width.toFloat()
                            val normY = tapOffset.y / size.height.toFloat()
                            viewModel.onPageLongPressed(pageIndex, normX, normY)
                        },
                        onTap = { tapOffset ->
                            val normX = tapOffset.x / size.width.toFloat()
                            val normY = tapOffset.y / size.height.toFloat()

                            // Check if tap hit any existing annotation!
                            val hit = annotations.find { ann ->
                                normX in (ann.rectLeft - 0.03f)..(ann.rectRight + 0.03f) &&
                                normY in (ann.rectTop - 0.03f)..(ann.rectBottom + 0.03f)
                            }

                            if (hit != null) {
                                onAnnotationClick(hit)
                            } else {
                                viewModel.onPageTapped(pageIndex, normX, normY)
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap != null && !pageBitmap!!.isRecycled) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize()
            )

            if (state.preferences.nightTreatment) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
            if (state.isSearchHighlightActive && state.activeSearchHighlight?.pageIndex == pageIndex) {
                Canvas(Modifier.fillMaxSize()) {
                    state.activeSearchHighlight.bounds.forEach { rect ->
                        drawRect(Color(0xFFFFC400).copy(alpha = .5f), Offset(rect.left * size.width, rect.top * size.height), Size(rect.width * size.width, rect.height * size.height))
                    }
                }
            }

            // Overlaid Highlights Canvas (Stored & Scaled purely via normalized coordinates)
            AnnotationOverlayCanvas(
                annotations = annotations,
                activeSelection = activeSelection,
                modifier = Modifier.fillMaxSize()
            )

            if (activeSelection != null) {
                val bottom = activeSelection.highlightRects.maxOfOrNull { it.bottom } ?: .5f
                val provider = remember(bottom) {
                    object : androidx.compose.ui.window.PopupPositionProvider {
                        override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                            layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize): androidx.compose.ui.unit.IntOffset {
                            val below = anchorBounds.top + (anchorBounds.height * bottom).toInt() + 12
                            val y = if (below + popupContentSize.height < windowSize.height) below else below - popupContentSize.height - 36
                            return androidx.compose.ui.unit.IntOffset((windowSize.width - popupContentSize.width) / 2, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
                        }
                    }
                }
                androidx.compose.ui.window.Popup(popupPositionProvider = provider, onDismissRequest = viewModel::clearSelection) {
                    Box(Modifier.widthIn(max = 420.dp).padding(8.dp)) {
                        TextSelectionContextBar(activeSelection.text, state.activeHighlightColorHex, viewModel::selectHighlightColor,
                            { viewModel.createAnnotationFromSelection(AnnotationType.HIGHLIGHT) },
                            { viewModel.createAnnotationFromSelection(AnnotationType.UNDERLINE) },
                            { viewModel.createAnnotationFromSelection(AnnotationType.STRIKETHROUGH) },
                            { viewModel.createAnnotationFromSelection(AnnotationType.NOTE) },
                            viewModel::copySelectionToClipboard, viewModel::clearSelection)
                    }
                }
            }

            // Overlaid Freehand Drawing Canvas (Pen, Highlighter, Eraser)
            DrawingPageCanvas(
                pageIndex = pageIndex,
                annotations = annotations,
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = BrandPurple,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text(
                        text = "Page ${pageIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

/**
 * Annotation Overlay Canvas:
 * Draws highlights, underlines, strikethroughs, and notes using PDF page-relative coordinates.
 * Stored coordinates are relative to PDF page coordinate space (normalized 0.0..1.0).
 * They remain 100% accurately positioned across zoom, rotation, and screen resizing.
 */
@Composable
fun AnnotationOverlayCanvas(
    annotations: List<BookAnnotation>,
    activeSelection: com.bookflow.app.pdf.engine.PdfTextSelection?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val pageW = size.width
        val pageH = size.height

        // 1. Draw Saved Annotations
        annotations.forEach { ann ->
            val left = ann.rectLeft * pageW
            val top = ann.rectTop * pageH
            val right = ann.rectRight * pageW
            val bottom = ann.rectBottom * pageH

            val color = try {
                Color(android.graphics.Color.parseColor(ann.colorHex))
            } catch (_: Exception) {
                Color(0xFFFFE600)
            }

            when (ann.type) {
                AnnotationType.HIGHLIGHT -> {
                    drawRoundRect(
                        color = color.copy(alpha = 0.42f),
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(10f), (bottom - top).coerceAtLeast(8f)),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
                AnnotationType.UNDERLINE -> {
                    drawLine(
                        color = color,
                        start = Offset(left, bottom),
                        end = Offset(right, bottom),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )
                }
                AnnotationType.STRIKETHROUGH -> {
                    val midY = top + (bottom - top) / 2f
                    drawLine(
                        color = color,
                        start = Offset(left, midY),
                        end = Offset(right, midY),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
                AnnotationType.NOTE -> {
                    // Highlight the text + draw a note indicator marker
                    drawRoundRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(10f), (bottom - top).coerceAtLeast(8f)),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawCircle(
                        color = color,
                        radius = 6.dp.toPx(),
                        center = Offset(right - 2f, top + 2f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(right - 2f, top + 2f)
                    )
                }
                else -> {}
            }
        }

        // 2. Draw Active Text Selection highlight
        activeSelection?.highlightRects?.forEach { rect ->
            val left = rect.left * pageW
            val top = rect.top * pageH
            val right = rect.right * pageW
            val bottom = rect.bottom * pageH

            drawRoundRect(
                color = BrandPurple.copy(alpha = 0.28f),
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(10f), (bottom - top).coerceAtLeast(8f)),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

/**
 * Phase 4 Contextual Toolbar (Shown on Long Press Text Selection)
 * Features:
 * - Highlight colors (Yellow, Green, Blue, Pink, Purple)
 * - Actions: Highlight, Underline, Strikethrough, Add Note, Copy
 */
@Composable
fun TextSelectionContextBar(
    selectedText: String,
    selectedColorHex: String,
    onColorSelected: (String) -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onStrikethrough: () -> Unit,
    onAddNote: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth().testTag("contextual_toolbar")) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HighlightColorPalette.forEach { (name, hex) ->
                IconButton(onClick = { onColorSelected(hex) }, modifier = Modifier.size(30.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (selectedColorHex.equals(hex, true)) 2.dp else 0.dp, BrandPurple, CircleShape))
                    if (selectedColorHex.equals(hex, true)) Icon(Icons.Default.Check, "Selected $name", Modifier.size(12.dp), tint = Color(0xFF101326))
                }
            }
            ContextBarActionButton(Icons.Default.FormatColorText, "Highlight", onHighlight)
            ContextBarActionButton(Icons.Default.FormatUnderlined, "Underline", onUnderline)
            ContextBarActionButton(Icons.Default.FormatStrikethrough, "Strikethrough", onStrikethrough)
            ContextBarActionButton(Icons.Default.ChatBubbleOutline, "Add note", onAddNote)
            ContextBarActionButton(Icons.Default.ContentCopy, "Copy", onCopy)
            IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Clear, "Dismiss selection", Modifier.size(16.dp), tint = Color(0xFF101326)) }
        }
    }
}

@Composable
private fun ContextBarActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, label, tint = Color(0xFF101326), modifier = Modifier.size(18.dp))
    }
}

/**
 * Phase 4 Existing Annotation Action Menu:
 * Shown when tapping a highlighted text.
 * Features:
 * - Change color (5 swatches)
 * - Edit note
 * - Delete highlight
 */
@Composable
fun ExistingAnnotationActionMenu(
    annotation: BookAnnotation,
    onColorChange: (String) -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().testTag("annotation_action_menu")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${annotation.type.name} on Page ${annotation.pageIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101326)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Dismiss", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            if (annotation.noteContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: ${annotation.noteContent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Color swatches to change color
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Change Color:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )

                HighlightColorPalette.forEach { (_, hex) ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = annotation.colorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.5.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onColorChange(hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onEditNote,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (annotation.noteContent.isBlank()) "Add Note" else "Edit Note")
                }

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

/**
 * Fast thumbnail renderer for bottom bar and grid sheet.
 */
@Composable
private fun MiniPageThumbnail(
    pageIndex: Int,
    viewModel: ReaderViewModel
) {
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        thumbBitmap = viewModel.loadThumbnailBitmap(pageIndex)
    }

    if (thumbBitmap != null && !thumbBitmap!!.isRecycled) {
        Image(
            bitmap = thumbBitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VerticalToolbarIcon(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isActive) BrandPurple else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = icon.name.substringAfterLast('.'),
            tint = if (isActive) Color.White else Color(0xFF475569),
            modifier = Modifier.size(18.dp)
        )
    }
}
