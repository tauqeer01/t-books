package com.bookflow.app.notes

import com.bookflow.app.notes.domain.CapturePlacement
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.HtmlText
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.notes.ink.InkGeometry
import com.bookflow.app.notes.ink.InkGeometry.Point
import com.bookflow.app.presentation.screens.notes.editor.EditorHistory
import com.bookflow.app.presentation.screens.notes.editor.EditorPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesLogicTest {
    private fun element(y: Float, h: Float) = NoteElement("e$y", ElementKind.TEXT, 0.1f, y, 0.5f, h)

    @Test fun captureGoesToTopOfEmptyPage() {
        val slot = CapturePlacement.place(emptyList(), 0.5f, 0.2f)
        assertFalse(slot.onNewPage)
        assertEquals(CapturePlacement.MARGIN, slot.y, 1e-6f)
    }

    @Test fun captureGoesBelowLowestElement() {
        val slot = CapturePlacement.place(listOf(element(0.1f, 0.2f), element(0.4f, 0.1f)), 0.5f, 0.2f)
        assertFalse(slot.onNewPage)
        assertEquals(0.5f + CapturePlacement.GAP, slot.y, 1e-6f)
    }

    @Test fun captureThatDoesNotFitStartsANewPage() {
        val slot = CapturePlacement.place(listOf(element(0.1f, 0.7f)), 0.5f, 0.3f)
        assertTrue(slot.onNewPage)
        assertEquals(CapturePlacement.MARGIN, slot.y, 1e-6f)
    }

    @Test fun oversizedCaptureIsScaledToFitOnePage() {
        val slot = CapturePlacement.place(emptyList(), 2f, 3f)
        assertTrue(slot.width <= 1f - 2 * CapturePlacement.MARGIN + 1e-6f)
        assertTrue(slot.y + slot.height <= 1f - CapturePlacement.MARGIN + 1e-6f)
        assertEquals(2f / 3f, slot.width / slot.height, 1e-4f)
    }

    @Test fun htmlBecomesSearchablePlainText() {
        val html = "<h1>Lift</h1><p>L = &frac12; is <b>not</b> decoded &amp; kept</p><ul><li>one</li><li>two&nbsp;items</li></ul><p>&#8364;5 &#x3c0;</p>"
        val plain = HtmlText.toPlain(html)
        assertTrue(plain.startsWith("Lift\n"))
        assertTrue(plain.contains("not decoded & kept"))
        assertTrue(plain.contains("one\ntwo items"))
        assertTrue(plain.contains("€5 π"))
        assertFalse(plain.contains("<"))
        assertEquals("", HtmlText.toPlain(null))
    }

    @Test fun ftsQueryUsesPrefixTermsAndDropsOperators() {
        assertEquals("lift* coeff*", HtmlText.ftsQuery("  lift \"coeff\" "))
        assertEquals("NEAR*", HtmlText.ftsQuery("NEAR"))
        assertNull(HtmlText.ftsQuery(" * - "))
    }

    @Test fun escapeProtectsQuotedPdfText() {
        assertEquals("a &lt;b&gt; &amp; &quot;c&quot;", HtmlText.escape("a <b> & \"c\""))
    }

    @Test fun segmentDistanceHandlesEndpointsAndDegenerateSegments() {
        assertEquals(1f, InkGeometry.distanceToSegment(Point(5f, 1f), Point(0f, 0f), Point(10f, 0f)), 1e-5f)
        assertEquals(5f, InkGeometry.distanceToSegment(Point(-3f, 4f), Point(0f, 0f), Point(10f, 0f)), 1e-5f)
        assertEquals(5f, InkGeometry.distanceToSegment(Point(3f, 4f), Point(0f, 0f), Point(0f, 0f)), 1e-5f)
    }

    @Test fun partialEraseSplitsAStrokeIntoRemainingRuns() {
        val line = (0..10).map { Point(it * 10f, 0f) }
        val runs = InkGeometry.splitRuns(line, Point(50f, -10f), Point(50f, 10f), 6f)!!
        assertEquals(listOf(0..4, 6..10), runs)
        assertNull(InkGeometry.splitRuns(line, Point(0f, 50f), Point(100f, 50f), 6f))
        // Erasing all but single isolated vertices leaves nothing visible
        assertEquals(emptyList<IntRange>(), InkGeometry.splitRuns(line.take(2), Point(0f, 0f), Point(10f, 0f), 1f))
    }

    @Test fun lassoSelectsStrokesMostlyInside() {
        val lasso = listOf(Point(0f, 0f), Point(100f, 0f), Point(100f, 100f), Point(0f, 100f))
        assertTrue(InkGeometry.lassoed(lasso, listOf(Point(10f, 10f), Point(50f, 50f), Point(150f, 50f))))
        assertFalse(InkGeometry.lassoed(lasso, listOf(Point(10f, 10f), Point(150f, 50f), Point(160f, 50f))))
        assertFalse(InkGeometry.contains(lasso.take(2), Point(1f, 1f)))
    }

    @Test fun historyUndoesAndRedoesAndDropsRedoOnNewEdit() {
        val history = EditorHistory(limit = 2)
        val p0 = EditorPage("p", PageBackground.BLANK, emptyList(), emptyList())
        val p1 = p0.copy(elements = listOf(element(0.1f, 0.1f)))
        val p2 = p1.copy(background = PageBackground.GRID)
        val p3 = p2.copy(background = PageBackground.DOTTED)
        history.record(p0, p1); history.record(p1, p2); history.record(p2, p3)
        assertEquals(p2, history.undo())
        assertEquals(p1, history.undo())
        assertNull("limit keeps only the last two steps", history.undo())
        assertEquals(p2, history.redo())
        history.record(p2, p0)
        assertFalse(history.canRedo)
        history.record(p0, p0)
        assertEquals(p2, history.undo())
    }
}
