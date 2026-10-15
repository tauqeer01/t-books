package com.bookflow.app.presentation.screens.notes.editor

import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.notes.ink.InkGeometry
import com.bookflow.app.notes.ink.InkStroke
import com.bookflow.app.notes.ink.PenPreset

enum class EditorTool { PEN, ERASER, LASSO, TEXT, HAND }

enum class EraserMode(val title: String) { STROKE("Stroke"), PARTIAL("Partial") }

data class EditorPage(
    val id: String,
    val background: PageBackground,
    val elements: List<NoteElement>,
    val strokes: List<InkStroke>,
    /** Set when this page's ink file could not be read; the rest of the note still opens. */
    val inkUnavailable: Boolean = false
)

/** Lasso or tap selection: strokes and elements on one page, moved and resized together. */
data class EditorSelection(val pageId: String, val strokeIds: Set<String>, val elementIds: Set<String>) {
    val isEmpty get() = strokeIds.isEmpty() && elementIds.isEmpty()
}

data class NoteEditorUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val pages: List<EditorPage> = emptyList(),
    val tool: EditorTool = EditorTool.PEN,
    val presets: List<PenPreset> = emptyList(),
    val activePreset: Int = 0,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val eraserSize: Float = 24f,
    val selection: EditorSelection? = null,
    /** Text element currently being typed into. */
    val editingElementId: String? = null,
    /** Once a stylus is seen, fingers navigate and only the stylus draws (unless [fingerDrawing] is forced on). */
    val stylusSeen: Boolean = false,
    val fingerDrawing: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: String? = null,
    val pendingSourceBookId: String? = null,
    val pendingSourcePage: Int? = null
) {
    val activePen: PenPreset? get() = presets.getOrNull(activePreset)
    /** Whether a finger touch should ink in drawing tools. */
    val fingerInks: Boolean get() = fingerDrawing && !stylusSeen
    fun page(id: String) = pages.firstOrNull { it.id == id }
}

/** Selection bounds in page units, from strokes plus elements (elements are page fractions). */
fun EditorPage.selectionBounds(selection: EditorSelection, pageWidth: Float, pageHeight: Float): InkGeometry.Bounds? {
    val strokeBounds = strokes.filter { it.id in selection.strokeIds }.mapNotNull { it.bounds }
    val elementBounds = elements.filter { it.id in selection.elementIds }.map {
        InkGeometry.Bounds(it.x * pageWidth, it.y * pageHeight, it.right * pageWidth, it.bottom * pageHeight)
    }
    return (strokeBounds + elementBounds).reduceOrNull { a, b -> a.union(b) }
}
