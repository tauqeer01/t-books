package com.bookflow.app.presentation.screens.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.notes.ink.PenPreset
import com.bookflow.app.pdf.drawing.DrawingTool
import com.bookflow.app.presentation.screens.notes.editor.PenEditor
import com.bookflow.app.presentation.screens.notes.editor.PenGlyph
import com.bookflow.app.presentation.components.DialEntry
import com.bookflow.app.presentation.components.ToolDial
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Whether the tool dial is open, and where, so a touch on the page outside it can fold it away. */
class ToolDialState {
    var open by mutableStateOf(false)
    var bounds: Rect = Rect.Zero
    var originInWindow: Offset = Offset.Zero
}

/** One stop on the dial: a tool, one of the saved pens, or "Add pen". */
private sealed interface DialItem {
    val label: String
    val tag: String

    data class Tool(val tool: DrawingTool, override val label: String, override val tag: String) : DialItem
    data class Pen(val index: Int, val preset: PenPreset) : DialItem {
        override val label get() = "${preset.type.title} ${index + 1}"
        override val tag get() = "tool_pen_$index"
    }
    data object AddPen : DialItem { override val label = "Add pen"; override val tag = "tool_add_pen" }
}

private fun dialItems(pens: List<PenPreset>): List<DialItem> =
    listOf(
        DialItem.Tool(DrawingTool.HAND, "Scroll", "tool_hand"),
        DialItem.Tool(DrawingTool.SELECT_TEXT, "Select text", "tool_select_text"),
        DialItem.Tool(DrawingTool.CLIP, "Clip to note", "tool_clip"),
    ) + pens.mapIndexed { i, p -> DialItem.Pen(i, p) } + listOf(
        DialItem.Tool(DrawingTool.TEXT_HIGHLIGHT, "Highlight text", "tool_text"),
        DialItem.Tool(DrawingTool.TEXT_UNDERLINE, "Underline text", "tool_text_underline"),
        DialItem.Tool(DrawingTool.TEXT_STRIKETHROUGH, "Strike through text", "tool_text_strikethrough"),
        DialItem.Tool(DrawingTool.LINE, "Line", "tool_line"),
        DialItem.Tool(DrawingTool.ARROW, "Arrow", "tool_arrow"),
        DialItem.Tool(DrawingTool.RECTANGLE, "Rectangle", "tool_rectangle"),
        DialItem.Tool(DrawingTool.ELLIPSE, "Ellipse", "tool_ellipse"),
        DialItem.Tool(DrawingTool.STICKY_NOTE, "Sticky note", "tool_sticky_note"),
        DialItem.Tool(DrawingTool.ERASER, "Eraser", "tool_eraser"),
        DialItem.AddPen
    )

private fun List<DialItem>.selectedIndex(state: ReaderUiState): Int = indexOfFirst { item ->
    when (item) {
        is DialItem.Tool -> state.drawingTool == item.tool
        is DialItem.Pen -> (state.drawingTool == DrawingTool.PEN || state.drawingTool == DrawingTool.HIGHLIGHTER) && item.index == state.activePenIndex
        DialItem.AddPen -> false
    }
}.coerceAtLeast(0)

