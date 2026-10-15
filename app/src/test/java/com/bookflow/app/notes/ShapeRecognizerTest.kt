package com.bookflow.app.notes

import com.bookflow.app.notes.ink.InkGeometry.Point
import com.bookflow.app.notes.ink.NoteShapes
import com.bookflow.app.notes.ink.ShapeKind
import com.bookflow.app.notes.ink.ShapeRecognizer
import com.bookflow.app.notes.ink.ShapeRecognizer.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShapeRecognizerTest {
    private val random = Random(7)

    /** Hand-drawn wobble, and the end overshooting the start a little, as people draw. */
    private fun wobbly(points: List<Point>, amount: Float = 4f) = points.map { Point(it.x + random.nextFloat() * amount - amount / 2, it.y + random.nextFloat() * amount - amount / 2) }

    private fun outline(vararg corners: Point): List<Point> {
        val loop = corners.toList() + corners.first()
        return loop.zipWithNext().flatMap { (p, q) -> List(20) { i -> Point(p.x + (q.x - p.x) * i / 20, p.y + (q.y - p.y) * i / 20) } } + Point(corners[0].x + 8, corners[0].y + 3)
    }

    @Test fun roughCircleBecomesEllipse() {
        val circle = List(80) { i -> val t = 2 * PI * i / 76; Point(300 + 100 * cos(t).toFloat(), 300 + 80 * sin(t).toFloat()) }
        val result = ShapeRecognizer.recognize(wobbly(circle, 8f))!!
        assertEquals(Kind.ELLIPSE, result.kind)
        assertEquals(200f, result.points.minOf { it.x }, 8f)
        assertEquals(400f, result.points.maxOf { it.x }, 8f)
    }

    @Test fun roughSquareBecomesRectangle() {
        val result = ShapeRecognizer.recognize(wobbly(outline(Point(100f, 100f), Point(400f, 104f), Point(398f, 300f), Point(102f, 296f))))!!
        assertEquals(Kind.RECTANGLE, result.kind)
        assertEquals(NoteShapes.points(ShapeKind.RECTANGLE, Point(result.points.minOf { it.x }, result.points.minOf { it.y }),
            Point(result.points.maxOf { it.x }, result.points.maxOf { it.y })), result.points)
    }

    @Test fun roughTriangleKeepsItsThreeCorners() {
        val result = ShapeRecognizer.recognize(wobbly(outline(Point(250f, 100f), Point(400f, 350f), Point(100f, 340f))))!!
        assertEquals(Kind.TRIANGLE, result.kind)
        listOf(Point(250f, 100f), Point(400f, 350f), Point(100f, 340f)).forEach { c ->
            assertTrue("corner near $c", result.points.any { kotlin.math.hypot(it.x - c.x, it.y - c.y) < 15f })
        }
    }

    @Test fun tiltedSquareStaysAQuadrilateral() {
        val result = ShapeRecognizer.recognize(wobbly(outline(Point(250f, 100f), Point(400f, 250f), Point(250f, 400f), Point(100f, 250f))))!!
        assertEquals(Kind.QUADRILATERAL, result.kind)
    }

    @Test fun nearlyStraightStrokeBecomesLine() {
        val line = List(40) { i -> Point(100f + i * 10, 200f + i * 3) }
        val result = ShapeRecognizer.recognize(wobbly(line, 5f))!!
        assertEquals(Kind.LINE, result.kind)
    }

    @Test fun scribbleAndOpenCurvesStayFreehand() {
        val zigzag = List(30) { i -> Point(100f + i * 12, if (i % 2 == 0) 100f else 220f) }
        assertNull(ShapeRecognizer.recognize(zigzag))
        val arc = List(40) { i -> val t = PI * i / 39; Point(300 + 100 * cos(t).toFloat(), 300 - 100 * sin(t).toFloat()) }
        assertNull(ShapeRecognizer.recognize(arc))
        assertNull(ShapeRecognizer.recognize(listOf(Point(1f, 1f), Point(2f, 2f), Point(3f, 3f))))
    }

    @Test fun newShapesAreClosedOutlines() {
        listOf(ShapeKind.DIAMOND, ShapeKind.PENTAGON, ShapeKind.HEXAGON, ShapeKind.STAR).forEach { kind ->
            val points = NoteShapes.points(kind, Point(100f, 100f), Point(300f, 300f))
            assertEquals(kind.title, points.first(), points.last())
            assertEquals(kind.title, 200f, points.first().x, 0.5f) // starts at top center
            assertTrue(kind.title, points.all { it.x in 99f..301f && it.y in 99f..301f })
        }
    }
}
