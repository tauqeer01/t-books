package com.bookflow.app.presentation.screens.reader

import com.bookflow.app.notes.ink.PenPreset
import com.bookflow.app.notes.ink.PenType
import com.bookflow.app.pdf.drawing.DrawingTool

/** Draws reader ink with the shared Notes pens. */
object ReaderInk {
    /**
     * Notes sizes are 1/1000 of the page width; reader strokes are 1/400 of it, and highlighters render 2.2× wide.
     * So a 0.3 mm pen in Notes is 0.3 mm on a PDF page too.
     */
    fun strokeWidth(pen: PenPreset): Float = pen.size * 0.4f / if (pen.type == PenType.HIGHLIGHTER) 2.2f else 1f

    /** Opaque ink as #RRGGBB; the pencil is softer, as in Notes. Highlighter translucency is added when drawn. */
    fun colorHex(pen: PenPreset): String = if (pen.type == PenType.PENCIL) "#B3%06X".format(pen.colorArgb and 0xFFFFFF)
        else "#%06X".format(pen.colorArgb and 0xFFFFFF)

    fun tool(pen: PenPreset): DrawingTool = if (pen.type == PenType.HIGHLIGHTER) DrawingTool.HIGHLIGHTER else DrawingTool.PEN
}
