package com.bookflow.app.presentation.screens.notes.editor

/**
 * Note-wide undo/redo of page content. Each step stores the page before and after, which keeps ink, text,
 * and element changes uniform. Page snapshots share immutable strokes, so steps are cheap.
 */
class EditorHistory(private val limit: Int = 100) {
    private data class Step(val before: EditorPage, val after: EditorPage)

    private val undo = ArrayDeque<Step>()
    private val redo = ArrayDeque<Step>()

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun record(before: EditorPage, after: EditorPage) {
        if (before == after) return
        undo.addLast(Step(before, after))
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    /** Returns the page state to restore. */
    fun undo(): EditorPage? = undo.removeLastOrNull()?.also { redo.addLast(it) }?.before
    fun redo(): EditorPage? = redo.removeLastOrNull()?.also { undo.addLast(it) }?.after

    /** A deleted page can no longer be restored by content steps. */
    fun forgetPage(pageId: String) {
        undo.removeAll { it.before.id == pageId }
        redo.removeAll { it.before.id == pageId }
    }
}
