package com.bookflow.app.presentation.screens.reader

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.core.util.FileUtils
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.pdf.drawing.DrawingStroke
import com.bookflow.app.pdf.drawing.DrawingTool
import com.bookflow.app.pdf.drawing.NormalizedPoint
import com.bookflow.app.pdf.drawing.ShapeGeometry
import com.bookflow.app.pdf.engine.PdfTextSelection
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.bookflow.app.pdf.drawing.StrokeSerializer
import com.bookflow.app.pdf.exporter.PdfAnnotatedExporter

private val PenColorPalette = listOf(
    "#0F172A", // Ink Black
    "#5935FF", // Brand Purple
    "#2563EB", // Royal Blue
    "#0EA5E9", // Sky
    "#10B981", // Emerald Green
    "#F97316", // Amber / Orange
    "#EF4444", // Crimson Red
    "#EC4899"  // Pink
)

private val HighlighterColorPalette = listOf(
    "#FFE066", // Yellow
    "#86EFAC", // Mint Green
    "#7DD3FC", // Sky Blue
    "#F472B6", // Pink
    "#C084FC", // Lilac
    "#FDBA74"  // Peach
)

private val InkWidthPresets = listOf(2.0f to "Fine", 4.0f to "Medium", 8.0f to "Bold", 16.0f to "Marker")
private val HighlighterWidthPresets = listOf(8.0f to "Thin", 14.0f to "Regular", 20.0f to "Wide", 28.0f to "Extra wide")
private val ShapeTools = listOf(DrawingTool.LINE, DrawingTool.ARROW, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE)
private val TextMarkupTools = listOf(DrawingTool.TEXT_HIGHLIGHT, DrawingTool.TEXT_UNDERLINE, DrawingTool.TEXT_STRIKETHROUGH)

private val RailInk = Color(0xFF334155)
private val RailSelectedBg = Color(0xFFEDE9FE)
private val RailDividerColor = Color(0xFFE2E8F0)

private enum class RailFlyout { TEXT, SHAPES, COLOR, WIDTH }

