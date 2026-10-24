package com.bookflow.app.presentation.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.notes.domain.NoteFolder
import com.bookflow.app.notes.domain.NoteSort
import com.bookflow.app.notes.domain.NoteSummary
import com.bookflow.app.notes.domain.NotesRepository
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.presentation.screens.notes.editor.NotePenPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesHomeUiState(
    val folders: List<NoteFolder> = emptyList(),
    val currentFolder: NoteFolder? = null,
    val breadcrumb: List<NoteFolder> = emptyList(),
    val childFolders: List<NoteFolder> = emptyList(),
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val query: String = "",
    val sort: NoteSort = NoteSort.UPDATED,
    val grid: Boolean = true,
    val isEmpty: Boolean = false
)

private data class Filters(val folderId: String?, val tag: String?, val query: String, val sort: NoteSort, val grid: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesHomeViewModel(private val repository: NotesRepository, private val prefs: NotePenPreferences) : ViewModel() {
    private val filters = MutableStateFlow(Filters(null, null, "", NoteSort.UPDATED, true))

    private val source = filters.flatMapLatest { f -> if (f.query.isBlank()) repository.notes() else repository.search(f.query) }

    val state: StateFlow<NotesHomeUiState> = combine(repository.folders(), source, filters) { folders, notes, f ->
        val byId = folders.associateBy { it.id }
        val current = f.folderId?.let { byId[it] }
        val breadcrumb = generateSequence(current) { it.parentId?.let(byId::get) }.toList().reversed()
        // While searching, results come from every folder
        val inScope = if (f.query.isNotBlank() || current == null) notes else notes.filter { it.folderId in descendantsOf(current.id, folders) }
        val tagged = f.tag?.let { tag -> inScope.filter { n -> n.tags.any { it.equals(tag, true) } } } ?: inScope
        val sorted = when (f.sort) {
            NoteSort.UPDATED -> tagged.sortedByDescending { it.updatedAt }
            NoteSort.CREATED -> tagged.sortedByDescending { it.createdAt }
            NoteSort.TITLE -> tagged.sortedBy { it.title.ifBlank { "￿" }.lowercase() }
        }
        NotesHomeUiState(
            folders = folders, currentFolder = current, breadcrumb = breadcrumb,
            childFolders = folders.filter { it.parentId == current?.id },
            pinned = sorted.filter { it.pinned }, others = sorted.filterNot { it.pinned },
            allTags = notes.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            selectedTag = f.tag, query = f.query, sort = f.sort, grid = f.grid,
            isEmpty = notes.isEmpty() && f.query.isBlank()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesHomeUiState())

    private fun descendantsOf(id: String, folders: List<NoteFolder>): Set<String> {
        val result = mutableSetOf(id)
        var frontier = setOf(id)
        while (frontier.isNotEmpty()) {
            frontier = folders.filter { it.parentId in frontier && it.id !in result }.map { it.id }.toSet()
            result += frontier
        }
        return result
    }

    fun openFolder(id: String?) = filters.value.let { filters.value = it.copy(folderId = id) }
    fun selectTag(tag: String?) = filters.value.let { filters.value = it.copy(tag = if (it.tag == tag) null else tag) }
    fun search(query: String) = filters.value.let { filters.value = it.copy(query = query) }
    fun setSort(sort: NoteSort) = filters.value.let { filters.value = it.copy(sort = sort) }
    fun toggleGrid() = filters.value.let { filters.value = it.copy(grid = !it.grid) }

    val lastTemplate: PageBackground get() = prefs.lastTemplate()

    suspend fun createNote(template: PageBackground): String {
        prefs.setLastTemplate(template)
        return repository.createNote(folderId = filters.value.folderId, background = template)
    }

    fun createFolder(name: String, colorHex: String) = viewModelScope.launch { repository.createFolder(name, colorHex, filters.value.folderId) }
    fun renameFolder(id: String, name: String) = viewModelScope.launch { repository.renameFolder(id, name) }
    fun deleteFolder(folder: NoteFolder) = viewModelScope.launch {
        repository.deleteFolder(folder.id)
        if (filters.value.folderId == folder.id) openFolder(folder.parentId)
    }

    fun togglePin(note: NoteSummary) = viewModelScope.launch { repository.setPinned(note.id, !note.pinned) }
    fun rename(note: NoteSummary, title: String) = viewModelScope.launch { repository.renameNote(note.id, title) }
    fun setTags(note: NoteSummary, tags: List<String>) = viewModelScope.launch { repository.setTags(note.id, tags) }
    fun move(note: NoteSummary, folderId: String?) = viewModelScope.launch { repository.moveNote(note.id, folderId) }
    fun delete(note: NoteSummary) = viewModelScope.launch { repository.deleteNote(note.id) }

    class Factory(private val repository: NotesRepository, private val prefs: NotePenPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesHomeViewModel(repository, prefs) as T
    }
}
