package com.bookflow.app.presentation.screens.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.PenPreset
import com.bookflow.app.notes.ink.PenType
import com.bookflow.app.notes.ink.ShapeKind
import com.mohamedrejeb.richeditor.model.RichTextState

data class ToolbarActions(
    val onTool: (EditorTool) -> Unit,
    val onSticky: () -> Unit,
    val onPhoto: () -> Unit,
    val onCamera: () -> Unit,
    val onAddPage: () -> Unit,
    val onPaste: () -> Unit
)

/** Every tool visible with a label, sized to fit a phone; inserts live in one menu. */
@Composable
fun EditorToolBar(state: NoteEditorUiState, actions: ToolbarActions) {
    var insertMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // Tools scroll on narrow phones; Insert stays pinned at the end
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                EditorToolEntries.forEach { (tool, entry) -> ToolButton(entry.first, entry.second, state.tool == tool) { actions.onTool(tool) } }
            }
            Box {
                ToolButton(Icons.Outlined.AddCircleOutline, "Insert", false) { insertMenu = true }
                DropdownMenu(expanded = insertMenu, onDismissRequest = { insertMenu = false }) {
                    DropdownMenuItem(text = { Text("Sticky note") }, leadingIcon = { Icon(Icons.Outlined.StickyNote2, null) }, onClick = { insertMenu = false; actions.onSticky() })
                    DropdownMenuItem(text = { Text("Photo") }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, onClick = { insertMenu = false; actions.onPhoto() })
                    DropdownMenuItem(text = { Text("Camera") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) }, onClick = { insertMenu = false; actions.onCamera() })
                    DropdownMenuItem(text = { Text("Page…") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { insertMenu = false; actions.onAddPage() })
                    DropdownMenuItem(text = { Text("Paste") }, leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) }, enabled = state.clipboardAvailable,
                        onClick = { insertMenu = false; actions.onPaste() })
                }
            }
        }
    }
}

/** Tool icons and labels, shared by the bottom bar and the floating toolbar. */
val EditorToolEntries: List<Pair<EditorTool, Pair<ImageVector, String>>> = listOf(
    EditorTool.PEN to (Icons.Outlined.Create to "Pen"),
    EditorTool.SHAPE to (Icons.Outlined.Category to "Shapes"),
    EditorTool.ERASER to (Icons.Outlined.CleaningServices to "Eraser"),
    EditorTool.LASSO to (Icons.Outlined.HighlightAlt to "Lasso"),
    EditorTool.TEXT to (Icons.Outlined.TextFields to "Text"),
    EditorTool.TAPE to (Icons.Outlined.VisibilityOff to "Tape"),
    EditorTool.LASER to (Icons.Outlined.Flare to "Laser"),
    EditorTool.HAND to (Icons.Outlined.PanTool to "Select")
)

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 30.dp).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(label, fontSize = 11.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ShapeBar(state: NoteEditorUiState, onShape: (ShapeKind) -> Unit, onAuto: () -> Unit, onEditPen: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Auto: draw freely; circles, boxes, triangles, and lines snap to clean shapes
        FilterChip(selected = state.autoShape, onClick = onAuto, label = { Text("Auto") }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null, Modifier.size(18.dp)) })
        ShapeKind.entries.forEach { kind -> FilterChip(selected = !state.autoShape && state.shapeKind == kind, onClick = { onShape(kind) }, label = { Text(kind.title) }) }
        state.activePen?.let { pen ->
            Box(Modifier.size(32.dp).background(Color(pen.colorArgb), CircleShape).border(1.dp, Color(0x33000000), CircleShape)
                .clickable(onClickLabel = "Change shape color and width", onClick = onEditPen))
        }
    }
}

val StickyColors = listOf(0xFFFEF08A, 0xFFBBF7D0, 0xFFBFDBFE, 0xFFFBCFE8, 0xFFFED7AA, 0xFFE9D5FF).map { it.toInt() }

