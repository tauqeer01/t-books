package com.bookflow.app.notes.domain

/** Where captured content (a PDF quote or clip) lands on a note: below everything already on the last page. */
object CapturePlacement {
    const val MARGIN = 0.06f
    const val GAP = 0.02f

    data class Slot(val onNewPage: Boolean, val x: Float, val y: Float, val width: Float, val height: Float)

    /**
     * [width]/[height] are page fractions. Content taller than a page is scaled down to fit one page;
     * if it does not fit below the existing elements, it goes to the top of a new page.
     */
    fun place(existing: List<NoteElement>, width: Float, height: Float): Slot {
        val maxWidth = 1f - 2 * MARGIN
        val maxHeight = 1f - 2 * MARGIN
        val scale = minOf(1f, maxWidth / width.coerceAtLeast(1e-4f), maxHeight / height.coerceAtLeast(1e-4f))
        val w = width * scale
        val h = height * scale
        val top = existing.maxOfOrNull { it.bottom }?.let { it + GAP } ?: MARGIN
        return if (top.coerceAtLeast(MARGIN) + h <= 1f - MARGIN) Slot(false, MARGIN, top.coerceAtLeast(MARGIN), w, h)
        else Slot(true, MARGIN, MARGIN, w, h)
    }
}
