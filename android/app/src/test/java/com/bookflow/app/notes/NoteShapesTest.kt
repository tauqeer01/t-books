package com.bookflow.app.notes

import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.notes.ink.InkGeometry.Point
import com.bookflow.app.notes.ink.NoteShapes
import com.bookflow.app.notes.ink.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class NoteShapesTest {
    private val a = Point(100f, 100f)
    private val b = Point(400f, 300f)

    @Test fun rectanglePassesThroughAllFourCornersAndCloses() {
        val points = NoteShapes.points(ShapeKind.RECTANGLE, a, b)
        listOf(a, Point(b.x, a.y), b, Point(a.x, b.y)).forEach { corner -> assertTrue("corner $corner", points.contains(corner)) }
        assertEquals(points.first(), points.last())
        assertTrue("edges are densified", points.size > 50)
        assertTrue("no gaps along edges", points.zipWithNext().all { (p, q) -> hypot(q.x - p.x, q.y - p.y) <= 6.5f })
    }

    @Test fun ellipseStaysOnItsBoundingBoxAndCloses() {
        val points = NoteShapes.points(ShapeKind.ELLIPSE, a, b)
        assertEquals(points.first().x, points.last().x, 1e-3f)
        assertEquals(points.first().y, points.last().y, 1e-3f)
        assertEquals(a.x, points.minOf { it.x }, 1f)
        assertEquals(b.x, points.maxOf { it.x }, 1f)
        assertEquals(a.y, points.minOf { it.y }, 1f)
        assertEquals(b.y, points.maxOf { it.y }, 1f)
    }

    @Test fun arrowEndsWithAHeadAtTheTip() {
        val points = NoteShapes.points(ShapeKind.ARROW, a, b)
        assertEquals(a, points.first())
        assertTrue(points.count { it == b } >= 2)
        assertTrue("head extends back from the tip", points.any { hypot(it.x - b.x, it.y - b.y) in 13f..61f && it.x < b.x })
    }

    @Test fun triangleApexIsTopCenter() {
        val points = NoteShapes.points(ShapeKind.TRIANGLE, a, b)
        assertEquals(Point(250f, 100f), points.first())
        assertTrue(points.contains(b) && points.contains(Point(a.x, b.y)))
    }

    @Test fun templatesKeepTheirPersistedNames() {
        // Saved pages store these names; renaming one would silently reset users' pages to blank
        assertEquals(listOf("BLANK", "LINED", "NARROW", "GRID", "GRAPH", "DOTTED", "CORNELL", "MUSIC", "ISOMETRIC"), PageBackground.entries.map { it.name })
    }
}