/** Saved pens as one-tap chips; tapping the active pen again opens its editor. */
@Composable
fun PenPresetBar(state: NoteEditorUiState, onSelect: (Int) -> Unit, onEditActive: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        state.presets.forEachIndexed { index, preset ->
            val active = index == state.activePreset
            Box(
                Modifier.size(38.dp).border(if (active) 2.5.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClickLabel = if (active) "Edit pen" else "Use pen") { if (active) onEditActive() else onSelect(index) }
                    .semantics { contentDescription = "${preset.type.title} ${index + 1}" },
                contentAlignment = Alignment.Center
            ) {
                val dot = (8 + preset.size.coerceAtMost(30f) * 0.7f).dp
                Box(Modifier.size(dot.coerceAtMost(26.dp)).background(Color(preset.colorArgb).copy(alpha = if (preset.type.translucent) 0.55f else 1f),
                    if (preset.type == PenType.HIGHLIGHTER) RoundedCornerShape(3.dp) else CircleShape))
            }
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(38.dp)) { Icon(Icons.Outlined.Add, "Add pen") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PenEditor(
    preset: PenPreset,
    canRemove: Boolean,
    recentColors: List<Int>,
    onChange: ((PenPreset) -> PenPreset) -> Unit,
    onCustomColor: (Int) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pen", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PenType.entries.forEach { type ->
                FilterChip(selected = preset.type == type, onClick = {
                    onChange { it.copy(type = type, size = if (type == PenType.HIGHLIGHTER) maxOf(it.size, 14f) else minOf(it.size, 24f)) }
                }, label = { Text(type.title) })
            }
        }
        Text("Color", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (InkBrushes.palette + recentColors.filterNot { c -> InkBrushes.palette.any { (it and 0xFFFFFF) == (c and 0xFFFFFF) } }).forEach { color ->
                val selected = (color and 0xFFFFFF) == (preset.colorArgb and 0xFFFFFF)
                Box(Modifier.size(32.dp).background(Color(color), CircleShape)
                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                    .clickable { onChange { it.copy(colorArgb = color) } })
            }
            // Custom color: a rainbow swatch that opens the picker
            Box(Modifier.size(32.dp).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                .clickable(onClickLabel = "Custom color") { picking = !picking },
                contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        if (picking) NoteColorPicker(preset.colorArgb) { color -> onCustomColor(color); picking = false }
        val sizes = if (preset.type == PenType.HIGHLIGHTER) InkBrushes.highlighterSizes else InkBrushes.sizes
        Text("Thickness", style = MaterialTheme.typography.labelLarge)
        Slider(value = preset.size, onValueChange = { v -> onChange { it.copy(size = v) } }, valueRange = sizes.first()..sizes.last())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (canRemove) TextButton(onClick = onRemove) { Text("Remove pen") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
fun EraserBar(state: NoteEditorUiState, onMode: (EraserMode) -> Unit, onSize: (Float) -> Unit, onClearPage: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                EraserMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(selected = state.eraserMode == mode, onClick = { onMode(mode) }, shape = SegmentedButtonDefaults.itemShape(i, EraserMode.entries.size)) {
                        Text(mode.title, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            TextButton(onClick = onClearPage) { Text("Clear page") }
        }
        Slider(value = state.eraserSize, onValueChange = onSize, valueRange = 8f..80f, modifier = Modifier.fillMaxWidth().height(32.dp))
    }
}

/** Tape colors, plus showing or covering every tape at once for self-testing. */
@Composable
fun TapeBar(state: NoteEditorUiState, onColor: (Int) -> Unit, onRevealAll: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TapeColors.forEach { color ->
                val selected = state.tapeColor == color
                Box(Modifier.size(width = 34.dp, height = 22.dp).background(Color(color), RoundedCornerShape(4.dp))
                    .border(if (selected) 2.5.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .clickable(onClickLabel = "Tape color") { onColor(color) })
            }
        }
        TextButton(onClick = { onRevealAll(true) }) { Text("Show all") }
        TextButton(onClick = { onRevealAll(false) }) { Text("Hide all") }
    }
}

@Composable
fun HintBar(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
fun SelectionBar(
    canRecolor: Boolean,
    sourceAvailable: Boolean,
    isSticky: Boolean,
    onStickyColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onFront: () -> Unit,
    onRecolor: (Int) -> Unit,
    onOpenSource: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete selection") }
            IconButton(onClick = onDuplicate) { Icon(Icons.Outlined.LibraryAdd, "Duplicate selection") }
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "Copy selection") }
            IconButton(onClick = onCut) { Icon(Icons.Outlined.ContentCut, "Cut selection") }
            IconButton(onClick = onFront) { Icon(Icons.Outlined.FlipToFront, "Bring to front") }
            if (sourceAvailable) TextButton(onClick = onOpenSource) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open source page") }
        }
        if (isSticky) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StickyColors.forEach { color ->
                    Box(Modifier.size(28.dp).background(Color(color), RoundedCornerShape(6.dp)).border(1.dp, Color(0x33000000), RoundedCornerShape(6.dp)).clickable { onStickyColor(color) })
                }
            }
        }
        if (canRecolor) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkBrushes.palette.forEach { color ->
                    Box(Modifier.size(28.dp).background(Color(color), CircleShape).border(1.dp, Color(0x33000000), CircleShape).clickable { onRecolor(color) })
                }
            }
        }
    }
}