/**
 * Annotation tools. Outside annotation mode it is the round pen button (bottom right). In annotation mode a
 * half-circle dial on the right edge holds every tool; picking one folds it into a small bar with the current
 * tool, undo, redo, more, and done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationToolRail(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    dial: ToolDialState,
    modifier: Modifier = Modifier
) {
    var flyout by remember { mutableStateOf<RailFlyout?>(null) }
    var editingPen by remember { mutableStateOf(false) }
    LaunchedEffect(state.isDrawingModeActive) { dial.open = state.isDrawingModeActive; flyout = null }
    val items = dialItems(state.penPresets)
    val selected = items.selectedIndex(state)

    fun pick(index: Int, tapped: Boolean) {
        val item = items.getOrNull(index) ?: return
        val again = tapped && index == selected
        flyout = null
        when (item) {
            // Tapping the current text mark or sticky note again opens its colors
            is DialItem.Tool -> if (again && item.tool.hasColor) flyout = RailFlyout.COLOR else viewModel.setDrawingTool(item.tool)
            is DialItem.Pen -> if (again) editingPen = true else viewModel.selectPen(item.index)
            DialItem.AddPen -> { viewModel.addPen(); editingPen = true }
        }
        dial.open = false
    }

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = when { !state.isDrawingModeActive -> 0; dial.open -> 1; else -> 2 },
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = .85f, transformOrigin = TransformOrigin(1f, .5f))) togetherWith
                    (fadeOut() + scaleOut(targetScale = .85f, transformOrigin = TransformOrigin(1f, .5f)))
            },
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.align(if (state.isDrawingModeActive) Alignment.CenterEnd else Alignment.BottomEnd),
            label = "annotation_tools"
        ) { mode ->
            when (mode) {
                0 -> Surface(
                    onClick = { viewModel.setDrawingMode(true); viewModel.selectPen(state.activePenIndex) },
                    shape = CircleShape,
                    color = BrandPurple,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(end = 16.dp).size(48.dp).testTag("annotation_rail_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(DrawingTool.PEN.icon(), contentDescription = "Annotate", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                1 -> ToolDial(items.map { item -> DialEntry(item.label, item.tag) { sel -> DialItemIcon(item, sel) } }, selected, onPick = ::pick,
                    modifier = Modifier.testTag("drawing_toolbar").onGloballyPositioned { dial.bounds = it.boundsInWindow() })
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = flyout != null, enter = fadeIn(), exit = fadeOut()) {
                        flyout?.let { f ->
                            RailFlyoutCard(
                                flyout = f, state = state,
                                onTool = { viewModel.setDrawingTool(it); flyout = null },
                                onColor = { viewModel.setDrawingColor(it); flyout = null },
                                onWidth = { flyout = null }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    CollapsedToolBar(state, viewModel, items.getOrNull(selected),
                        onOpenDial = { flyout = null; dial.open = true },
                        onColor = { flyout = if (flyout == RailFlyout.COLOR) null else RailFlyout.COLOR })
                }
            }
        }
    }

    val pen = state.penPresets.getOrNull(state.activePenIndex)
    if (editingPen && pen != null) {
        ModalBottomSheet(onDismissRequest = { editingPen = false }) {
            PenEditor(
                preset = pen,
                canRemove = state.penPresets.size > 1,
                recentColors = emptyList(),
                onChange = viewModel::updateActivePen,
                onCustomColor = { color -> viewModel.updateActivePen { it.copy(colorArgb = color) } },
                onRemove = { viewModel.removeActivePen(); editingPen = false },
                onDone = { editingPen = false }
            )
        }
    }
}

@Composable
private fun DialItemIcon(item: DialItem, selected: Boolean) {
    val tint = if (selected) BrandPurple else MaterialTheme.colorScheme.onSurface
    when (item) {
        is DialItem.Pen -> PenGlyph(item.preset, Modifier.size(width = 24.dp, height = 40.dp).clipToBounds())
        is DialItem.Tool -> Icon(item.tool.icon(), null, tint = tint, modifier = Modifier.size(22.dp))
        DialItem.AddPen -> Icon(Icons.Default.Add, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** The dial folded away: current tool (tap to spin again), undo, redo, more, done. */
@Composable
private fun CollapsedToolBar(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    current: DialItem?,
    onOpenDial: () -> Unit,
    onColor: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.testTag("drawing_toolbar")
    ) {
        Column(Modifier.width(52.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                Modifier.size(44.dp).clipToBounds().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = "Choose tool", onClick = onOpenDial)
                    .semantics { contentDescription = "Tool: ${current?.label ?: "none"}" }
                    .testTag("current_tool_button"),
                contentAlignment = Alignment.Center
            ) { current?.let { DialItemIcon(it, selected = true) } }
            // Text marks and sticky notes keep their own color; pens carry theirs
            if (state.drawingTool.isTextMarkup || state.drawingTool == DrawingTool.STICKY_NOTE) {
                Box(Modifier.size(40.dp).clickable(onClickLabel = "Color", onClick = onColor).semantics { contentDescription = "Color" }.testTag("tool_color"),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(22.dp).background(parseHex(state.drawingColorHex), CircleShape))
                }
            }
            RailDivider()
            RailButton(Icons.AutoMirrored.Filled.Undo, "Undo", false, "drawing_undo_button", enabled = state.canUndoDrawing) { viewModel.undoDrawing() }
            RailButton(Icons.AutoMirrored.Filled.Redo, "Redo", false, "drawing_redo_button", enabled = state.canRedoDrawing) { viewModel.redoDrawing() }
            Box {
                RailButton(Icons.Default.MoreHoriz, "More", menu, "drawing_more_button") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Clear page drawings") }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                        onClick = { menu = false; viewModel.clearCurrentPageDrawings() }, modifier = Modifier.testTag("drawing_clear_page_button"))
                    DropdownMenuItem(text = { Text(if (state.isStylusOnlyDrawing) "Stylus only: on" else "Stylus only: off") },
                        leadingIcon = { Icon(Icons.Default.Gesture, null) },
                        onClick = { menu = false; viewModel.toggleStylusOnlyDrawing() }, modifier = Modifier.testTag("stylus_toggle_button"))
                }
            }
            RailDivider()
            Surface(onClick = { viewModel.setDrawingMode(false) }, shape = CircleShape, color = BrandPurple,
                modifier = Modifier.size(40.dp).testTag("drawing_done_button")) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
