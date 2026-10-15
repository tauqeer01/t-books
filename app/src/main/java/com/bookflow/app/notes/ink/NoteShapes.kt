package com.bookflow.app.notes.ink

import com.bookflow.app.notes.ink.InkGeometry.Point
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

enum class ShapeKind(val title: String) {
    LINE("Line"), ARROW("Arrow"), RECTANGLE("Rectangle"), ELLIPSE("Ellipse"), TRIANGLE("Triangle"),
    DIAMOND("Diamond"), PENTAGON("Pentagon"), HEXAGON("Hexagon"), STAR("Star")
}

/**
 * Outlines for the shape tool as one polyline each, dragged from [a] to [b] in page units. Edges are densified
 * so Ink's stroke smoothing keeps corners sharp and lines straight.
 */
object NoteShapes {
    private const val STEP = 6f

    fun points(kind: ShapeKind, a: Point, b: Point): List<Point> = when (kind) {
        ShapeKind.LINE -> segment(a, b)
        ShapeKind.ARROW -> {
            val angle = atan2(b.y - a.y, b.x - a.x)
            val head = (hypot(b.x - a.x, b.y - a.y) * 0.25f).coerceIn(14f, 60f)
            val left = Point(b.x - head * cos(angle - PI.toFloat() / 6), b.y - head * sin(angle - PI.toFloat() / 6))
            val right = Point(b.x - head * cos(angle + PI.toFloat() / 6), b.y - head * sin(angle + PI.toFloat() / 6))
            path(listOf(a, b, left, b, right))
        }
        ShapeKind.RECTANGLE -> path(listOf(a, Point(b.x, a.y), b, Point(a.x, b.y), a))
        ShapeKind.TRIANGLE -> path(listOf(Point((a.x + b.x) / 2, a.y), b, Point(a.x, b.y), Point((a.x + b.x) / 2, a.y)))
        ShapeKind.ELLIPSE -> {
            val cx = (a.x + b.x) / 2
            val cy = (a.y + b.y) / 2
            val rx = abs(b.x - a.x) / 2
            val ry = abs(b.y - a.y) / 2
            val n = max(24, ((rx + ry) * PI / STEP).toInt())
            List(n + 1) { i -> val t = 2 * PI * i / n; Point(cx + rx * cos(t).toFloat(), cy + ry * sin(t).toFloat()) }
        }
        ShapeKind.DIAMOND -> {
            val cx = (a.x + b.x) / 2
            val cy = (a.y + b.y) / 2
            path(listOf(Point(cx, a.y), Point(b.x, cy), Point(cx, b.y), Point(a.x, cy), Point(cx, a.y)))
        }
        ShapeKind.PENTAGON -> path(regular(5, a, b, inner = null))
        ShapeKind.HEXAGON -> path(regular(6, a, b, inner = null))
        ShapeKind.STAR -> path(regular(5, a, b, inner = 0.4f))
    }

    /** Corners of a regular polygon (or a star when [inner] is set) inscribed in the box, first point at the top. */
    private fun regular(sides: Int, a: Point, b: Point, inner: Float?): List<Point> {
        val cx = (a.x + b.x) / 2
        val cy = (a.y + b.y) / 2
        val rx = abs(b.x - a.x) / 2
        val ry = abs(b.y - a.y) / 2
        val count = if (inner != null) sides * 2 else sides
        val corners = List(count) { i ->
            val t = -PI / 2 + 2 * PI * i / count
            val r = if (inner != null && i % 2 == 1) inner else 1f
            Point(cx + rx * r * cos(t).toFloat(), cy + ry * r * sin(t).toFloat())
        }
        return corners + corners.first()
    }

    /** A closed or open outline through [corners], densified like the built-in shapes. */
    fun polygon(corners: List<Point>): List<Point> = path(corners)

    /** Corners are repeated so smoothing cannot round them off. */
    private fun path(corners: List<Point>): List<Point> =
        corners.zipWithNext().flatMap { (p, q) -> listOf(p, p) + segment(p, q).dropLast(1) } + List(3) { corners.last() }

    private fun segment(a: Point, b: Point): List<Point> {
        val n = max(1, (hypot(b.x - a.x, b.y - a.y) / STEP).toInt())
        return List(n + 1) { i -> Point(a.x + (b.x - a.x) * i / n, a.y + (b.y - a.y) * i / n) }
    }
}
