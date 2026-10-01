package com.bookflow.app.presentation.screens.reader

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
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
import com.bookflow.app.pdf.drawing.StrokeSerializer
import com.bookflow.app.pdf.exporter.PdfAnnotatedExporter

private val PenColorPalette = listOf(
    "#0F172A", // Dark Slate / Black
    "#4F46E5", // Brand Indigo/Purple
    "#EF4444", // Crimson Red
    "#10B981", // Emerald Green
    "#2563EB", // Royal Blue
    "#F97316"  // Amber / Orange
)

private val HighlighterColorPalette = listOf(
    "#FFE066", // Yellow
    "#86EFAC", // Mint Green
    "#7DD3FC", // Sky Blue
    "#F472B6", // Pink
    "#C084FC"  // Lilac
)

private val StrokeWidthPresets = listOf(
    2.0f to "Fine",
    4.0f to "Med",
    8.0f to "Bold",
    16.0f to "Max"
)

/**
 * Modern floating Drawing Toolbar (Apple Books / Samsung Notes style)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingToolbar(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val currentColors = if (state.drawingTool == DrawingTool.HIGHLIGHTER) {
        HighlighterColorPalette
    } else {
        PenColorPalette
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 10.dp,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .testTag("drawing_toolbar")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ROW 1: Tool Selection, Undo/Redo, Stylus Toggle, Done
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Drawing Tools Group (Pen, Highlighter, Eraser)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DrawingToolButton(
                        icon = Icons.Default.Create,
                        label = "Pen",
                        isSelected = state.drawingTool == DrawingTool.PEN,
                        onClick = { viewModel.setDrawingTool(DrawingTool.PEN) },
                        testTag = "tool_pen"
                    )

                    DrawingToolButton(
                        icon = Icons.Default.Highlight,
                        label = "Highlighter",
                        isSelected = state.drawingTool == DrawingTool.HIGHLIGHTER,
                        onClick = { viewModel.setDrawingTool(DrawingTool.HIGHLIGHTER) },
                        testTag = "tool_highlighter"
                    )

                    DrawingToolButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Eraser",
                        isSelected = state.drawingTool == DrawingTool.ERASER,
                        onClick = { viewModel.setDrawingTool(DrawingTool.ERASER) },
                        testTag = "tool_eraser"
                    )
                }

                // Middle: Undo & Redo & Clear Page
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = viewModel::undoDrawing,
                        enabled = state.canUndoDrawing,
                        modifier = Modifier.size(34.dp).testTag("drawing_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndoDrawing) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = viewModel::redoDrawing,
                        enabled = state.canRedoDrawing,
                        modifier = Modifier.size(34.dp).testTag("drawing_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedoDrawing) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = viewModel::clearCurrentPageDrawings,
                        modifier = Modifier.size(34.dp).testTag("drawing_clear_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Page Drawings",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Right: Stylus Mode & Done
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Stylus Toggle (Fingers scroll/pan while stylus draws!)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.isStylusOnlyDrawing) Color(0xFFE0E7FF) else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .clickable(onClick = viewModel::toggleStylusOnlyDrawing)
                            .testTag("stylus_toggle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = null,
                                tint = if (state.isStylusOnlyDrawing) BrandPurple else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.isStylusOnlyDrawing) "Stylus Only" else "Touch+Pen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.isStylusOnlyDrawing) BrandPurple else Color(0xFF475569)
                            )
                        }
                    }

                    // Done Button
                    Button(
                        onClick = { viewModel.setDrawingMode(false) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("drawing_done_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ROW 2 (Only if not Eraser): Color Swatches & Stroke Width Selectors
            if (state.drawingTool != DrawingTool.ERASER) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color Palette Swatches
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        currentColors.forEach { colorHex ->
                            val color = Color(android.graphics.Color.parseColor(colorHex))
                            val isSelected = state.drawingColorHex.equals(colorHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0x33000000),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setDrawingColor(colorHex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (colorHex == "#FFE066" || colorHex == "#86EFAC") Color.Black else Color.White)
                                    )
                                }
                            }
                        }
                    }

                    // Stroke Width Presets
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Width",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        StrokeWidthPresets.forEach { (width, label) ->
                            val isSelected = (state.drawingStrokeWidth == width)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                modifier = Modifier
                                    .clickable { viewModel.setStrokeWidth(width) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawingToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) BrandPurple else Color(0xFFF8FAFC),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF475569),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF475569)
            )
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
                if (!state.isDrawingModeActive) return@pointerInput

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) {
                            currentPoints.clear()
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

    AlertDialog(
        onDismissRequest = {
            if (!state.isExportingPdf) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = BrandPurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Annotated PDF",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
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
        },
        confirmButton = {
            if (state.exportedPdfFile != null) {
                Button(
                    onClick = {
                        val shareIntent = PdfAnnotatedExporter.createShareIntent(context, state.exportedPdfFile!!)
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Annotated PDF"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    modifier = Modifier.testTag("dialog_share_annotated_pdf")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share PDF")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !state.isExportingPdf
            ) {
                Text(if (state.exportedPdfFile != null) "Done" else "Cancel")
            }
        }
    )
}
