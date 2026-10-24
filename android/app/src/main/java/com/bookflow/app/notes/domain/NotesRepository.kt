package com.bookflow.app.notes.domain

import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun folders(): Flow<List<NoteFolder>>
    fun notes(): Flow<List<NoteSummary>>
    /** Full-text search over titles, typed text, tags, and PDF clip quotes. */
    fun search(query: String): Flow<List<NoteSummary>>

    suspend fun createFolder(name: String, colorHex: String, parentId: String? = null): String
    suspend fun renameFolder(id: String, name: String)
    /** Notes inside move to the folder's parent; child folders move up a level. */
    suspend fun deleteFolder(id: String)

    suspend fun createNote(folderId: String? = null, title: String = "", background: PageBackground = PageBackground.LINED): String
    suspend fun loadNote(id: String): NoteDocument?
    suspend fun renameNote(id: String, title: String)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setTags(id: String, tags: List<String>)
    suspend fun moveNote(id: String, folderId: String?)
    suspend fun deleteNote(id: String)

    suspend fun addPage(noteId: String, index: Int, background: PageBackground): NotePage
    suspend fun deletePage(noteId: String, pageId: String)
    suspend fun setPageBackground(pageId: String, background: PageBackground)
    suspend fun reorderPages(noteId: String, orderedPageIds: List<String>)

    /** Replaces every element on a page; the editor saves a page's elements as one unit. */
    suspend fun saveElements(noteId: String, pageId: String, elements: List<NoteElement>)
    suspend fun touch(noteId: String)
    suspend fun setThumbnail(noteId: String, path: String?)

    /**
     * Places [element] below the lowest element on the note's last page, appending a page when it does not fit.
     * [element] geometry supplies width/height; x/y are chosen by the repository. Returns the placed element.
     */
    suspend fun appendElement(noteId: String, element: NoteElement): Pair<String, NoteElement>
    suspend fun removeElement(noteId: String, pageId: String, elementId: String)
}
