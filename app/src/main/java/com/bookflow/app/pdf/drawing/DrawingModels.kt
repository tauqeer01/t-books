package com.bookflow.app.pdf.drawing

import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class DrawingTool(val title: String) {
    PEN("Pen"),
    HIGHLIGHTER("Highlighter"),
    ERASER("Eraser"),
    LINE("Line"),
    ARROW("Arrow"),
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
    TEXT_HIGHLIGHT("Highlight"),
    TEXT_UNDERLINE("Underline"),
    TEXT_STRIKETHROUGH("Strikethrough"),
    STICKY_NOTE("Sticky note"),
    HAND("Scroll");

    val isShape: Boolean get() = this == LINE || this == ARROW || this == RECTANGLE || this == ELLIPSE

    /** Drag across text to mark whole words (snaps to the PDF text layer). */
    val isTextMarkup: Boolean get() = this == TEXT_HIGHLIGHT || this == TEXT_UNDERLINE || this == TEXT_STRIKETHROUGH

    /** Translucent tools pick from the pastel palette. */
    val usesHighlighterPalette: Boolean get() = this == HIGHLIGHTER || this == TEXT_HIGHLIGHT || this == STICKY_NOTE

    val hasColor: Boolean get() = this != ERASER && this != HAND

    val hasStrokeWidth: Boolean get() = this == PEN || this == HIGHLIGHTER || isShape
}

/**
 * Builds shape outlines as ordinary stroke points so shapes reuse stroke persistence, erasing and export.
 * Coordinates are in pixels; edges are densified so the Bezier smoothing in [DrawingStroke] keeps corners sharp.
 */
object ShapeGeometry {
    fun points(
        tool: DrawingTool,
        start: Offset,
        end: Offset,
        arrowHeadRange: ClosedFloatingPointRange<Float> = 12f..48f
    ): List<Offset> = when (tool) {
        DrawingTool.LINE -> segment(start, end)
        DrawingTool.ARROW -> {
            val angle = atan2(end.y - start.y, end.x - start.x)
            val head = (hypot(end.x - start.x, end.y - start.y) * 0.28f).coerceIn(arrowHeadRange)
            val left = Offset(end.x - head * cos(angle - ARROW_SPREAD), end.y - head * sin(angle - ARROW_SPREAD))
            val right = Offset(end.x - head * cos(angle + ARROW_SPREAD), end.y - head * sin(angle + ARROW_SPREAD))
            segment(start, end) + segment(end, left).drop(1) + segment(left, end).drop(1) + segment(end, right).drop(1)
        }
        DrawingTool.RECTANGLE -> {
            val corners = listOf(start, Offset(end.x, start.y), end, Offset(start.x, end.y), start)
            corners.zipWithNext().flatMapIndexed { i, (a, b) -> segment(a, b).let { if (i == 0) it else it.drop(1) } }
        }
        DrawingTool.ELLIPSE -> {
            val cx = (start.x + end.x) / 2f
            val cy = (start.y + end.y) / 2f
            val rx = abs(end.x - start.x) / 2f
            val ry = abs(end.y - start.y) / 2f
            (0..64).map { i ->
                val t = (2 * PI * i / 64).toFloat()
                Offset(cx + rx * cos(t), cy + ry * sin(t))
            }
        }
        else -> listOf(start, end)
    }

    private fun segment(a: Offset, b: Offset, steps: Int = 16): List<Offset> =
        (0..steps).map { i -> Offset(a.x + (b.x - a.x) * i / steps, a.y + (b.y - a.y) * i / steps) }

    private const val ARROW_SPREAD = 0.45f
}

