package com.bookflow.app.presentation.screens.notes.editor

import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.ink.InkStroke

/**
 * Copied ink and elements, shared by every open note so a selection can be pasted into another note.
 * Strokes are in page units and elements in page fractions, as on the page they came from. Kept in memory only.
 */
object NoteClipboard {
    data class Content(val strokes: List<InkStroke>, val elements: List<NoteElement>)

    @Volatile var content: Content? = null
        private set

    fun put(strokes: List<InkStroke>, elements: List<NoteElement>) { content = Content(strokes, elements).takeIf { strokes.isNotEmpty() || elements.isNotEmpty() } }

    val isEmpty: Boolean get() = content == null
}
