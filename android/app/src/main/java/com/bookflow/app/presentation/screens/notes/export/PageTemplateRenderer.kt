package com.bookflow.app.presentation.screens.notes.export

import android.graphics.Canvas
import android.graphics.Paint
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.notes.domain.PageBackground
import kotlin.math.sqrt

/** Draws paper templates in page units scaled by [scale]. One page unit is 0.21 mm on A4. */
object PageTemplateRenderer {
    /** Page units per millimetre on A4 (210 mm wide). */
    const val MM = PAGE_WIDTH / 210f
    const val RULED_SPACING = 44f
    private const val NARROW_SPACING = 34f
    private const val MARGIN = 44f

    private const val LIGHT = 0xFFD7DCE5.toInt()
    private const val FAINT = 0xFFEBEEF3.toInt()
    private const val MEDIUM = 0xFFC3CAD6.toInt()
    private const val ACCENT = 0xFFF2B8B5.toInt()

    fun draw(canvas: Canvas, background: PageBackground, scale: Float) {
        val w = PAGE_WIDTH
        val h = PAGE_HEIGHT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 1.2f) {
            paint.color = color; paint.strokeWidth = (width * scale).coerceAtLeast(1f)
            canvas.drawLine(x1 * scale, y1 * scale, x2 * scale, y2 * scale, paint)
        }
        fun ruled(top: Float, bottom: Float, left: Float, right: Float, spacing: Float) {
            var y = top
            while (y < bottom) { line(left, y, right, y, LIGHT); y += spacing }
        }
        when (background) {
            PageBackground.BLANK -> Unit
            PageBackground.LINED -> {
                ruled(RULED_SPACING * 3, h - RULED_SPACING, MARGIN, w - MARGIN, RULED_SPACING)
                line(MARGIN * 2.4f, RULED_SPACING * 2, MARGIN * 2.4f, h - RULED_SPACING, ACCENT)
            }
            PageBackground.NARROW -> {
                ruled(NARROW_SPACING * 3, h - NARROW_SPACING, MARGIN, w - MARGIN, NARROW_SPACING)
                line(MARGIN * 2.4f, NARROW_SPACING * 2, MARGIN * 2.4f, h - NARROW_SPACING, ACCENT)
            }
            PageBackground.GRID -> grid(::line, 5 * MM, LIGHT)
            PageBackground.GRAPH -> {
                grid(::line, MM, FAINT, 0.6f)
                grid(::line, 5 * MM, LIGHT, 1f)
                grid(::line, 10 * MM, MEDIUM, 1.6f)
            }
            PageBackground.DOTTED -> {
                paint.color = MEDIUM; paint.strokeWidth = (3f * scale).coerceAtLeast(2f); paint.strokeCap = Paint.Cap.ROUND
                val step = 5 * MM
                var y = step
                while (y < h) { var x = step; while (x < w) { canvas.drawPoint(x * scale, y * scale, paint); x += step }; y += step }
            }
            PageBackground.CORNELL -> {
                val header = h * 0.1f
                val summary = h * 0.8f
                val cue = w * 0.3f
                line(0f, header, w, header, MEDIUM, 1.8f)
                line(cue, header, cue, summary, MEDIUM, 1.8f)
                line(0f, summary, w, summary, MEDIUM, 1.8f)
                ruled(header + RULED_SPACING, summary - 4f, cue + 12f, w - MARGIN, RULED_SPACING)
                ruled(summary + RULED_SPACING, h - RULED_SPACING / 2, MARGIN, w - MARGIN, RULED_SPACING)
            }
            PageBackground.MUSIC -> {
                val lineGap = 2.2f * MM
                val staffGap = 16 * MM
                var top = 22 * MM
                while (top + 4 * lineGap < h - 12 * MM) {
                    repeat(5) { i -> line(MARGIN, top + i * lineGap, w - MARGIN, top + i * lineGap, MEDIUM, 1f) }
                    top += 4 * lineGap + staffGap
                }
            }
            PageBackground.ISOMETRIC -> isometric(::line, 6 * MM)
        }
    }

    private inline fun grid(line: (Float, Float, Float, Float, Int, Float) -> Unit, step: Float, color: Int, width: Float = 1f) {
        var x = step
        while (x < PAGE_WIDTH) { line(x, 0f, x, PAGE_HEIGHT, color, width); x += step }
        var y = step
        while (y < PAGE_HEIGHT) { line(0f, y, PAGE_WIDTH, y, color, width); y += step }
    }

    /** Vertical lines plus lines at ±30°, forming equilateral triangles of side [side]. */
    private fun isometric(line: (Float, Float, Float, Float, Int, Float) -> Unit, side: Float) {
        val columnWidth = side * sqrt(3f) / 2
        var x = 0f
        while (x <= PAGE_WIDTH) { line(x, 0f, x, PAGE_HEIGHT, FAINT, 1f); x += columnWidth }
        val slope = 1f / sqrt(3f) // tan 30°
        val rise = PAGE_WIDTH * slope
        var c = -rise
        while (c < PAGE_HEIGHT + rise) {
            line(0f, c, PAGE_WIDTH, c + rise, LIGHT, 1f)
            line(0f, c + rise, PAGE_WIDTH, c, LIGHT, 1f)
            c += side
        }
    }
}