/** Formatting for the text box being edited. Checklist items use ☐/☑ glyphs so they survive HTML export. */
@Composable
fun TextFormatBar(rich: RichTextState, onColor: (Color) -> Unit) {
    val style = rich.currentSpanStyle
    Column {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FormatToggle(Icons.Outlined.FormatBold, "Bold", style.fontWeight == FontWeight.Bold) { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
            FormatToggle(Icons.Outlined.FormatItalic, "Italic", style.fontStyle == FontStyle.Italic) { rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
            FormatToggle(Icons.Outlined.FormatUnderlined, "Underline", style.textDecoration?.contains(TextDecoration.Underline) == true) { rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
            FormatToggle(Icons.Outlined.FormatStrikethrough, "Strikethrough", style.textDecoration?.contains(TextDecoration.LineThrough) == true) { rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
            FormatToggle(Icons.Outlined.Title, "Heading", style.fontSize == HEADING_SIZE) { rich.toggleSpanStyle(SpanStyle(fontSize = HEADING_SIZE, fontWeight = FontWeight.Bold)) }
            FormatToggle(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bulleted list", rich.isUnorderedList) { rich.toggleUnorderedList() }
            FormatToggle(Icons.Outlined.FormatListNumbered, "Numbered list", rich.isOrderedList) { rich.toggleOrderedList() }
            FormatToggle(Icons.Outlined.CheckBox, "Checklist item", false) { toggleChecklist(rich) }
            listOf(0xFF111827, 0xFFDC2626, 0xFF2563EB, 0xFF16A34A, 0xFF7C3AED, 0xFFCA8A04).forEach { argb ->
                val color = Color(argb)
                Box(Modifier.padding(4.dp).size(24.dp).background(color, CircleShape)
                    .border(if (style.color == color) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onColor(color) })
            }
        }
    }
}

private val HEADING_SIZE = 30.sp

@Composable
private fun FormatToggle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(Modifier.size(34.dp).background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, label)
        }
    }
}

/** Adds a checkbox to the current line, or flips an existing one between ☐ and ☑. */
private fun toggleChecklist(rich: RichTextState) {
    val text = rich.annotatedString.text
    val cursor = rich.selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0 || cursor == 0) 0 else it + 1 }
    val current = text.getOrNull(lineStart)
    val replacement = when (current) { '☐' -> "☑"; '☑' -> "☐"; else -> null }
    val saved = rich.selection
    if (replacement != null) {
        rich.selection = androidx.compose.ui.text.TextRange(lineStart, lineStart + 1)
        rich.removeSelectedText()
        rich.addTextAfterSelection(replacement)
        rich.selection = saved
    } else {
        rich.selection = androidx.compose.ui.text.TextRange(lineStart)
        rich.addTextAfterSelection("☐ ")
        rich.selection = androidx.compose.ui.text.TextRange(saved.start + 2, saved.end + 2)
    }
}
