package com.bookflow.app.notes.domain

/**
 * Notes use fixed A4-ratio pages. Element geometry is stored as fractions of the page (0..1) and ink in
 * [PAGE_WIDTH] × [PAGE_HEIGHT] page units, so a note looks identical on phone, tablet, and in export.
 */
const val PAGE_WIDTH = 1000f
const val PAGE_HEIGHT = 1414f

/** Paper templates. Persisted by name; unknown names fall back to [BLANK]. Spacing is real-world mm on A4. */
enum class PageBackground(val title: String, val description: String) {
    BLANK("Drawing canvas", "Plain page for sketches and diagrams"),
    LINED("Ruled", "Wide lines for handwriting and typing"),
    NARROW("Narrow ruled", "College-ruled lines, more per page"),
    GRID("Square grid", "5 mm squares"),
    GRAPH("Math graph", "1 mm / 5 mm / 1 cm graph paper"),
    DOTTED("Dot grid", "5 mm dots for bullet journaling"),
    CORNELL("Cornell notes", "Cues, notes, and summary areas"),
    MUSIC("Music staff", "Staves for notation"),
    ISOMETRIC("Isometric", "30° grid for 3D drawing")
}

/** [TAPE] is a study cover drawn over ink and text; tap it to reveal what is underneath. */
enum class ElementKind { TEXT, IMAGE, PDF_CLIP, TAPE }

data class NoteFolder(
    val id: String,
    val name: String,
    val colorHex: String,
    val parentId: String? = null,
    val sortOrder: Int = 0
)

data class NoteSummary(
    val id: String,
    val title: String,
    val folderId: String?,
    val pinned: Boolean,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String?
)

data class NoteElement(
    val id: String,
    val kind: ElementKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val z: Int = 0,
    /** Rich text for [ElementKind.TEXT] (and the quote of a text clip), as HTML. */
    val html: String? = null,
    val imagePath: String? = null,
    /** Plain text of a PDF text clip, kept for search and attribution. */
    val quoteText: String? = null,
    val sourceBookId: String? = null,
    val sourcePage: Int? = null,
    /** Background of a sticky note or color of a tape (ARGB); null for plain text boxes, images, and clips. */
    val fillColor: Int? = null
) {
    val bottom: Float get() = y + height
    val right: Float get() = x + width
}

data class NotePage(
    val id: String,
    val index: Int,
    val background: PageBackground,
    val elements: List<NoteElement>
)

data class NoteDocument(val summary: NoteSummary, val pages: List<NotePage>)

enum class NoteSort(val title: String) { UPDATED("Last edited"), CREATED("Date created"), TITLE("Title") }
