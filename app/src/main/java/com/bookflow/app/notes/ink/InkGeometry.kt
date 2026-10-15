package com.bookflow.app.notes.ink

import kotlin.math.hypot

/** Plain geometry used by the eraser and lasso. Independent of the Ink library so it is unit-testable. */
object InkGeometry {
    data class Point(val x: Float, val y: Float)

    fun distanceToSegment(p: Point, a: Point, b: Point): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Whether any vertex of [points] is within [radius] of the eraser path segment [a]–[b]. */
    fun touches(points: List<Point>, a: Point, b: Point, radius: Float): Boolean = points.any { distanceToSegment(it, a, b) <= radius }

    /**
     * Partial erase: removes vertices within [radius] of the eraser segment and returns the remaining runs as
     * index ranges. A run needs at least two vertices to remain visible. Returns null when nothing was erased.
     */
    fun splitRuns(points: List<Point>, a: Point, b: Point, radius: Float): List<IntRange>? {
        val erased = BooleanArray(points.size) { distanceToSegment(points[it], a, b) <= radius }
        if (erased.none { it }) return null
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (i in points.indices) {
            if (!erased[i] && start < 0) start = i
            if ((erased[i] || i == points.lastIndex) && start >= 0) {
                val end = if (erased[i]) i - 1 else i
                if (end - start >= 1) runs += start..end
                start = -1
            }
        }
        return runs
    }

    /** Even–odd ray casting. */
    fun contains(polygon: List<Point>, p: Point): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > p.y) != (pj.y > p.y) && p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x) inside = !inside
            j = i
        }
        return inside
    }

    /** A stroke counts as lassoed when at least half of its vertices fall inside the lasso. */
    fun lassoed(polygon: List<Point>, points: List<Point>): Boolean =
        points.isNotEmpty() && points.count { contains(polygon, it) } * 2 >= points.size

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
        fun union(other: Bounds) = Bounds(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
        fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    }

    fun bounds(points: List<Point>, padding: Float = 0f): Bounds? = if (points.isEmpty()) null else
        Bounds(points.minOf { it.x } - padding, points.minOf { it.y } - padding, points.maxOf { it.x } + padding, points.maxOf { it.y } + padding)
}
