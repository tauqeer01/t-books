package com.bookflow.app.presentation.screens.notes.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.bookflow.app.notes.domain.ElementKind
import com.bookflow.app.notes.domain.NoteElement
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.notes.domain.PageBackground
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.presentation.screens.notes.editor.EditorPage
import kotlin.math.roundToInt

/**
 * Draws a note page on an Android [Canvas] at any scale: background, elements, then ink. Used for the cover
 * thumbnail, PNG export, and PDF export, so all three match the editor.
 */
object NotePageRenderer {
    const val LINE_SPACING = PageTemplateRenderer.RULED_SPACING
    const val DEFAULT_TEXT_SIZE = 28f

    fun renderBitmap(context: Context, page: EditorPage, widthPx: Int): Bitmap {
        val scale = widthPx / PAGE_WIDTH
        val bitmap = Bitmap.createBitmap(widthPx, (PAGE_HEIGHT * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        draw(context, Canvas(bitmap), page, scale, CanvasStrokeRenderer.create(InkBrushes.textures))
        return bitmap
    }

    fun draw(context: Context, canvas: Canvas, page: EditorPage, scale: Float, renderer: CanvasStrokeRenderer) {
        canvas.drawColor(Color.WHITE)
        drawBackground(canvas, page.background, scale)
        page.elements.filter { it.kind != ElementKind.TAPE }.sortedBy { it.z }.forEach { drawElement(context, canvas, it, scale) }
        drawStrokes(canvas, page, scale, renderer)
        drawTapes(canvas, page, scale)
    }

    /**
     * Study tapes, above ink and text. Covered tapes are opaque with a faint stripe; [revealed] ones (editor
     * only) shrink to a tinted outline so the content shows while the tape stays visible.
     */
    fun drawTapes(canvas: Canvas, page: EditorPage, scale: Float, revealed: Set<String> = emptySet()) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF; strokeWidth = 6f * scale }
        page.elements.filter { it.kind == ElementKind.TAPE }.sortedBy { it.z }.forEach { tape ->
            val rect = RectF(tape.x * PAGE_WIDTH * scale, tape.y * PAGE_HEIGHT * scale, tape.right * PAGE_WIDTH * scale, tape.bottom * PAGE_HEIGHT * scale)
            val color = tape.fillColor ?: 0xFFF59E0B.toInt()
            val radius = 4f * scale
            if (tape.id in revealed) {
                fill.style = Paint.Style.FILL; fill.color = (color and 0x00FFFFFF) or 0x1F000000
                canvas.drawRoundRect(rect, radius, radius, fill)
                fill.style = Paint.Style.STROKE; fill.strokeWidth = 2f * scale; fill.color = (color and 0x00FFFFFF) or 0x99000000.toInt()
                canvas.drawRoundRect(rect, radius, radius, fill)
                return@forEach
            }
            fill.style = Paint.Style.FILL; fill.color = color or 0xFF000000.toInt()
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.save()
            canvas.clipRect(rect)
            var x = rect.left - rect.height()
            while (x < rect.right) { canvas.drawLine(x, rect.bottom, x + rect.height(), rect.top, stripe); x += 22f * scale }
            canvas.restore()
        }
    }

    /** The renderer's matrix only describes the transform for level of detail; the canvas must apply it too. */
    fun drawStrokes(canvas: Canvas, page: EditorPage, scale: Float, renderer: CanvasStrokeRenderer) {
        val matrix = Matrix().apply { setScale(scale, scale) }
        canvas.save()
        canvas.concat(matrix)
        page.strokes.forEach { renderer.draw(canvas, it.stroke, matrix) }
        canvas.restore()
    }

    fun drawBackground(canvas: Canvas, background: PageBackground, scale: Float) = PageTemplateRenderer.draw(canvas, background, scale)

    private fun drawElement(context: Context, canvas: Canvas, element: NoteElement, scale: Float) {
        val rect = RectF(element.x * PAGE_WIDTH * scale, element.y * PAGE_HEIGHT * scale, element.right * PAGE_WIDTH * scale, element.bottom * PAGE_HEIGHT * scale)
        when (element.kind) {
            ElementKind.TEXT -> {
                element.fillColor?.let { fill ->
                    canvas.drawRoundRect(rect, 6f * scale, 6f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
                    rect.inset(14f * scale, 14f * scale)
                }
                drawText(canvas, element.html.orEmpty(), rect, scale)
            }
            ElementKind.TAPE -> Unit // drawn above the ink by drawTapes
            ElementKind.IMAGE, ElementKind.PDF_CLIP -> {
                val path = element.imagePath
                if (path != null) drawImage(canvas, path, rect)
                else drawText(canvas, element.html.orEmpty(), rect, scale) // text quote clip
            }
        }
    }

    private fun drawText(canvas: Canvas, html: String, rect: RectF, scale: Float) {
        // Normalized first: notes saved before entity normalization may still hold &comma;-style entities
        val text = Html.fromHtml(com.bookflow.app.notes.domain.HtmlText.normalize(html), Html.FROM_HTML_MODE_COMPACT)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt(); textSize = DEFAULT_TEXT_SIZE * scale }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, rect.width().roundToInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setLineSpacing(LINE_SPACING * scale - paint.fontSpacing, 1f).build()
        canvas.save()
        canvas.translate(rect.left, rect.top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawImage(canvas: Canvas, path: String, rect: RectF) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= rect.width() && bounds.outHeight / (sample * 2) >= rect.height()) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
        canvas.drawBitmap(bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
    }
}
