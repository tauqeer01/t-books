package com.bookflow.app.presentation.screens.notes.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bookflow.app.notes.ink.PenType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingToolbarActions(
    val onTool: (EditorTool) -> Unit,
    val onPreset: (Int) -> Unit,
    val onEditPen: () -> Unit,
    val onHighlighter: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onPaste: () -> Unit,
    /** Position as fractions of the free space, persisted between sessions. */
    val initialPosition: Pair<Float, Float>,
    val onMoved: (Float, Float) -> Unit
)

/**
 * A palette that floats over the page: collapsed it is one round button showing the current tool; expanded it
 * lists pens and tools. Drag it anywhere; it settles against the nearer side so it never covers the middle.
 */
@Composable
fun NoteFloatingToolbar(state: NoteEditorUiState, actions: FloatingToolbarActions, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(true) }
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

        Surface(
            shape = if (expanded) RoundedCornerShape(26.dp) else CircleShape,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .offset { IntOffset((margin + x.value * freeW).roundToInt(), (margin + y.value * freeH).roundToInt()) }
                .onSizeChanged { barW = it.width; barH = it.height }
        ) {
            if (!expanded) {
                val active = EditorToolEntries.firstOrNull { it.first == state.tool }?.second
                val tint = if (state.tool == EditorTool.PEN) state.activePen?.let { Color(it.colorArgb) } else null
                Box(
                    Modifier.size(52.dp).then(drag).clickable(onClickLabel = "Show tools") { expanded = true }
                        .semantics { contentDescription = "Tools: ${active?.second ?: ""}" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(active?.first ?: Icons.Outlined.DragIndicator, null, tint = tint ?: MaterialTheme.colorScheme.primary)
                    if (tint != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(width = 18.dp, height = 3.dp).background(tint, CircleShape))
                }
            } else {
                Column(
                    Modifier.width(52.dp).heightIn(max = with(LocalDensity.current) { (maxH - 2 * margin).toDp() })
                        .verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(Modifier.size(44.dp, 28.dp).then(drag).semantics { contentDescription = "Move toolbar" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.DragIndicator, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    }
                    // Saved pens: tap to use, tap the active one again to edit it
                    state.presets.take(MAX_PEN_DOTS).forEachIndexed { index, pen ->
                        val active = state.tool == EditorTool.PEN && index == state.activePreset
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .border(if (active) 2.5.dp else 0.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable(onClickLabel = if (active) "Edit pen" else "Use pen") { if (active) actions.onEditPen() else actions.onPreset(index) }
                                .semantics { contentDescription = "${pen.type.title} ${index + 1}"; selected = active },
                            contentAlignment = Alignment.Center
                        ) {
                            val dot = (10 + pen.size.coerceAtMost(28f) * 0.5f).dp.coerceAtMost(22.dp)
                            Box(Modifier.size(dot).background(Color(pen.colorArgb).copy(alpha = if (pen.type.translucent) 0.6f else 1f),
                                if (pen.type == PenType.HIGHLIGHTER) RoundedCornerShape(3.dp) else CircleShape))
                        }
                    }
                    HorizontalDivider(Modifier.width(28.dp).padding(vertical = 2.dp))
                    val highlighting = state.tool == EditorTool.PEN && state.activePen?.type == PenType.HIGHLIGHTER
                    FloatingTool(Icons.Outlined.BorderColor, "Highlighter", highlighting, onClick = actions.onHighlighter)
                    EditorToolEntries.filter { it.first != EditorTool.PEN }.forEach { (tool, entry) ->
                        FloatingTool(entry.first, entry.second, state.tool == tool) { actions.onTool(tool) }
                    }
                    HorizontalDivider(Modifier.width(28.dp).padding(vertical = 2.dp))
                    FloatingTool(Icons.AutoMirrored.Outlined.Undo, "Undo", false, enabled = state.canUndo, onClick = actions.onUndo)
                    FloatingTool(Icons.AutoMirrored.Outlined.Redo, "Redo", false, enabled = state.canRedo, onClick = actions.onRedo)
                    if (state.clipboardAvailable) FloatingTool(Icons.Outlined.ContentPaste, "Paste", false, onClick = actions.onPaste)
                    FloatingTool(Icons.Outlined.UnfoldLess, "Collapse toolbar", false) { expanded = false }
                }
            }
        }
    }
}

private const val MAX_PEN_DOTS = 5

@Composable
private fun FloatingTool(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        })
    }
}
