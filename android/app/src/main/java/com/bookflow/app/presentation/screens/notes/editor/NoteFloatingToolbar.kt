package com.bookflow.app.presentation.screens.notes.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.notes.ink.InkGeometry
import com.bookflow.app.notes.ink.NoteShapes
import com.bookflow.app.notes.ink.ShapeKind
import com.bookflow.app.presentation.components.DialEntry
import com.bookflow.app.presentation.components.ToolDial
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingToolbarActions(
    val onTool: (EditorTool) -> Unit,
    val onPreset: (Int) -> Unit,
    val onEditPen: () -> Unit,
    val onAddPen: () -> Unit,
    val onShape: (ShapeKind) -> Unit,
    val onAutoShape: () -> Unit,
    val onPaste: () -> Unit,
    /** Position as fractions of the free space, persisted between sessions. */
    val initialPosition: Pair<Float, Float>,
    val onMoved: (Float, Float) -> Unit
)

/** What a stop on the Notes tool dial does. */
private sealed interface NoteDialItem {
    data class Pen(val index: Int) : NoteDialItem
    data class Shape(val kind: ShapeKind) : NoteDialItem
    data object AutoShape : NoteDialItem
    data class Tool(val tool: EditorTool, val icon: ImageVector, val label: String) : NoteDialItem
    data object AddPen : NoteDialItem
    data object Paste : NoteDialItem
}

/**
 * A pencil button that floats over the page (bottom right at first). Tapping it opens the tool dial on the right
 * edge: every saved pen, every shape, and the other tools. Spin it and lift, or tap a tool, and it closes again.
 * Drag the button anywhere; it settles against the nearer side so it never covers the middle of the page.
 */
@Composable
fun NoteFloatingToolbar(state: NoteEditorUiState, actions: FloatingToolbarActions, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val margin = with(LocalDensity.current) { 8.dp.toPx() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        var barW by remember { mutableIntStateOf(0) }
        var barH by remember { mutableIntStateOf(0) }
        val x = remember { Animatable(actions.initialPosition.first) }
        val y = remember { Animatable(actions.initialPosition.second) }
        val freeW = (maxW - barW - 2 * margin).coerceAtLeast(0f)
        val freeH = (maxH - barH - 2 * margin).coerceAtLeast(0f)

        val drag = Modifier.pointerInput(freeW, freeH) {
            detectDragGestures(
                onDragEnd = {
                    // Settle against the nearer edge, like a chat bubble
                    scope.launch {
                        val target = if (x.value < 0.5f) 0f else 1f
                        x.animateTo(target)
                        actions.onMoved(target, y.value)
                    }
                }
            ) { change, amount ->
                change.consume()
                scope.launch {
                    if (freeW > 0) x.snapTo((x.value + amount.x / freeW).coerceIn(0f, 1f))
                    if (freeH > 0) y.snapTo((y.value + amount.y / freeH).coerceIn(0f, 1f))
                }
            }
        }

        if (expanded) {
            BackHandler { expanded = false }
            // Tapping the page outside the dial closes it
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { expanded = false } })
            val items = noteDialItems(state)
            val selected = items.indexOfFirst { item ->
                when (item) {
                    is NoteDialItem.Pen -> state.tool == EditorTool.PEN && item.index == state.activePreset
                    is NoteDialItem.Shape -> state.tool == EditorTool.SHAPE && !state.autoShape && item.kind == state.shapeKind
                    NoteDialItem.AutoShape -> state.tool == EditorTool.SHAPE && state.autoShape
                    is NoteDialItem.Tool -> state.tool == item.tool
                    else -> false
                }
            }.coerceAtLeast(0)
            ToolDial(
                entries = items.map { item -> DialEntry(item.label(state), item.tag()) { sel -> NoteDialIcon(item, state, sel) } },
                selected = selected,
                onPick = { index, tapped ->
                    expanded = false
                    when (val item = items[index]) {
                        is NoteDialItem.Pen -> if (tapped && index == selected) actions.onEditPen() else actions.onPreset(item.index)
                        is NoteDialItem.Shape -> actions.onShape(item.kind)
                        NoteDialItem.AutoShape -> actions.onAutoShape()
                        is NoteDialItem.Tool -> actions.onTool(item.tool)
                        NoteDialItem.AddPen -> { actions.onAddPen(); actions.onEditPen() }
                        NoteDialItem.Paste -> actions.onPaste()
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        } else {
            val active = EditorToolEntries.firstOrNull { it.first == state.tool }?.second?.second.orEmpty()
            // The bar under the pencil shows the current pen color while a pen is in use
            val penColor = if (state.tool == EditorTool.PEN) state.activePen?.let { Color(it.colorArgb) } else null
            Surface(
                shape = CircleShape,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .offset { IntOffset((margin + x.value * freeW).roundToInt(), (margin + y.value * freeH).roundToInt()) }
                    .onSizeChanged { barW = it.width; barH = it.height }
            ) {
                Box(
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer).then(drag)
                        .clickable(onClickLabel = "Open tools") { expanded = true }
                        .semantics { contentDescription = "Tools, current: $active" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (penColor != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp).size(width = 18.dp, height = 3.dp).background(penColor, CircleShape))
                }
            }
        }
    }
}

