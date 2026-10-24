package com.bookflow.app.notes.data

import com.bookflow.app.notes.domain.CapturePlacement
import com.bookflow.app.notes.domain.HtmlText
import com.bookflow.app.notes.domain.NoteDocument
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.NotePage
import com.bookflow.app.notes.domain.NoteSummary
import com.bookflow.app.notes.domain.NotesRepository
import com.bookflow.app.notes.domain.PageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NotesRepositoryImpl(
    private val dao: NotesDao,
    private val files: NoteFileStore,
    private val clock: () -> Long = System::currentTimeMillis
) : NotesRepository {
    // Page structure edits (append/add/delete) must not interleave for the same note.
    private val structureLock = Mutex()

    override fun folders() = dao.folders().map { list -> list.map { it.toDomain() } }
    override fun notes() = dao.notes().map { list -> list.map { it.toDomain() } }
    override fun search(query: String): Flow<List<NoteSummary>> {
        val match = HtmlText.ftsQuery(query) ?: return flowOf(emptyList())
        return dao.search(match).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun createFolder(name: String, colorHex: String, parentId: String?): String = withContext(Dispatchers.IO) {
        UUID.randomUUID().toString().also { dao.upsertFolder(NoteFolderEntity(it, name.trim(), colorHex, parentId, 0, clock())) }
    }

    override suspend fun renameFolder(id: String, name: String) = withContext(Dispatchers.IO) { dao.renameFolder(id, name.trim()) }

    override suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        val parent = dao.folder(id)?.parentId
        dao.moveNotesBetweenFolders(id, parent)
        dao.reparentFolders(id, parent)
        dao.deleteFolder(id)
    }

    override suspend fun createNote(folderId: String?, title: String, background: PageBackground): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = clock()
        dao.upsertNote(NoteEntity(id, folderId, title.trim(), false, "", now, now, null))
        dao.upsertPages(listOf(NotePageEntity(UUID.randomUUID().toString(), id, 0, background.name)))
        reindex(id)
        id
    }

    override suspend fun loadNote(id: String): NoteDocument? = withContext(Dispatchers.IO) {
        val note = dao.note(id) ?: return@withContext null
        val elements = dao.elementsForNote(id).groupBy { it.pageId }
        val pages = dao.pages(id).mapIndexed { index, page ->
            NotePage(page.id, index, page.backgroundType, elements[page.id].orEmpty().map { it.toDomain() })
        }
        NoteDocument(note.toDomain(), pages)
    }

    override suspend fun renameNote(id: String, title: String) = withContext(Dispatchers.IO) {
        dao.renameNote(id, title.trim(), clock()); reindex(id)
    }

    override suspend fun setPinned(id: String, pinned: Boolean) = withContext(Dispatchers.IO) { dao.setPinned(id, pinned) }

    override suspend fun setTags(id: String, tags: List<String>) = withContext(Dispatchers.IO) {
        val clean = tags.map { it.replace(",", " ").trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        dao.setTags(id, clean.joinToString(","), clock()); reindex(id)
    }

    override suspend fun moveNote(id: String, folderId: String?) = withContext(Dispatchers.IO) { dao.moveNote(id, folderId) }

    override suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        dao.deleteNote(id) // pages and elements cascade
        dao.clearSearchIndex(id)
        files.deleteNote(id)
    }

    override suspend fun addPage(noteId: String, index: Int, background: PageBackground): NotePage = withContext(Dispatchers.IO) {
        structureLock.withLock { insertPage(noteId, index, background) }
    }

    private suspend fun insertPage(noteId: String, index: Int, background: PageBackground): NotePage {
        val pages = dao.pages(noteId).toMutableList()
        val page = NotePageEntity(UUID.randomUUID().toString(), noteId, 0, background.name)
        pages.add(index.coerceIn(0, pages.size), page)
        dao.upsertPages(pages.mapIndexed { i, p -> p.copy(pageIndex = i) })
        dao.touch(noteId, clock())
        return NotePage(page.id, pages.indexOf(page), background, emptyList())
    }

    override suspend fun deletePage(noteId: String, pageId: String) = withContext(Dispatchers.IO) {
        structureLock.withLock {
            val elements = dao.elementsForPage(pageId)
            dao.deletePage(pageId)
            elements.mapNotNull { it.imagePath }.forEach { File(it).delete() }
            files.deletePage(noteId, pageId)
            val remaining = dao.pages(noteId)
            if (remaining.isEmpty()) dao.upsertPages(listOf(NotePageEntity(UUID.randomUUID().toString(), noteId, 0, PageBackground.LINED.name)))
            else dao.upsertPages(remaining.mapIndexed { i, p -> p.copy(pageIndex = i) })
            dao.touch(noteId, clock())
            reindex(noteId)
        }
    }

    override suspend fun setPageBackground(pageId: String, background: PageBackground) = withContext(Dispatchers.IO) {
        dao.setBackground(pageId, background.name)
    }

    override suspend fun reorderPages(noteId: String, orderedPageIds: List<String>) = withContext(Dispatchers.IO) {
        structureLock.withLock {
            val byId = dao.pages(noteId).associateBy { it.id }
            dao.upsertPages(orderedPageIds.mapNotNull { byId[it] }.mapIndexed { i, p -> p.copy(pageIndex = i) })
            dao.touch(noteId, clock())
        }
    }

    override suspend fun saveElements(noteId: String, pageId: String, elements: List<NoteElement>) = withContext(Dispatchers.IO) {
        val removedImages = dao.elementsForPage(pageId).mapNotNull { it.imagePath }.toSet() - elements.mapNotNull { it.imagePath }.toSet()
        dao.replaceElements(pageId, elements.map { NoteElementEntity.from(pageId, it) })
        removedImages.forEach { File(it).delete() }
        dao.touch(noteId, clock())
        reindex(noteId)
    }

    override suspend fun touch(noteId: String) = withContext(Dispatchers.IO) { dao.touch(noteId, clock()) }

    override suspend fun setThumbnail(noteId: String, path: String?) = withContext(Dispatchers.IO) { dao.setThumbnail(noteId, path) }

    override suspend fun appendElement(noteId: String, element: NoteElement): Pair<String, NoteElement> = withContext(Dispatchers.IO) {
        structureLock.withLock {
            val pages = dao.pages(noteId)
            val last = pages.lastOrNull()
            val existing = last?.let { dao.elementsForPage(it.id).map { e -> e.toDomain() } }.orEmpty()
            val slot = CapturePlacement.place(existing, element.width, element.height)
            val pageId = if (last == null || slot.onNewPage) insertPage(noteId, pages.size, last?.backgroundType ?: PageBackground.LINED).id else last.id
            val placed = element.copy(x = slot.x, y = slot.y, width = slot.width, height = slot.height,
                z = (existing.maxOfOrNull { it.z } ?: 0) + 1)
            dao.insertElements(listOf(NoteElementEntity.from(pageId, placed)))
            dao.touch(noteId, clock())
            reindex(noteId)
            pageId to placed
        }
    }

    override suspend fun removeElement(noteId: String, pageId: String, elementId: String) = withContext(Dispatchers.IO) {
        dao.elementsForPage(pageId).firstOrNull { it.id == elementId }?.imagePath?.let { File(it).delete() }
        dao.deleteElement(elementId)
        dao.touch(noteId, clock())
        reindex(noteId)
    }

    private suspend fun reindex(noteId: String) {
        val note = dao.note(noteId) ?: return
        val body = buildString {
            append(note.tags.replace(',', ' ')).append('\n')
            dao.elementsForNote(noteId).forEach { e ->
                append(HtmlText.toPlain(e.html)).append('\n')
                e.quoteText?.let { append(it).append('\n') }
            }
        }
        dao.replaceSearchIndex(NoteFtsEntity(noteId = noteId, title = note.title, body = body))
    }
}
