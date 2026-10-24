package com.bookflow.app.presentation.screens.notes.editor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.notes.ink.PenPreset
import com.bookflow.app.notes.ink.PenType
import kotlin.math.ln

private val Barrel = Color(0xFFF4F4F5)
private val BarrelEdge = Color(0x33000000)
private val Metal = Color(0xFFB8BCC4)
private val Wood = Color(0xFFE9C99B)

/**
 * A pen standing upright, tip at the top, drawn for its type: ballpoint cone, fountain nib, sharpened pencil,
 * bullet marker or chisel highlighter. The tip and a band on the barrel show the ink color; the tip width
 * follows the thickness. The barrel runs off the bottom edge, so callers can sink unselected pens into a tray.
 */
@Composable
fun PenGlyph(preset: PenPreset, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPen(preset) }
}

private fun DrawScope.drawPen(preset: PenPreset) {
    val w = size.width
    val h = size.height
    val ink = Color(preset.colorArgb).copy(alpha = if (preset.type.translucent) 0.75f else 1f)
    val wide = preset.type == PenType.MARKER || preset.type == PenType.HIGHLIGHTER
    val barrelHalf = w * if (wide) 0.34f else 0.26f
    val cx = w / 2
    val shoulder = h * 0.42f
    // Thickness on a log scale, like the slider: 0 = finest, 1 = boldest
    val range = InkBrushes.sizeRange(preset.type)
    val t = ((ln(preset.size) - ln(range.start)) / (ln(range.endInclusive) - ln(range.start))).coerceIn(0f, 1f)

    // Barrel with an ink-colored band
    drawRoundRect(Barrel, Offset(cx - barrelHalf, shoulder), Size(barrelHalf * 2, h - shoulder + 8f), CornerRadius(3f, 3f))
    drawRoundRect(BarrelEdge, Offset(cx - barrelHalf, shoulder), Size(barrelHalf * 2, h - shoulder + 8f), CornerRadius(3f, 3f), style = Stroke(1f))
    val bandTop = shoulder + (h - shoulder) * 0.28f
    val band = Size(barrelHalf * 2, (h - shoulder) * 0.14f)
    if (preset.type == PenType.DASHED) {
        drawLine(ink, Offset(cx - barrelHalf, bandTop + band.height / 2), Offset(cx + barrelHalf, bandTop + band.height / 2),
            strokeWidth = band.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(barrelHalf * 0.4f, barrelHalf * 0.25f)))
    } else drawRect(ink, Offset(cx - barrelHalf, bandTop), band)

    val tipTop = h * 0.08f
    when (preset.type) {
        PenType.BALLPOINT, PenType.DASHED -> {
            val pointHalf = w * (0.035f + 0.05f * t)
            drawPath(trapezoid(cx, shoulder, barrelHalf, tipTop + h * 0.04f, pointHalf), Metal)
            drawCircle(ink, radius = pointHalf, center = Offset(cx, tipTop + h * 0.04f + pointHalf * 0.4f))
        }
        PenType.FOUNTAIN -> {
            // Grip, then a pointed nib with a slit
            val gripTop = shoulder - h * 0.1f
            drawPath(trapezoid(cx, shoulder, barrelHalf, gripTop, barrelHalf * 0.8f), Color(0xFF374151))
            val nibHalf = barrelHalf * 0.8f
            val point = w * (0.02f + 0.05f * t)
            val nib = Path().apply {
                moveTo(cx - nibHalf, gripTop)
                quadraticTo(cx - nibHalf, tipTop + h * 0.12f, cx - point, tipTop)
                lineTo(cx + point, tipTop)
                quadraticTo(cx + nibHalf, tipTop + h * 0.12f, cx + nibHalf, gripTop)
                close()
            }
            drawPath(nib, lerp(ink, Color.White, 0.15f))
            drawLine(Color.White.copy(alpha = 0.8f), Offset(cx, tipTop + h * 0.03f), Offset(cx, gripTop - h * 0.06f), strokeWidth = 1.2f)
            drawCircle(Color.White.copy(alpha = 0.8f), radius = 1.6f, center = Offset(cx, gripTop - h * 0.06f))
        }
        PenType.PENCIL -> {
            // Sharpened wood with a graphite point in the pencil's color
            val pointHalf = w * (0.03f + 0.05f * t)
            drawPath(trapezoid(cx, shoulder, barrelHalf, tipTop, pointHalf), Wood)
            val leadBase = tipTop + (shoulder - tipTop) * 0.38f
            val leadHalf = pointHalf + (barrelHalf - pointHalf) * 0.38f
            drawPath(trapezoid(cx, leadBase, leadHalf, tipTop, pointHalf), ink)
        }
        PenType.MARKER -> {
            val tipHalf = barrelHalf * (0.35f + 0.45f * t)
            drawPath(trapezoid(cx, shoulder, barrelHalf, shoulder - h * 0.12f, barrelHalf * 0.7f), Metal)
            drawRoundRect(ink, Offset(cx - tipHalf, tipTop + h * 0.04f), Size(tipHalf * 2, shoulder - h * 0.12f - tipTop - h * 0.04f + 2f),
                CornerRadius(tipHalf, tipHalf))
        }
        PenType.HIGHLIGHTER -> {
            // Chisel tip: a slanted flat edge
            val tipHalf = barrelHalf * (0.55f + 0.4f * t)
            val base = shoulder - h * 0.06f
            drawPath(trapezoid(cx, shoulder, barrelHalf, base, barrelHalf * 0.85f), Metal)
            val chisel = Path().apply {
                moveTo(cx - tipHalf, base)
                lineTo(cx - tipHalf, tipTop + h * 0.12f)
                lineTo(cx + tipHalf, tipTop)
                lineTo(cx + tipHalf, base)
                close()
            }
            drawPath(chisel, ink)
        }
    }
}

/** A tapered section from [bottomHalf] wide at [bottom] to [topHalf] wide at [top], centered on [cx]. */
private fun trapezoid(cx: Float, bottom: Float, bottomHalf: Float, top: Float, topHalf: Float) = Path().apply {
    moveTo(cx - bottomHalf, bottom)
    lineTo(cx - topHalf, top)
    lineTo(cx + topHalf, top)
    lineTo(cx + bottomHalf, bottom)
    close()
}
