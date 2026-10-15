package com.bookflow.app.presentation.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.notes.data.CaptureSource
import com.bookflow.app.notes.data.NoteCapture
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.NoteSummary
import com.bookflow.app.notes.domain.NotesRepository
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.presentation.screens.notes.editor.NotePenPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of a capture, for the confirmation snackbar. */
data class CaptureDelivered(val noteId: String, val noteTitle: String, val pageId: String, val elementId: String)

data class ReaderCaptureState(
    /** Waiting for the user to choose a destination note. */
    val pending: CaptureSource? = null,
    val notes: List<NoteSummary> = emptyList(),
    val lastNote: NoteSummary? = null,
    /** Note shown beside the reader; captures go straight to it while open. */
    val panelNoteId: String? = null,
    val delivered: CaptureDelivered? = null,
    val error: String? = null
)

class ReaderCaptureViewModel(
    private val repository: NotesRepository,
    private val capture: NoteCapture,
    private val prefs: NotePenPreferences
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderCaptureState())
    val state: StateFlow<ReaderCaptureState> = _state.asStateFlow()

    private var bookId = ""
    private var bookTitle = "Book"

    init {
        viewModelScope.launch {
            repository.notes().collect { notes ->
                _state.update { s -> s.copy(notes = notes, lastNote = notes.firstOrNull { it.id == prefs.lastCaptureNoteId() }) }
            }
        }
    }

    fun setBook(id: String, title: String) { bookId = id; bookTitle = title.ifBlank { "Book" } }

    /** Captures go to the open side panel; otherwise the destination sheet asks where. */
    fun capture(source: CaptureSource) = _state.update { it.copy(pending = source) }

    fun dismiss() = _state.update { it.copy(pending = null) }

    /** Sends the pending capture to [noteId], or to a new note named after the book when null. */
    fun sendTo(noteId: String?) {
        val source = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        viewModelScope.launch {
            try {
                val target = noteId ?: repository.createNote(title = bookTitle, background = PageBackground.LINED)
                val element = withContext(Dispatchers.IO) { capture.element(target, source, bookTitle) }
                val (pageId, placed) = repository.appendElement(target, element)
                prefs.setLastCaptureNoteId(target)
                val title = repository.loadNote(target)?.summary?.title?.ifBlank { "Untitled note" } ?: bookTitle
                _state.update { it.copy(delivered = CaptureDelivered(target, title, pageId, placed.id)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't add to note: ${e.message.orEmpty()}") }
            }
        }
    }

    /** Builds the element for the open panel; the panel's editor places and saves it. */
    suspend fun elementForPanel(source: CaptureSource): NoteElement? {
        val noteId = _state.value.panelNoteId ?: return null
        prefs.setLastCaptureNoteId(noteId)
        return withContext(Dispatchers.IO) { capture.element(noteId, source, bookTitle) }
    }

    fun undoDelivered() {
        val delivered = _state.value.delivered ?: return
        _state.update { it.copy(delivered = null) }
        viewModelScope.launch { repository.removeElement(delivered.noteId, delivered.pageId, delivered.elementId) }
    }

    fun consumeDelivered() = _state.update { it.copy(delivered = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    fun openPanel(noteId: String?) {
        viewModelScope.launch {
            val id = noteId ?: repository.createNote(title = bookTitle, background = PageBackground.LINED)
            prefs.setLastCaptureNoteId(id)
            _state.update { it.copy(panelNoteId = id) }
        }
    }

    fun closePanel() = _state.update { it.copy(panelNoteId = null) }

    class Factory(
        private val repository: NotesRepository,
        private val capture: NoteCapture,
        private val prefs: NotePenPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderCaptureViewModel(repository, capture, prefs) as T
    }
}