private fun noteDialItems(state: NoteEditorUiState): List<NoteDialItem> =
    state.presets.indices.map { NoteDialItem.Pen(it) } +
        ShapeKind.entries.map { NoteDialItem.Shape(it) } + NoteDialItem.AutoShape +
        EditorToolEntries.filter { it.first != EditorTool.PEN && it.first != EditorTool.SHAPE }
            .map { (tool, entry) -> NoteDialItem.Tool(tool, entry.first, entry.second) } +
        NoteDialItem.AddPen +
        listOfNotNull(NoteDialItem.Paste.takeIf { state.clipboardAvailable })

private fun NoteDialItem.label(state: NoteEditorUiState): String = when (this) {
    is NoteDialItem.Pen -> "${state.presets[index].type.title} ${index + 1}"
    is NoteDialItem.Shape -> kind.title
    NoteDialItem.AutoShape -> "Auto shape"
    is NoteDialItem.Tool -> label
    NoteDialItem.AddPen -> "Add pen"
    NoteDialItem.Paste -> "Paste"
}

private fun NoteDialItem.tag(): String = when (this) {
    is NoteDialItem.Pen -> "note_pen_$index"
    is NoteDialItem.Shape -> "note_shape_${kind.name.lowercase()}"
    NoteDialItem.AutoShape -> "note_auto_shape"
    is NoteDialItem.Tool -> "note_tool_${tool.name.lowercase()}"
    NoteDialItem.AddPen -> "note_add_pen"
    NoteDialItem.Paste -> "note_paste"
}

@Composable
private fun NoteDialIcon(item: NoteDialItem, state: NoteEditorUiState, selected: Boolean) {
    val tint = if (selected) BrandPurple else MaterialTheme.colorScheme.onSurface
    when (item) {
        is NoteDialItem.Pen -> PenGlyph(state.presets[item.index], Modifier.size(width = 24.dp, height = 40.dp).clipToBounds())
        is NoteDialItem.Shape -> ShapeGlyph(item.kind, tint)
        NoteDialItem.AutoShape -> Icon(Icons.Outlined.AutoFixHigh, null, tint = tint, modifier = Modifier.size(22.dp))
        is NoteDialItem.Tool -> Icon(item.icon, null, tint = tint, modifier = Modifier.size(22.dp))
        NoteDialItem.AddPen -> Icon(Icons.Outlined.Add, null, tint = tint, modifier = Modifier.size(22.dp))
        NoteDialItem.Paste -> Icon(Icons.Outlined.ContentPaste, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** The shape itself, drawn with the same outline the editor inserts. */
@Composable
private fun ShapeGlyph(kind: ShapeKind, tint: Color) {
    val points = remember(kind) { NoteShapes.points(kind, InkGeometry.Point(0f, 0f), InkGeometry.Point(100f, 100f)) }
    Canvas(Modifier.size(22.dp)) {
        if (points.size < 2) return@Canvas
        val s = size.minDimension / 100f
        val path = Path().apply {
            moveTo(points[0].x * s, points[0].y * s)
            points.drop(1).forEach { lineTo(it.x * s, it.y * s) }
        }
        drawPath(path, tint, style = Stroke(1.8.dp.toPx()))
    }
}
