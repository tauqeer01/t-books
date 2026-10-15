package com.bookflow.app.presentation.screens.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.presentation.screens.notes.export.NotePageRenderer
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.io.File

/** One [RichTextState] per text element, created from saved HTML the first time the element is shown. */
@Stable
class RichTextStates {
    private val states = mutableMapOf<String, RichTextState>()
    fun get(element: NoteElement): RichTextState = states.getOrPut(element.id) { RichTextState().apply { setHtml(element.html.orEmpty()) } }
    /** Undo/redo can change HTML underneath an element; re-seed the editor when it is not being typed into. */
    fun sync(element: NoteElement, editing: Boolean) {
        val state = states[element.id] ?: return
        if (!editing && state.toHtml() != element.html.orEmpty()) state.setHtml(element.html.orEmpty())
    }
}

/** Line height matches the ruled-page spacing so typed lines sit on the rules. */
val noteTextStyle = TextStyle(fontSize = NotePageRenderer.DEFAULT_TEXT_SIZE.sp, color = Color(0xFF111827), lineHeight = NotePageRenderer.LINE_SPACING.sp)

/** Rendered inside the page's local density, where 1.dp equals one page unit. */
@OptIn(FlowPreview::class)
@Composable
fun NoteElementView(element: NoteElement, pageId: String, editing: Boolean, textStates: RichTextStates, vm: NoteEditorViewModel) {
    val position = Modifier.offset((element.x * PAGE_WIDTH).dp, (element.y * PAGE_HEIGHT).dp).width((element.width * PAGE_WIDTH).dp)
    when {
        element.kind == ElementKind.TEXT || (element.kind == ElementKind.PDF_CLIP && element.imagePath == null) -> {
            val rich = textStates.get(element)
            LaunchedEffect(element.html, editing) { textStates.sync(element, editing) }
            val focus = remember { FocusRequester() }
            val density = LocalDensity.current
            var heightPx = 0
            LaunchedEffect(editing) { if (editing) runCatching { focus.requestFocus() } }
            if (editing) {
                LaunchedEffect(element.id) {
                    snapshotFlow { rich.annotatedString }.drop(1).debounce(250).collect {
                        vm.updateText(pageId, element.id, rich.toHtml(), heightPx / (PAGE_HEIGHT * density.density))
                    }
                }
            }
            val quote = element.kind == ElementKind.PDF_CLIP
            val sticky = element.fillColor
            Box(
                position.wrapContentHeight()
                    .then(if (sticky != null) Modifier.heightIn(min = (element.width * PAGE_WIDTH * 0.6f).dp)
                        .shadow(6.dp, RoundedCornerShape(6.dp)).background(Color(sticky), RoundedCornerShape(6.dp)) else Modifier)
                    .then(if (editing) Modifier.border(1.dp, Color(0xFF8B5CF6), RoundedCornerShape(4.dp)) else Modifier)
                    .then(if (quote) Modifier.background(Color(0xFFF5F3FF), RoundedCornerShape(4.dp)) else Modifier)
                    .onSizeChanged { size ->
                        heightPx = size.height
                        val fraction = size.height / (PAGE_HEIGHT * density.density)
                        if (kotlin.math.abs(fraction - element.height) > 0.004f) vm.updateElementHeight(pageId, element.id, fraction)
                    }
            ) {
                if (quote) Box(Modifier.matchParentSize()) { Box(Modifier.width(4.dp).fillMaxHeight().background(Color(0xFF8B5CF6))) }
                BasicRichTextEditor(
                    state = rich,
                    enabled = editing,
                    readOnly = !editing,
                    textStyle = noteTextStyle,
                    cursorBrush = SolidColor(Color(0xFF6D28D9)),
                    modifier = Modifier.fillMaxWidth().padding(start = if (quote) 12.dp else if (sticky != null) 14.dp else 4.dp, end = if (sticky != null) 14.dp else 4.dp,
                        top = if (sticky != null) 14.dp else 4.dp, bottom = if (element.sourcePage != null) 40.dp else 4.dp)
                        .focusRequester(focus)
                )
                SourceChip(element, Modifier.align(Alignment.BottomStart))
            }
        }
        else -> Box(position.size((element.width * PAGE_WIDTH).dp, (element.height * PAGE_HEIGHT).dp)) {
            val path = element.imagePath
            if (path != null && File(path).exists()) {
                AsyncImage(model = File(path), contentDescription = if (element.kind == ElementKind.PDF_CLIP) "PDF clip" else "Image",
                    contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFF3F4F6)), contentAlignment = Alignment.Center) {
                    Text("Image unavailable", fontSize = 14.sp, color = Color(0xFF6B7280))
                }
            }
            SourceChip(element, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun SourceChip(element: NoteElement, modifier: Modifier) {
    val page = element.sourcePage ?: return
    Text(
        "p. ${page + 1}", fontSize = 18.sp, color = Color(0xFF5B21B6),
        modifier = modifier.padding(6.dp).background(Color(0xFFEDE9FE), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}
