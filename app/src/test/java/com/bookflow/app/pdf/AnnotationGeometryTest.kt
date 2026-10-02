package com.bookflow.app.pdf

import androidx.compose.ui.geometry.Offset
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.pdf.drawing.DrawingTool
import com.bookflow.app.pdf.drawing.ShapeGeometry
import com.bookflow.app.pdf.engine.MarkupRects
import com.bookflow.app.pdf.engine.PdfRect
import com.bookflow.app.pdf.engine.isStickyNote
import com.bookflow.app.pdf.engine.markupRects
import com.bookflow.app.pdf.engine.mergedIntoLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationGeometryTest {

    @Test fun glyphRectsMergeIntoOneRectPerLine() {
        val glyphs = listOf(
            PdfRect(0.10f, 0.20f, 0.12f, 0.22f),
            PdfRect(0.12f, 0.20f, 0.14f, 0.225f),
            PdfRect(0.15f, 0.20f, 0.18f, 0.22f),
            // Next line wraps back to the left margin
            PdfRect(0.10f, 0.24f, 0.13f, 0.26f),
            PdfRect(0.13f, 0.24f, 0.16f, 0.26f)
        )
        val lines = glyphs.mergedIntoLines()
        assertEquals(2, lines.size)
        assertEquals(PdfRect(0.10f, 0.20f, 0.18f, 0.225f), lines[0])
        assertEquals(PdfRect(0.10f, 0.24f, 0.16f, 0.26f), lines[1])
    }

    @Test fun markupRectsRoundTripThroughStorage() {
        val rects = listOf(PdfRect(0.1f, 0.2f, 0.9f, 0.22f), PdfRect(0.1f, 0.24f, 0.5f, 0.26f))
        val decoded = MarkupRects.decode(MarkupRects.encode(rects))!!
        assertEquals(2, decoded.size)
        decoded.zip(rects).forEach { (a, b) ->
            assertEquals(b.left, a.left, 1e-4f); assertEquals(b.top, a.top, 1e-4f)
            assertEquals(b.right, a.right, 1e-4f); assertEquals(b.bottom, a.bottom, 1e-4f)
        }
        assertEquals(PdfRect(0.1f, 0.2f, 0.9f, 0.26f), MarkupRects.union(rects))
    }

    @Test fun annotationsWithoutStoredLinesFallBackToTheirRect() {
        val legacy = BookAnnotation(id = "a", bookId = "b", pageIndex = 0, rectLeft = .1f, rectTop = .2f, rectRight = .3f, rectBottom = .25f)
        assertEquals(listOf(PdfRect(.1f, .2f, .3f, .25f)), legacy.markupRects())
        // Pen strokes also live in strokePathData; they must not be read as markup lines
        assertEquals(1, legacy.copy(strokePathData = "v1|w:3.5|h:0|c:#000000|pts:0.1,0.1,1.0").markupRects().size)
    }

    @Test fun stickyNotesAreNotesWithoutSelectedText() {
        val note = BookAnnotation(id = "n", bookId = "b", pageIndex = 0, type = AnnotationType.NOTE)
        assertTrue(note.isStickyNote)
        assertFalse(note.copy(selectedText = "Cobalt").isStickyNote)
        assertFalse(note.copy(type = AnnotationType.HIGHLIGHT).isStickyNote)
    }

    @Test fun rectangleOutlineIsClosedAndHitsAllCorners() {
        val points = ShapeGeometry.points(DrawingTool.RECTANGLE, Offset(10f, 20f), Offset(110f, 80f))
        assertEquals(points.first(), points.last())
        listOf(Offset(10f, 20f), Offset(110f, 20f), Offset(110f, 80f), Offset(10f, 80f)).forEach { corner ->
            assertTrue("missing corner $corner", points.any { (it - corner).getDistance() < 0.01f })
        }
    }

    @Test fun arrowEndsAtTipWithTwoHeadStrokes() {
        val points = ShapeGeometry.points(DrawingTool.ARROW, Offset(0f, 0f), Offset(200f, 0f))
        // The tip is visited twice: end of the shaft and the return from the first head stroke
        assertEquals(2, points.count { (it - Offset(200f, 0f)).getDistance() < 0.01f })
        assertTrue(points.all { it.x <= 200.01f })
    }
}
