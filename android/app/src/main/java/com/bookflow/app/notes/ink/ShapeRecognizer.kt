package com.bookflow.app.notes.ink

import com.bookflow.app.notes.ink.InkGeometry.Point
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Turns a hand-drawn line, triangle, rectangle, or ellipse into a clean outline (the "auto shape" tool).
 * Returns null when the drawing is not close enough to any of them, so it can stay as freehand ink.
 */
object ShapeRecognizer {
    enum class Kind { LINE, TRIANGLE, RECTANGLE, QUADRILATERAL, ELLIPSE }
    data class Result(val kind: Kind, val points: List<Point>)

    fun recognize(raw: List<Point>): Result? {
        val points = raw.distinctConsecutive()
        if (points.size < 3) return null
        val left = points.minOf { it.x }; val right = points.maxOf { it.x }
        val top = points.minOf { it.y }; val bottom = points.maxOf { it.y }
        val diagonal = hypot(right - left, bottom - top)
        if (diagonal < 20f) return null
        val length = points.zipWithNext().sumOf { (p, q) -> hypot(q.x - p.x, q.y - p.y).toDouble() }.toFloat()
        val first = points.first(); val last = points.last()
        val closed = hypot(last.x - first.x, last.y - first.y) < max(0.2f * diagonal, 0.08f * length)

        if (!closed) {
            // A line: every point close to the chord and the path barely longer than it
            val chord = hypot(last.x - first.x, last.y - first.y)
            val straight = points.all { InkGeometry.distanceToSegment(it, first, last) < max(0.06f * chord, 6f) }
            return if (straight && length < chord * 1.2f) Result(Kind.LINE, NoteShapes.points(ShapeKind.LINE, first, last)) else null
        }

        val loop = points + first
        val corners = simplify(loop, 0.08f * diagonal).dropLast(1).let(::mergeNearby).let { mergeCollinear(it, diagonal) }
        when (corners.size) {
            3 -> return Result(Kind.TRIANGLE, NoteShapes.polygon(corners + corners.first()))
            4 -> {
                val area = polygonArea(corners)
                val box = (right - left) * (bottom - top)
                return if (box > 0 && area / box > 0.82f) {
                    Result(Kind.RECTANGLE, NoteShapes.points(ShapeKind.RECTANGLE, Point(left, top), Point(right, bottom)))
                } else Result(Kind.QUADRILATERAL, NoteShapes.polygon(corners + corners.first()))
            }
        }

        // Ellipse: points stay near the ellipse fitted to the bounding box
        val cx = (left + right) / 2; val cy = (top + bottom) / 2
        val rx = (right - left) / 2; val ry = (bottom - top) / 2
        if (min(rx, ry) < 0.15f * max(rx, ry)) return null
        val deviation = points.map { p ->
            val nx = (p.x - cx) / rx; val ny = (p.y - cy) / ry
            abs(sqrt(nx * nx + ny * ny) - 1f)
        }
        return if (deviation.average() < 0.12 && deviation.max() < 0.35f) {
            Result(Kind.ELLIPSE, NoteShapes.points(ShapeKind.ELLIPSE, Point(left, top), Point(right, bottom)))
        } else null
    }

    /** Ramer–Douglas–Peucker. */
    private fun simplify(points: List<Point>, epsilon: Float): List<Point> {
        if (points.size < 3) return points
        var index = 0
        var maxDistance = 0f
        for (i in 1 until points.lastIndex) {
            val d = InkGeometry.distanceToSegment(points[i], points.first(), points.last())
            if (d > maxDistance) { maxDistance = d; index = i }
        }
        // A closed loop starts and ends at the same point: split at the farthest point from it instead
        if (points.first() == points.last()) {
            index = points.indices.maxBy { hypot(points[it].x - points[0].x, points[it].y - points[0].y) }
            if (index == 0) return points
            return simplify(points.subList(0, index + 1), epsilon).dropLast(1) + simplify(points.subList(index, points.size), epsilon)
        }
        return if (maxDistance > epsilon) {
            simplify(points.subList(0, index + 1), epsilon).dropLast(1) + simplify(points.subList(index, points.size), epsilon)
        } else listOf(points.first(), points.last())
    }

    /** The start of a closed drawing usually adds a corner next to a real one. */
    private fun mergeNearby(corners: List<Point>): List<Point> {
        if (corners.size < 2) return corners
        val perimeter = (corners + corners.first()).zipWithNext().sumOf { (p, q) -> hypot(q.x - p.x, q.y - p.y).toDouble() }.toFloat()
        val out = corners.toMutableList()
        var i = 0
        while (out.size > 2 && i < out.size) {
            val next = out[(i + 1) % out.size]
            if (hypot(next.x - out[i].x, next.y - out[i].y) < 0.08f * perimeter) out.removeAt((i + 1) % out.size) else i++
        }
        return out
    }

    /** Drops corners where the outline barely turns. */
    private fun mergeCollinear(corners: List<Point>, diagonal: Float): List<Point> {
        val out = corners.toMutableList()
        var changed = true
        while (changed && out.size > 3) {
            changed = false
            for (i in out.indices) {
                val prev = out[(i - 1 + out.size) % out.size]; val next = out[(i + 1) % out.size]
                if (InkGeometry.distanceToSegment(out[i], prev, next) < 0.07f * diagonal) { out.removeAt(i); changed = true; break }
            }
        }
        return out
    }

    private fun polygonArea(corners: List<Point>): Float =
        abs((corners + corners.first()).zipWithNext().sumOf { (p, q) -> (p.x * q.y - q.x * p.y).toDouble() }.toFloat()) / 2

    private fun List<Point>.distinctConsecutive(): List<Point> = filterIndexed { i, p -> i == 0 || p != this[i - 1] }
}
