package com.bookflow.app.notes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.NoteFolder
import com.bookflow.app.notes.domain.NoteSummary
import com.bookflow.app.notes.domain.PageBackground

@Entity(tableName = "note_folders")
data class NoteFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val parentId: String?,
    val sortOrder: Int,
    val createdAt: Long
) {
    fun toDomain() = NoteFolder(id, name, colorHex, parentId, sortOrder)
}

@Entity(tableName = "notes", indices = [Index("folderId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val title: String,
    val pinned: Boolean,
    /** Comma-separated; tags never contain commas. */
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String?
) {
    fun toDomain() = NoteSummary(id, title, folderId, pinned, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        createdAt, updatedAt, thumbnailPath)
}

@Entity(
    tableName = "note_pages",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId")]
)
data class NotePageEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val pageIndex: Int,
    val background: String
) {
    val backgroundType: PageBackground get() = runCatching { PageBackground.valueOf(background) }.getOrDefault(PageBackground.BLANK)
}

@Entity(
    tableName = "note_elements",
    foreignKeys = [ForeignKey(entity = NotePageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("pageId")]
)
data class NoteElementEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rotation: Float,
    val z: Int,
    val html: String?,
    val imagePath: String?,
    val quoteText: String?,
    val sourceBookId: String?,
    val sourcePage: Int?
) {
    fun toDomain() = NoteElement(id, runCatching { ElementKind.valueOf(kind) }.getOrDefault(ElementKind.TEXT), x, y, w, h, rotation, z,
        html, imagePath, quoteText, sourceBookId, sourcePage)

    companion object {
        fun from(pageId: String, e: NoteElement) = NoteElementEntity(e.id, pageId, e.kind.name, e.x, e.y, e.width, e.height, e.rotation, e.z,
            e.html, e.imagePath, e.quoteText, e.sourceBookId, e.sourcePage)
    }
}

/** Search index, rebuilt per note whenever its text changes. */
@Fts4(notIndexed = ["noteId"])
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val noteId: String,
    val title: String,
    val body: String
)
