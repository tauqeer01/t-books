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
import androidx.compose.foundation.gestures.drag
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
import com.bookflow.app.presentation.components.BookFlowBottomSheet
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
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.bookflow.app.pdf.engine.markupRects
import com.bookflow.app.pdf.engine.isStickyNote
import kotlin.math.roundToInt

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
            state.isPageNavigatorVisible -> viewModel.setPageNavigatorVisible(false)
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
        if (scope == null) BookFlowBottomSheet(
            onDismissRequest = { viewModel.showReadingPreferences(false) },
            title = "Reading Preferences",
            subtitle = "Choose how settings are applied",
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showReadingPreferences(false) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Surface(
                    onClick = { scope = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Customize for This Book", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                        Text("Overrides apply only while reading this PDF", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = { scope = false },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Change Global Defaults", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                        Text("Applies to all books in your library", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = {
                        viewModel.useGlobalPreferences()
                        viewModel.showReadingPreferences(false)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Reset to Global Defaults", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = BrandPurple)
                    }
                }
            }
        } else ReadingPreferencesEditor(state.preferences, onDismiss = { viewModel.showReadingPreferences(false) }, onSave = { viewModel.saveReadingPreferences(it, scope == true) })
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                Modifier.navigationBarsPadding().padding(
                    bottom = if (state.isPageNavigatorVisible) 80.dp else 12.dp
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
                    .pointerInput(state.isDrawingModeActive, state.isStylusOnlyDrawing, state.drawingTool) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val drawing = state.isDrawingModeActive && state.drawingTool != DrawingTool.HAND && (!state.isStylusOnlyDrawing || event.changes.any { it.type == androidx.compose.ui.input.pointer.PointerType.Stylus })
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
                                top = ReaderHeaderHeight + 12.dp,
                                bottom = 24.dp,
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
                    } else if (state.scrollMode == PageScrollMode.BOOK) {
                        // MODE 3: Book — Apple Books style page curl
                        BookPageTurner(
                            pageCount = state.pageCount,
                            currentPage = state.currentPage,
                            onPageChange = viewModel::onPageScrolled,
                            swipeEnabled = scale <= 1.05f && (!state.isDrawingModeActive || state.drawingTool == DrawingTool.HAND),
                            pageAspectRatio = viewModel::getPageAspectRatio,
                            modifier = Modifier.padding(top = ReaderHeaderHeight + 8.dp, bottom = 16.dp, start = 12.dp, end = 12.dp)
                        ) { pageIndex ->
                            PdfPageItem(
                                pageIndex = pageIndex,
                                viewModel = viewModel,
                                aspectRatio = viewModel.getPageAspectRatio(pageIndex),
                                annotations = state.allBookAnnotations.filter { it.pageIndex == pageIndex },
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
                                if (kotlin.math.abs(pagerState.currentPage - state.currentPage) == 1) {
                                    pagerState.animateScrollToPage(state.currentPage)
                                } else {
                                    pagerState.scrollToPage(state.currentPage)
                                }
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = state.scrollMode != PageScrollMode.SINGLE_PAGE && scale <= 1.05f,
                            pageSpacing = state.preferences.pageSpacing.dp,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = ReaderHeaderHeight + 8.dp,
                                bottom = 16.dp,
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
                        .padding(bottom = if (state.isPageNavigatorVisible) 80.dp else 24.dp, start = 16.dp, end = 16.dp)
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

            // --- TOP APP BAR (stays on screen; hidden only while a text selection popup is open) ---
            AnimatedVisibility(
                visible = state.selectedTextSelection == null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderHeader(
                    state = state,
                    onBackClick = onBackClick,
                    onContentsClick = { viewModel.setDrawerOpen(true, 0) },
                    onPageLabelClick = viewModel::openGoToPageDialog,
                    onSearchClick = { viewModel.setSearchOpen(!state.isSearchOpen) },
                    onBookmarkClick = viewModel::toggleBookmark,
                    onMoreClick = { viewModel.openMoreOptionsSheet(true) }
                )
            }

            // --- SEARCH OVERLAY BAR ---
            AnimatedVisibility(
                visible = state.isSearchOpen,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = ReaderHeaderHeight, start = 12.dp, end = 12.dp)
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

            // --- RIGHT ANNOTATION RAIL (pen button that expands into a vertical tool strip) ---
            AnimatedVisibility(
                visible = state.isDocumentReady && state.selectedTextSelection == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = ReaderHeaderHeight + 8.dp, bottom = 16.dp, end = 12.dp)
            ) {
                AnnotationToolRail(state = state, viewModel = viewModel)
            }

            // --- PAGE NAVIGATOR (opens when the page is tapped) ---
            AnimatedVisibility(
                visible = state.isDocumentReady && state.isPageNavigatorVisible && state.selectedTextSelection == null && !state.isDrawingModeActive,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PageNavigator(state = state, viewModel = viewModel)
            }

            if (state.isMoreOptionsSheetOpen) {
                ModalBottomSheet(onDismissRequest = { viewModel.openMoreOptionsSheet(false) }) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Reader options", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(14.dp))
                        Text("Reading mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                        Spacer(Modifier.height(8.dp))
                        ReadingModeSelector(selected = state.scrollMode, onSelect = viewModel::setScrollMode)
                        Spacer(Modifier.height(8.dp))
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

                BookFlowBottomSheet(
                    onDismissRequest = viewModel::closeGoToPageDialog,
                    title = "Jump to Page",
                    subtitle = "Enter a page number between 1 and ${state.pageCount}",
                    confirmButton = {
                        Button(
                            onClick = {
                                val p = inputPageText.toIntOrNull()
                                if (p != null && p in 1..state.pageCount) {
                                    viewModel.jumpToPage(p - 1)
                                }
                                viewModel.closeGoToPageDialog()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Jump", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::closeGoToPageDialog,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                    }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandPurple,
                                focusedLabelColor = BrandPurple
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("goto_page_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

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
                }
            }

            // --- DIALOG: EDIT NOTE ---
            if (state.editingNoteAnnotation != null) {
                val ann = state.editingNoteAnnotation!!
                var noteInput by remember { mutableStateOf(ann.noteContent) }

                BookFlowBottomSheet(
                    onDismissRequest = viewModel::cancelEditingNote,
                    title = "Edit Note",
                    confirmButton = {
                        Button(
                            onClick = { viewModel.saveAnnotationNote(ann.id, noteInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Note", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = viewModel::cancelEditingNote,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color(0xFF64748B))
                        }
                    }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandPurple,
                                focusedLabelColor = BrandPurple
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("edit_note_input")
                        )
                    }
                }
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
            val color = try {
                Color(android.graphics.Color.parseColor(ann.colorHex))
            } catch (_: Exception) {
                Color(0xFFFFE600)
            }
            when {
                ann.type == AnnotationType.PEN_DRAW -> Unit
                ann.isStickyNote -> drawStickyNote(
                    center = Offset((ann.rectLeft + ann.rectRight) / 2f * pageW, (ann.rectTop + ann.rectBottom) / 2f * pageH),
                    color = color,
                    hasText = ann.noteContent.isNotBlank()
                )
                ann.type == AnnotationType.NOTE -> {
                    // Highlight the text + draw a note indicator marker
                    drawTextMarkup(AnnotationType.HIGHLIGHT, ann.markupRects(), color.copy(alpha = 0.35f / 0.42f))
                    val right = ann.rectRight * pageW
                    val top = ann.rectTop * pageH
                    drawCircle(color = color, radius = 6.dp.toPx(), center = Offset(right - 2f, top + 2f))
                    drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(right - 2f, top + 2f))
                }
                else -> drawTextMarkup(ann.type, ann.markupRects(), color)
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

private val ReaderHeaderHeight = 56.dp

/**
 * Persistent reader header: back, title with tappable page indicator (opens Go to page), and reading tools.
 */
@Composable
private fun ReaderHeader(
    state: ReaderUiState,
    onBackClick: () -> Unit,
    onContentsClick: () -> Unit,
    onPageLabelClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = 0.97f),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().testTag("reader_header")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(ReaderHeaderHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.testTag("reader_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Go to page", onClick = onPageLabelClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = state.book?.title ?: "PDF Reader",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Page ${state.currentPage + 1} of ${state.pageCount}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            HeaderAction(Icons.AutoMirrored.Filled.ViewList, "Contents", "reader_contents_button", onClick = onContentsClick)
            HeaderAction(Icons.Default.Search, "Search", "reader_search_button", isActive = state.isSearchOpen, onClick = onSearchClick)
            HeaderAction(
                icon = if (state.isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = "Bookmark",
                testTag = "reader_bookmark_toggle",
                isActive = state.isCurrentPageBookmarked,
                onClick = onBookmarkClick
            )
            HeaderAction(Icons.Default.MoreVert, "Reader options", "reader_more_button", onClick = onMoreClick)
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    testTag: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp).testTag(testTag)) {
        Icon(icon, contentDescription = label, tint = if (isActive) BrandPurple else Color(0xFF334155), modifier = Modifier.size(22.dp))
    }
}

/**
 * Compact floating page bar shown on page tap: a slim scrubber with bookmark ticks, prev/next, and a
 * page chip (opens Go to page). While scrubbing, a preview bubble shows the target page's thumbnail and chapter.
 */
@Composable
private fun PageNavigator(
    state: ReaderUiState,
    viewModel: ReaderViewModel
) {
    val maxPage = (state.pageCount - 1).coerceAtLeast(0)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPage by remember(state.currentPage) { mutableIntStateOf(state.currentPage) }
    var trackStartX by remember { mutableFloatStateOf(0f) }
    var trackWidth by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableFloatStateOf(0f) }
    val shownPage = if (isScrubbing) scrubPage else state.currentPage
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .onGloballyPositioned { barWidth = it.size.width.toFloat() }
            .testTag("page_navigator")
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.97f),
            shadowElevation = 6.dp,
            border = BorderStroke(0.5.dp, Color(0x14000000)),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PageBarIcon(Icons.Default.ChevronLeft, "Previous page", enabled = state.currentPage > 0, onClick = viewModel::previousPage)
                PageScrubber(
                    page = shownPage,
                    maxPage = maxPage,
                    isScrubbing = isScrubbing,
                    bookmarkedPages = state.bookmarks.map { it.pageIndex },
                    onScrubStart = { isScrubbing = true },
                    onScrub = { scrubPage = it },
                    onScrubEnd = {
                        isScrubbing = false
                        viewModel.jumpToPage(scrubPage)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned {
                            trackStartX = it.positionInParent().x
                            trackWidth = it.size.width.toFloat()
                        }
                )
                PageBarIcon(Icons.Default.ChevronRight, "Next page", enabled = state.currentPage < maxPage, onClick = viewModel::nextPage)
                Surface(
                    onClick = viewModel::openGoToPageDialog,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .semantics { contentDescription = "Go to page" }
                ) {
                    Text(
                        text = "${shownPage + 1} / ${state.pageCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Scrub preview bubble, centred over the thumb and kept inside the bar
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = .9f),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset {
                    val bubbleWidth = with(density) { PreviewBubbleWidth.toPx() }
                    val inset = with(density) { ScrubberThumbInset.toPx() }
                    val fraction = if (maxPage == 0) 0f else scrubPage / maxPage.toFloat()
                    val thumbX = trackStartX + inset + fraction * (trackWidth - 2 * inset)
                    val x = (thumbX - bubbleWidth / 2f).coerceIn(0f, (barWidth - bubbleWidth).coerceAtLeast(0f))
                    androidx.compose.ui.unit.IntOffset(x.roundToInt(), -with(density) { 54.dp.roundToPx() })
                }
        ) {
            val chapter = state.outline.lastOrNull { it.pageIndex <= scrubPage }?.title
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 10.dp,
                modifier = Modifier.width(PreviewBubbleWidth)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 86.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniPageThumbnail(scrubPage, viewModel)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Page ${scrubPage + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    if (chapter != null) {
                        Text(chapter, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private val PreviewBubbleWidth = 112.dp
private val ScrubberThumbInset = 10.dp

@Composable
private fun PageBarIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color(0xFF334155) else Color(0xFFCBD5E1), modifier = Modifier.size(20.dp))
    }
}

/**
 * Thin page scrubber: 4dp track, bookmark ticks, and a ringed thumb that grows while dragging.
 * Tapping the track jumps there; dragging previews until release.
 */
@Composable
private fun PageScrubber(
    page: Int,
    maxPage: Int,
    isScrubbing: Boolean,
    bookmarkedPages: List<Int>,
    onScrubStart: () -> Unit,
    onScrub: (Int) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbRadius by androidx.compose.animation.core.animateDpAsState(if (isScrubbing) 9.dp else 7.dp, label = "thumb")
    val fraction = if (maxPage == 0) 0f else page / maxPage.toFloat()
    Canvas(
        modifier = modifier
            .height(32.dp)
            .semantics {
                contentDescription = "Page scrubber"
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(page.toFloat(), 0f..maxPage.toFloat().coerceAtLeast(1f))
            }
            .pointerInput(maxPage) {
                val inset = ScrubberThumbInset.toPx()
                fun pageAt(x: Float): Int {
                    val f = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    return (f * maxPage).roundToInt()
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onScrubStart()
                    onScrub(pageAt(down.position.x))
                    drag(down.id) { change ->
                        onScrub(pageAt(change.position.x))
                        change.consume()
                    }
                    onScrubEnd()
                }
            }
    ) {
        val inset = ScrubberThumbInset.toPx()
        val cy = size.height / 2f
        val start = inset
        val end = size.width - inset
        val thumbX = start + fraction * (end - start)
        val track = 4.dp.toPx()
        drawLine(Color(0xFFE2E8F0), Offset(start, cy), Offset(end, cy), strokeWidth = track, cap = StrokeCap.Round)
        drawLine(BrandPurple, Offset(start, cy), Offset(thumbX, cy), strokeWidth = track, cap = StrokeCap.Round)
        if (maxPage > 0) {
            bookmarkedPages.forEach { bm ->
                val x = start + bm / maxPage.toFloat() * (end - start)
                drawCircle(Color(0xFFA78BFA), radius = 2.dp.toPx(), center = Offset(x, cy - 7.dp.toPx()))
            }
        }
        val r = thumbRadius.toPx()
        drawCircle(Color.Black.copy(alpha = 0.12f), radius = r + 1.5.dp.toPx(), center = Offset(thumbX, cy + 1.dp.toPx()))
        drawCircle(Color.White, radius = r, center = Offset(thumbX, cy))
        drawCircle(BrandPurple, radius = r, center = Offset(thumbX, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
    }
}

/**
 * Segmented reading-mode switcher: scroll, swipe, single page, or book-style page curl.
 */
@Composable
private fun ReadingModeSelector(
    selected: PageScrollMode,
    onSelect: (PageScrollMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF1F5F9))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            Triple(PageScrollMode.CONTINUOUS_VERTICAL, Icons.Default.SwapVert, "Scroll"),
            Triple(PageScrollMode.HORIZONTAL_PAGING, Icons.Default.ViewAgenda, "Swipe"),
            Triple(PageScrollMode.SINGLE_PAGE, Icons.Default.CropFree, "Single"),
            Triple(PageScrollMode.BOOK, Icons.AutoMirrored.Filled.MenuBook, "Book")
        ).forEach { (mode, icon, label) ->
            val isSelected = mode == selected
            Surface(
                onClick = { onSelect(mode) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Color.White else Color.Transparent,
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier.weight(1f).testTag("reading_mode_${mode.name.lowercase()}")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = if (isSelected) BrandPurple else Color(0xFF64748B), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

/** Draws a highlight, underline or strikethrough over normalized line rects. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextMarkup(
    type: AnnotationType,
    rects: List<com.bookflow.app.pdf.engine.PdfRect>,
    color: Color
) {
    val pageW = size.width
    val pageH = size.height
    rects.forEach { r ->
        val left = r.left * pageW
        val top = r.top * pageH
        val right = r.right * pageW
        val bottom = r.bottom * pageH
        when (type) {
            AnnotationType.UNDERLINE -> drawLine(color, Offset(left, bottom), Offset(right, bottom), strokeWidth = 3.5f, cap = StrokeCap.Round)
            AnnotationType.STRIKETHROUGH -> {
                val midY = (top + bottom) / 2f
                drawLine(color, Offset(left, midY), Offset(right, midY), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            else -> drawRoundRect(
                color = color.copy(alpha = 0.42f * color.alpha),
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(10f), (bottom - top).coerceAtLeast(8f)),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

/** Sticky note glyph: a small square with a folded corner and ruled lines once it has text. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStickyNote(center: Offset, color: Color, hasText: Boolean) {
    val half = 11.dp.toPx()
    val fold = 6.dp.toPx()
    val left = center.x - half
    val top = center.y - half
    val right = center.x + half
    val bottom = center.y + half
    drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(left + 1.dp.toPx(), top + 2.dp.toPx()), Size(half * 2, half * 2), CornerRadius(3.dp.toPx()))
    val body = androidx.compose.ui.graphics.Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom - fold)
        lineTo(right - fold, bottom)
        lineTo(left, bottom)
        close()
    }
    drawPath(body, color)
    val corner = androidx.compose.ui.graphics.Path().apply {
        moveTo(right, bottom - fold)
        lineTo(right - fold, bottom - fold)
        lineTo(right - fold, bottom)
        close()
    }
    drawPath(corner, Color.Black.copy(alpha = 0.18f))
    if (hasText) {
        val ink = Color.Black.copy(alpha = 0.35f)
        listOf(0.32f, 0.52f, 0.72f).forEach { f ->
            val y = top + half * 2 * f
            drawLine(ink, Offset(left + 4.dp.toPx(), y), Offset(right - (if (f > 0.6f) fold + 2.dp.toPx() else 4.dp.toPx()), y), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
