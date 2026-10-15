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
        page.elements.sortedBy { it.z }.forEach { drawElement(context, canvas, it, scale) }
        drawStrokes(canvas, page, scale, renderer)
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