data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val points: List<NormalizedPoint>,
    val colorHex: String,
    val strokeWidth: Float, // in dp
    val isHighlighter: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Converts normalized points to a smooth Jetpack Compose Path scaled to [pageWidth] and [pageHeight].
     * Uses quadratic Bezier curve midpoint interpolation for silky smooth rendering and low latency.
     */
    fun toComposePath(pageWidth: Float, pageHeight: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path

        val firstX = points[0].x * pageWidth
        val firstY = points[0].y * pageHeight
        path.moveTo(firstX, firstY)

        if (points.size == 1) {
            path.lineTo(firstX + 0.5f, firstY + 0.5f)
            return path
        }

        var prevX = firstX
        var prevY = firstY

        for (i in 1 until points.size) {
            val currX = points[i].x * pageWidth
            val currY = points[i].y * pageHeight
            val midX = (prevX + currX) / 2f
            val midY = (prevY + currY) / 2f

            path.quadraticBezierTo(prevX, prevY, midX, midY)
            prevX = currX
            prevY = currY
        }

        path.lineTo(prevX, prevY)
        return path
    }

    /**
     * Converts normalized points to an Android Graphics Path for native PDF canvas rendering.
     */
    fun toAndroidPath(pageWidth: Float, pageHeight: Float): AndroidPath {
        val path = AndroidPath()
        if (points.isEmpty()) return path

        val firstX = points[0].x * pageWidth
        val firstY = points[0].y * pageHeight
        path.moveTo(firstX, firstY)

        if (points.size == 1) {
            path.lineTo(firstX + 0.5f, firstY + 0.5f)
            return path
        }

        var prevX = firstX
        var prevY = firstY

        for (i in 1 until points.size) {
            val currX = points[i].x * pageWidth
            val currY = points[i].y * pageHeight
            val midX = (prevX + currX) / 2f
            val midY = (prevY + currY) / 2f

            path.quadTo(prevX, prevY, midX, midY)
            prevX = currX
            prevY = currY
        }

        path.lineTo(prevX, prevY)
        return path
    }

    /**
     * Checks if this stroke intersects with an eraser circle at normalized [normX], [normY] with radius [normRadius].
     */
    fun intersectsPoint(normX: Float, normY: Float, normRadius: Float = 0.025f): Boolean {
        if (points.isEmpty()) return false
        val rSq = normRadius * normRadius

        for (pt in points) {
            val dx = pt.x - normX
            val dy = pt.y - normY
            if (dx * dx + dy * dy <= rSq) return true
        }

        // Also check segments between points
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if (distanceSqToSegment(normX, normY, p1.x, p1.y, p2.x, p2.y) <= rSq) {
                return true
            }
        }
        return false
    }

    private fun distanceSqToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
        if (l2 == 0f) return (px - x1) * (px - x1) + (py - y1) * (py - y1)
        val t = (((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2).coerceIn(0f, 1f)
        val projX = x1 + t * (x2 - x1)
        val projY = y1 + t * (y2 - y1)
        return (px - projX) * (px - projX) + (py - projY) * (py - projY)
    }
}

/**
 * Compact string serializer and deserializer for strokes to persist in Room BookAnnotation.strokePathData.
 * Format: "v1|w:3.5|h:1|c:#FFE066|pts:0.12,0.34,1.0;0.15,0.38,0.95"
 */
object StrokeSerializer {
    fun serialize(stroke: DrawingStroke): String {
        val sb = StringBuilder()
        sb.append("v1|")
        sb.append("w:").append(stroke.strokeWidth).append("|")
        sb.append("h:").append(if (stroke.isHighlighter) "1" else "0").append("|")
        sb.append("c:").append(stroke.colorHex).append("|")
        sb.append("pts:")
        stroke.points.forEachIndexed { index, pt ->
            if (index > 0) sb.append(";")
            sb.append(String.format(java.util.Locale.US, "%.5f,%.5f,%.2f", pt.x, pt.y, pt.pressure))
        }
        return sb.toString()
    }

    fun deserialize(strokeId: String, pageIndex: Int, serialized: String, defaultColorHex: String = "#4F46E5"): DrawingStroke? {
        if (!serialized.startsWith("v1|")) return null
        try {
            val parts = serialized.split("|")
            var strokeWidth = 3.5f
            var isHighlighter = false
            var colorHex = defaultColorHex
            val points = mutableListOf<NormalizedPoint>()

            for (part in parts) {
                when {
                    part.startsWith("w:") -> {
                        strokeWidth = part.removePrefix("w:").toFloatOrNull() ?: 3.5f
                    }
                    part.startsWith("h:") -> {
                        isHighlighter = part.removePrefix("h:") == "1"
                    }
                    part.startsWith("c:") -> {
                        colorHex = part.removePrefix("c:")
                    }
                    part.startsWith("pts:") -> {
                        val ptsStr = part.removePrefix("pts:")
                        if (ptsStr.isNotBlank()) {
                            val ptList = ptsStr.split(";")
                            for (ptItem in ptList) {
                                val coords = ptItem.split(",")
                                if (coords.size >= 2) {
                                    val x = coords[0].toFloatOrNull() ?: continue
                                    val y = coords[1].toFloatOrNull() ?: continue
                                    val p = if (coords.size >= 3) coords[2].toFloatOrNull() ?: 1.0f else 1.0f
                                    points.add(NormalizedPoint(x, y, p))
                                }
                            }
                        }
                    }
                }
            }

            if (points.isEmpty()) return null

            return DrawingStroke(
                id = strokeId,
                pageIndex = pageIndex,
                points = points,
                colorHex = colorHex,
                strokeWidth = strokeWidth,
                isHighlighter = isHighlighter
            )
        } catch (_: Exception) {
            return null
        }
    }
}