private val EraserIcon: ImageVector by lazy {
    ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes(
                "M16.24,3.56l4.95,4.94c0.78,0.79 0.78,2.05 0,2.84L12,20.53a4.008,4.008 0,0 1,-5.66 0L2.81,17" +
                    "c-0.78,-0.79 -0.78,-2.05 0,-2.84l10.6,-10.6c0.79,-0.78 2.05,-0.78 2.83,0M4.22,15.58l3.54,3.53" +
                    "c0.78,0.79 2.04,0.79 2.83,0l3.53,-3.53l-4.95,-4.95l-4.95,4.95z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

private fun DrawingTool.icon(): ImageVector = when (this) {
    DrawingTool.PEN -> Icons.Default.Create
    DrawingTool.HIGHLIGHTER -> Icons.Default.Highlight
    DrawingTool.ERASER -> EraserIcon
    DrawingTool.LINE -> Icons.Default.HorizontalRule
    DrawingTool.ARROW -> Icons.Default.NorthEast
    DrawingTool.RECTANGLE -> Icons.Default.CropSquare
    DrawingTool.ELLIPSE -> Icons.Default.RadioButtonUnchecked
    DrawingTool.TEXT_HIGHLIGHT -> Icons.Default.FormatColorText
    DrawingTool.TEXT_UNDERLINE -> Icons.Default.FormatUnderlined
    DrawingTool.TEXT_STRIKETHROUGH -> Icons.Default.FormatStrikethrough
    DrawingTool.STICKY_NOTE -> Icons.AutoMirrored.Filled.StickyNote2
    DrawingTool.HAND -> Icons.Default.PanTool
}

private fun parseHex(hex: String, fallback: Color = BrandPurple): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }

/**
 * Right-edge annotation rail. Collapsed it is a single pen button; tapping it enters drawing mode and expands
 * a slim vertical strip with tools, style pickers (as side flyouts) and history actions.
 */
@Composable
fun AnnotationToolRail(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    var flyout by remember { mutableStateOf<RailFlyout?>(null) }
    // Kept separately so the flyout keeps its content while animating out
    var lastFlyout by remember { mutableStateOf(RailFlyout.COLOR) }
    LaunchedEffect(state.isDrawingModeActive) { flyout = null }
    val styleFlyoutHidden = (flyout == RailFlyout.COLOR && !state.drawingTool.hasColor) ||
        (flyout == RailFlyout.WIDTH && !state.drawingTool.hasStrokeWidth)

    fun toggle(target: RailFlyout) {
        if (flyout == target) flyout = null else { flyout = target; lastFlyout = target }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        AnimatedVisibility(
            visible = flyout != null && state.isDrawingModeActive && !styleFlyoutHidden,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 })
        ) {
            RailFlyoutCard(
                flyout = lastFlyout,
                state = state,
                onTool = { viewModel.setDrawingTool(it); flyout = null },
                onColor = { viewModel.setDrawingColor(it); flyout = null },
                onWidth = { viewModel.setStrokeWidth(it); flyout = null }
            )
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = state.isDrawingModeActive,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = .85f, transformOrigin = TransformOrigin(1f, 1f))) togetherWith
                    (fadeOut() + scaleOut(targetScale = .85f, transformOrigin = TransformOrigin(1f, 1f)))
            },
            contentAlignment = Alignment.BottomEnd,
            label = "annotation_rail"
        ) { expanded ->
            if (!expanded) {
                Surface(
                    onClick = {
                        viewModel.setDrawingMode(true)
                        viewModel.setDrawingTool(DrawingTool.PEN)
                    },
                    shape = CircleShape,
                    color = BrandPurple,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(48.dp).testTag("annotation_rail_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Create, contentDescription = "Annotate", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    modifier = Modifier.testTag("drawing_toolbar")
                ) {
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val tool = state.drawingTool
                        fun select(t: DrawingTool) { viewModel.setDrawingTool(t); flyout = null }
                        RailButton(DrawingTool.HAND.icon(), "Scroll", tool == DrawingTool.HAND, "tool_hand") { select(DrawingTool.HAND) }
                        RailButton(DrawingTool.PEN.icon(), "Pen", tool == DrawingTool.PEN, "tool_pen") { select(DrawingTool.PEN) }
                        RailButton(DrawingTool.HIGHLIGHTER.icon(), "Highlighter", tool == DrawingTool.HIGHLIGHTER, "tool_highlighter") {
                            select(DrawingTool.HIGHLIGHTER)
                        }
                        RailButton(
                            icon = if (tool.isTextMarkup) tool.icon() else Icons.Default.FormatColorText,
                            label = "Text markup",
                            isSelected = tool.isTextMarkup,
                            testTag = "tool_text",
                            hasFlyout = true
                        ) {
                            // First tap picks a text tool right away; tapping again offers the other styles
                            if (!tool.isTextMarkup) viewModel.setDrawingTool(DrawingTool.TEXT_HIGHLIGHT)
                            toggle(RailFlyout.TEXT)
                        }
                        RailButton(
                            icon = if (tool.isShape) tool.icon() else Icons.Default.Category,
                            label = "Shapes",
                            isSelected = tool.isShape,
                            testTag = "tool_shapes",
                            hasFlyout = true
                        ) { toggle(RailFlyout.SHAPES) }
                        RailButton(DrawingTool.STICKY_NOTE.icon(), "Sticky note", tool == DrawingTool.STICKY_NOTE, "tool_sticky_note") {
                            select(DrawingTool.STICKY_NOTE)
                        }
                        RailButton(DrawingTool.ERASER.icon(), "Eraser", tool == DrawingTool.ERASER, "tool_eraser") { select(DrawingTool.ERASER) }

                        if (tool.hasColor) {
                            RailDivider()
                            RailStyleButton("Color", "tool_color", flyout == RailFlyout.COLOR, { toggle(RailFlyout.COLOR) }) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(parseHex(state.drawingColorHex))
                                        .border(1.dp, Color(0x22000000), CircleShape)
                                )
                            }
                            if (tool.hasStrokeWidth) {
                                RailStyleButton("Thickness", "tool_width", flyout == RailFlyout.WIDTH, { toggle(RailFlyout.WIDTH) }) {
                                    val dot = (state.drawingStrokeWidth * 0.9f).coerceIn(4f, 22f).dp
                                    Box(Modifier.size(dot).clip(CircleShape).background(RailInk))
                                }
                            }
                        }

                        RailDivider()
                        RailButton(Icons.AutoMirrored.Filled.Undo, "Undo", false, "drawing_undo_button", enabled = state.canUndoDrawing) {
                            viewModel.undoDrawing()
                        }
                        RailButton(Icons.AutoMirrored.Filled.Redo, "Redo", false, "drawing_redo_button", enabled = state.canRedoDrawing) {
                            viewModel.redoDrawing()
                        }
                        RailButton(Icons.Default.DeleteSweep, "Clear page drawings", false, "drawing_clear_page_button") {
                            viewModel.clearCurrentPageDrawings()
                        }
                        RailButton(Icons.Default.Gesture, if (state.isStylusOnlyDrawing) "Stylus only" else "Touch and stylus", state.isStylusOnlyDrawing, "stylus_toggle_button") {
                            viewModel.toggleStylusOnlyDrawing()
                        }

                        RailDivider()
                        Surface(
                            onClick = { viewModel.setDrawingMode(false) },
                            shape = CircleShape,
                            color = BrandPurple,
                            modifier = Modifier.size(40.dp).testTag("drawing_done_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    enabled: Boolean = true,
    hasFlyout: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) RailSelectedBg else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !enabled -> Color(0xFFCBD5E1)
                isSelected -> BrandPurple
                else -> RailInk
            },
            modifier = Modifier.size(20.dp)
        )
        if (hasFlyout) {
            // Small corner notch hints that this button opens more options
            Canvas(Modifier.align(Alignment.BottomStart).padding(5.dp).size(5.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f); lineTo(0f, size.height); lineTo(size.width, size.height); close()
                }
                drawPath(path, if (isSelected) BrandPurple else Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun RailStyleButton(
    label: String,
    testTag: String,
    isOpen: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isOpen) RailSelectedBg else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RailDivider() {
    HorizontalDivider(
        color = RailDividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RailFlyoutCard(
    flyout: RailFlyout,
    state: ReaderUiState,
    onTool: (DrawingTool) -> Unit,
    onColor: (String) -> Unit,
    onWidth: (Float) -> Unit
) {
    val isHighlighter = state.drawingTool.usesHighlighterPalette
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        modifier = Modifier.width(184.dp).testTag("drawing_flyout")
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = when (flyout) {
                    RailFlyout.TEXT -> "Mark text"
                    RailFlyout.SHAPES -> "Shapes"
                    RailFlyout.COLOR -> if (isHighlighter) "Marker color" else "Ink color"
                    RailFlyout.WIDTH -> "Thickness"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
            Spacer(Modifier.height(10.dp))
            when (flyout) {
                RailFlyout.TEXT, RailFlyout.SHAPES -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (if (flyout == RailFlyout.TEXT) TextMarkupTools else ShapeTools).forEach { shape ->
                        val selected = state.drawingTool == shape
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) RailSelectedBg else Color.Transparent)
                                .clickable { onTool(shape) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("tool_${shape.name.lowercase()}")
                        ) {
                            Icon(shape.icon(), contentDescription = null, tint = if (selected) BrandPurple else RailInk, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(shape.title, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Color(0xFF0F172A))
                        }
                    }
                }
                RailFlyout.COLOR -> FlowRow(
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    (if (isHighlighter) HighlighterColorPalette else PenColorPalette).forEach { hex ->
                        val selected = state.drawingColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(parseHex(hex))
                                .border(if (selected) 2.5.dp else 1.dp, if (selected) Color(0xFF0F172A) else Color(0x22000000), CircleShape)
                                .clickable { onColor(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected color",
                                    tint = if (isHighlighter) Color(0xFF0F172A) else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                RailFlyout.WIDTH -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val color = parseHex(state.drawingColorHex).let { if (isHighlighter) it.copy(alpha = .6f) else it }
                    (if (isHighlighter) HighlighterWidthPresets else InkWidthPresets).forEach { (width, label) ->
                        val selected = state.drawingStrokeWidth == width
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) RailSelectedBg else Color.Transparent)
                                .clickable { onWidth(width) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((width * if (isHighlighter) .5f else .8f).coerceIn(1.5f, 14f).dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(color)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Freehand Drawing Layer on top of each PDF Page.
 * Renders persisted strokes from Room and captures live pointer movements with sub-frame Bezier smoothing.
 * Fully supports Stylus differentiation (differentiates stylus from finger touch).
 */
@Composable
fun DrawingPageCanvas(
    pageIndex: Int,
    annotations: List<BookAnnotation>,
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var eraserPosition by remember { mutableStateOf<Offset?>(null) }
    // Live word-snapped preview for the text markup tools
    var markupPreview by remember { mutableStateOf<PdfTextSelection?>(null) }
    val scope = rememberCoroutineScope()

    // Collect persisted drawing strokes for this page
    val savedStrokes = remember(annotations) {
        annotations.filter { it.pageIndex == pageIndex && it.type == AnnotationType.PEN_DRAW }
            .mapNotNull { ann ->
                val data = ann.strokePathData ?: return@mapNotNull null
                StrokeSerializer.deserialize(ann.id, pageIndex, data, ann.colorHex)
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pageIndex, state.isDrawingModeActive, state.drawingTool, state.isStylusOnlyDrawing, state.drawingColorHex, state.drawingStrokeWidth) {
                // The scroll tool leaves every touch to the page's normal scroll, swipe and zoom
                if (!state.isDrawingModeActive || state.drawingTool == DrawingTool.HAND) return@pointerInput

                awaitPointerEventScope {
                    var shapeStart: Offset? = null
                    var markStart: Offset? = null
                    var previewJob: kotlinx.coroutines.Job? = null
                    var noteDown: Offset? = null
                    val arrowHead = 8.dp.toPx()..22.dp.toPx()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) {
                            currentPoints.clear()
                            shapeStart = null
                            continue
                        }
                        val change = event.changes.firstOrNull { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
                            ?: event.changes.firstOrNull() ?: continue

                        val isStylus = change.type == PointerType.Stylus || change.type == PointerType.Eraser
                        val shouldDraw = isStylus || !state.isStylusOnlyDrawing

                        if (!shouldDraw) {
                            // Touch event in Stylus-only mode: do not consume, allow user to pan/zoom/scroll!
                            continue
                        }

                        // We are capturing drawing or eraser input
                        change.consume()

                        val normX = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val normY = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)

                        when (state.drawingTool) {
                            DrawingTool.TEXT_HIGHLIGHT, DrawingTool.TEXT_UNDERLINE, DrawingTool.TEXT_STRIKETHROUGH -> {
                                val tool = state.drawingTool
                                if (change.pressed) {
                                    val start = markStart ?: Offset(normX, normY).also { markStart = it }
                                    previewJob?.cancel()
                                    previewJob = scope.launch {
                                        markupPreview = viewModel.selectTextRange(pageIndex, start.x, start.y, normX, normY)
                                    }
                                } else {
                                    val start = markStart
                                    previewJob?.cancel()
                                    markStart = null
                                    if (start != null) scope.launch {
                                        // A tap marks the word under the finger; a drag marks every word in between
                                        val selection = viewModel.selectTextRange(pageIndex, start.x, start.y, normX, normY)
                                        viewModel.commitTextMarkup(selection, tool)
                                        markupPreview = null
                                    }
                                }
                            }
                            DrawingTool.STICKY_NOTE -> {
                                if (change.pressed) {
                                    if (noteDown == null) noteDown = change.position
                                } else {
                                    val down = noteDown
                                    noteDown = null
                                    if (down != null && (change.position - down).getDistance() < viewConfiguration.touchSlop) {
                                        viewModel.placeStickyNote(pageIndex, normX, normY)
                                    }
                                }
                            }
                            DrawingTool.HAND -> Unit
                            DrawingTool.LINE, DrawingTool.ARROW, DrawingTool.RECTANGLE, DrawingTool.ELLIPSE -> {
                                if (change.pressed) {
                                    val start = shapeStart ?: change.position.also { shapeStart = it }
                                    val outline = ShapeGeometry.points(state.drawingTool, start, change.position, arrowHead)
                                    currentPoints.clear()
                                    outline.mapTo(currentPoints) {
                                        NormalizedPoint(
                                            x = (it.x / size.width.toFloat()).coerceIn(0f, 1f),
                                            y = (it.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        )
                                    }
                                } else {
                                    val start = shapeStart
                                    // Ignore taps: only persist shapes dragged out to a visible size
                                    if (start != null && (change.position - start).getDistance() > 6.dp.toPx() && currentPoints.size >= 2) {
                                        viewModel.onStrokeFinished(
                                            DrawingStroke(
                                                pageIndex = pageIndex,
                                                points = currentPoints.toList(),
                                                colorHex = state.drawingColorHex,
                                                strokeWidth = state.drawingStrokeWidth
                                            )
                                        )
                                    }
                                    currentPoints.clear()
                                    shapeStart = null
                                }
                            }
                            DrawingTool.ERASER -> {
                                if (change.pressed) {
                                    eraserPosition = change.position
                                    viewModel.eraseStrokesAt(pageIndex, normX, normY)
                                } else {
                                    eraserPosition = null
                                }
                            }
                            DrawingTool.PEN, DrawingTool.HIGHLIGHTER -> {
                                if (change.pressed) {
                                    currentPoints.add(
                                        NormalizedPoint(
                                            x = normX,
                                            y = normY,
                                            pressure = change.pressure.coerceIn(0.2f, 1.5f)
                                        )
                                    )
                                } else {
                                    // Pointer released: complete and persist stroke
                                    if (currentPoints.size >= 1) {
                                        val finishedStroke = DrawingStroke(
                                            pageIndex = pageIndex,
                                            points = currentPoints.toList(),
                                            colorHex = state.drawingColorHex,
                                            strokeWidth = state.drawingStrokeWidth,
                                            isHighlighter = (state.drawingTool == DrawingTool.HIGHLIGHTER)
                                        )
                                        viewModel.onStrokeFinished(finishedStroke)
                                    }
                                    currentPoints.clear()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pageW = size.width
            val pageH = size.height

            markupPreview?.let { preview ->
                val type = when (state.drawingTool) {
                    DrawingTool.TEXT_UNDERLINE -> AnnotationType.UNDERLINE
                    DrawingTool.TEXT_STRIKETHROUGH -> AnnotationType.STRIKETHROUGH
                    else -> AnnotationType.HIGHLIGHT
                }
                drawTextMarkup(type, preview.highlightRects, parseHex(state.drawingColorHex))
            }

            // 1. Draw all saved strokes for this page
            savedStrokes.forEach { stroke ->
                val path = stroke.toComposePath(pageW, pageH)
                val color = try {
                    Color(android.graphics.Color.parseColor(stroke.colorHex))
                } catch (_: Exception) {
                    Color(0xFF4F46E5)
                }

                val strokeWidthPx = (stroke.strokeWidth * (pageW / 400f)).coerceAtLeast(1f)

                if (stroke.isHighlighter) {
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.42f),
                        style = Stroke(
                            width = strokeWidthPx * 2.2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 2. Draw live in-progress stroke for ultra-low latency feedback
            if (currentPoints.isNotEmpty()) {
                val liveStroke = DrawingStroke(
                    pageIndex = pageIndex,
                    points = currentPoints.toList(),
                    colorHex = state.drawingColorHex,
                    strokeWidth = state.drawingStrokeWidth,
                    isHighlighter = (state.drawingTool == DrawingTool.HIGHLIGHTER)
                )

                val livePath = liveStroke.toComposePath(pageW, pageH)
                val color = try {
                    Color(android.graphics.Color.parseColor(state.drawingColorHex))
                } catch (_: Exception) {
                    Color(0xFF4F46E5)
                }

                val strokeWidthPx = (state.drawingStrokeWidth * (pageW / 400f)).coerceAtLeast(1f)

                if (state.drawingTool == DrawingTool.HIGHLIGHTER) {
                    drawPath(
                        path = livePath,
                        color = color.copy(alpha = 0.42f),
                        style = Stroke(
                            width = strokeWidthPx * 2.2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    drawPath(
                        path = livePath,
                        color = color,
                        style = Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 3. Draw live eraser circle under cursor
            eraserPosition?.let { pos ->
                drawCircle(
                    color = Color.Black.copy(alpha = 0.15f),
                    radius = 18.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = 18.dp.toPx(),
                    center = pos,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Phase 6: Export Annotated Copy Dialog
 */
@Composable
fun ExportAnnotatedPdfDialog(
    state: ReaderUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    com.bookflow.app.presentation.components.BookFlowBottomSheet(
        onDismissRequest = {
            if (!state.isExportingPdf) onDismiss()
        },
        title = "Export Annotated PDF",
        titleIcon = {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = BrandPurple,
                modifier = Modifier.size(24.dp)
            )
        },
        confirmButton = {
            if (state.exportedPdfFile != null) {
                Button(
                    onClick = {
                        val shareIntent = PdfAnnotatedExporter.createShareIntent(context, state.exportedPdfFile!!)
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Annotated PDF"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("dialog_share_annotated_pdf")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share PDF", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isExportingPdf,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (state.exportedPdfFile != null) "Done" else "Cancel", color = Color(0xFF64748B))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (state.isExportingPdf) {
                Text(
                    text = "Rendering annotations, highlights & drawings into a standalone PDF document...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(14.dp))

                val (current, total) = state.exportProgress ?: Pair(0, 1)
                val progressFraction = if (total > 0) current.toFloat() / total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = BrandPurple,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Page $current of $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            } else if (state.exportedPdfFile != null) {
                val file = state.exportedPdfFile!!
                Text(
                    text = "Your annotated copy is ready with all highlights, underlines, notes, and freehand pen drawings baked in!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = file.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FileUtils.formatFileSize(file.length()),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else if (state.errorMessage != null) {
                Text(
                    text = "Failed to export: ${state.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
