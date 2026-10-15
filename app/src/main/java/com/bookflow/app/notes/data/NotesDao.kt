package com.bookflow.app.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query("SELECT * FROM note_folders ORDER BY sortOrder, name COLLATE NOCASE")
    fun folders(): Flow<List<NoteFolderEntity>>

    @Query("SELECT * FROM note_folders WHERE id = :id")
    suspend fun folder(id: String): NoteFolderEntity?

    // @Upsert, never REPLACE: REPLACE deletes the old row first, which cascades to child rows
    @Upsert
    suspend fun upsertFolder(folder: NoteFolderEntity)

    @Query("UPDATE note_folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: String, name: String)

    @Query("UPDATE note_folders SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparentFolders(oldParent: String, newParent: String?)

    @Query("UPDATE notes SET folderId = :newFolder WHERE folderId = :oldFolder")
    suspend fun moveNotesBetweenFolders(oldFolder: String, newFolder: String?)

    @Query("DELETE FROM note_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun notes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun note(id: String): NoteEntity?

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("UPDATE notes SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun renameNote(id: String, title: String, now: Long)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE notes SET tags = :tags, updatedAt = :now WHERE id = :id")
    suspend fun setTags(id: String, tags: String, now: Long)

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :id")
    suspend fun moveNote(id: String, folderId: String?)

    @Query("UPDATE notes SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("UPDATE notes SET thumbnailPath = :path WHERE id = :id")
    suspend fun setThumbnail(id: String, path: String?)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String)

    @Query("SELECT * FROM note_pages WHERE noteId = :noteId ORDER BY pageIndex")
    suspend fun pages(noteId: String): List<NotePageEntity>

    @Upsert
    suspend fun upsertPages(pages: List<NotePageEntity>)

    @Query("UPDATE note_pages SET background = :background WHERE id = :pageId")
    suspend fun setBackground(pageId: String, background: String)

    @Query("DELETE FROM note_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    @Query("SELECT * FROM note_elements WHERE pageId IN (SELECT id FROM note_pages WHERE noteId = :noteId) ORDER BY z")
    suspend fun elementsForNote(noteId: String): List<NoteElementEntity>

    @Query("SELECT * FROM note_elements WHERE pageId = :pageId ORDER BY z")
    suspend fun elementsForPage(pageId: String): List<NoteElementEntity>

    @Query("DELETE FROM note_elements WHERE pageId = :pageId")
    suspend fun clearElements(pageId: String)

    @Query("DELETE FROM note_elements WHERE id = :elementId")
    suspend fun deleteElement(elementId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElements(elements: List<NoteElementEntity>)

    @Transaction
    suspend fun replaceElements(pageId: String, elements: List<NoteElementEntity>) {
        clearElements(pageId)
        insertElements(elements)
    }

    @Query("DELETE FROM notes_fts WHERE noteId = :noteId")
    suspend fun clearSearchIndex(noteId: String)

    @Insert
    suspend fun insertSearchIndex(entry: NoteFtsEntity)

    @Transaction
    suspend fun replaceSearchIndex(entry: NoteFtsEntity) {
        clearSearchIndex(entry.noteId)
        insertSearchIndex(entry)
    }

    @Query("SELECT notes.* FROM notes JOIN notes_fts ON notes.id = notes_fts.noteId WHERE notes_fts MATCH :query ORDER BY notes.updatedAt DESC")
    fun search(query: String): Flow<List<NoteEntity>>
}
