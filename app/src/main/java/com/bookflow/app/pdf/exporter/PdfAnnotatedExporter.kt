package com.bookflow.app.pdf.exporter

import com.bookflow.app.pdf.engine.isStickyNote
import com.bookflow.app.pdf.engine.markupRects
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.bookflow.app.domain.model.AnnotationType
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.model.BookAnnotation
import com.bookflow.app.pdf.drawing.StrokeSerializer
import com.bookflow.app.pdf.engine.PdfEngine
import com.bookflow.app.pdf.engine.RenderQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfAnnotatedExporter {

    suspend fun exportAnnotatedPdf(
        context: Context,
        pdfEngine: PdfEngine,
        book: Book,
        annotations: List<BookAnnotation>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val cleanTitle = book.title.replace(Regex("[^a-zA-Z0-9_]"), "_").take(40)
        val destFile = File(exportDir, "${cleanTitle}_Annotated_${System.currentTimeMillis()}.pdf")

        val pdfDocument = PdfDocument()
        val totalPages = book.pageCount.coerceAtLeast(1)

        try {
            for (pageIdx in 0 until totalPages) {
                onProgress(pageIdx + 1, totalPages)

                // 1. Render high-resolution page bitmap
                val pageBitmap = pdfEngine.renderPage(pageIdx, scale = 1.5f, quality = RenderQuality.HIGH_DPI)
                    ?: Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(AndroidColor.WHITE)
                    }

                val pageWidth = pageBitmap.width
                val pageHeight = pageBitmap.height

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // 2. Draw base page bitmap
                canvas.drawBitmap(pageBitmap, 0f, 0f, null)

                // 3. Draw annotations for this page
                val pageAnnotations = annotations.filter { it.pageIndex == pageIdx }

                // A. Highlights, Underlines, Strikethroughs
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                for (ann in pageAnnotations) {
                    val colorInt = try {
                        AndroidColor.parseColor(ann.colorHex)
                    } catch (_: Exception) {
                        AndroidColor.YELLOW
                    }
                    // One rect per text line for markups; the union rect for older single-rect annotations
                    val lineRects = ann.markupRects().map {
                        RectF(it.left * pageWidth, it.top * pageHeight, it.right * pageWidth, it.bottom * pageHeight)
                    }

                    when (ann.type) {
                        AnnotationType.HIGHLIGHT -> {
                            textPaint.style = Paint.Style.FILL
                            textPaint.color = colorInt
                            textPaint.alpha = 110 // Translucent highlight
                            lineRects.forEach { canvas.drawRoundRect(it, 4f, 4f, textPaint) }
                        }
                        AnnotationType.UNDERLINE -> {
                            textPaint.style = Paint.Style.STROKE
                            textPaint.strokeWidth = 3f * (pageWidth / 595f)
                            textPaint.color = colorInt
                            textPaint.alpha = 240
                            lineRects.forEach { canvas.drawLine(it.left, it.bottom, it.right, it.bottom, textPaint) }
                        }
                        AnnotationType.STRIKETHROUGH -> {
                            textPaint.style = Paint.Style.STROKE
                            textPaint.strokeWidth = 2.5f * (pageWidth / 595f)
                            textPaint.color = colorInt
                            textPaint.alpha = 240
                            lineRects.forEach {
                                val midY = (it.top + it.bottom) / 2f
                                canvas.drawLine(it.left, midY, it.right, midY, textPaint)
                            }
                        }
                        AnnotationType.NOTE -> {
                            val rect = RectF(ann.rectLeft * pageWidth, ann.rectTop * pageHeight, ann.rectRight * pageWidth, ann.rectBottom * pageHeight)
                            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                            if (ann.isStickyNote) {
                                // Sticky note square in the note's color
                                val half = 9f * (pageWidth / 595f)
                                badgePaint.color = colorInt
                                canvas.drawRoundRect(rect.centerX() - half, rect.centerY() - half, rect.centerX() + half, rect.centerY() + half, 3f, 3f, badgePaint)
                            } else {
                                // Note icon badge indicator
                                badgePaint.color = AndroidColor.parseColor("#4F46E5")
                                canvas.drawCircle(rect.left + 12f, rect.top + 12f, 10f, badgePaint)
                            }
                        }
                        AnnotationType.PEN_DRAW -> {
                            // Handled in drawing strokes pass below
                        }
                    }
                }

                // B. Freehand Pen & Highlighter Drawings
                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                val penAnnotations = pageAnnotations.filter { it.type == AnnotationType.PEN_DRAW }
                for (ann in penAnnotations) {
                    val strokeData = ann.strokePathData ?: continue
                    val stroke = StrokeSerializer.deserialize(ann.id, pageIdx, strokeData, ann.colorHex) ?: continue
                    val path = stroke.toAndroidPath(pageWidth.toFloat(), pageHeight.toFloat())

                    val strokeColorInt = try {
                        AndroidColor.parseColor(stroke.colorHex)
                    } catch (_: Exception) {
                        AndroidColor.parseColor("#4F46E5")
                    }

                    drawPaint.color = strokeColorInt
                    val scaleFactor = (pageWidth / 400f).coerceIn(0.8f, 2.5f)

                    if (stroke.isHighlighter) {
                        drawPaint.strokeWidth = (stroke.strokeWidth * 2.2f * scaleFactor)
                        drawPaint.alpha = 100 // 40% opacity for highlighter
                    } else {
                        drawPaint.strokeWidth = (stroke.strokeWidth * scaleFactor)
                        drawPaint.alpha = 255
                    }

                    canvas.drawPath(path, drawPaint)
                }

                pdfDocument.finishPage(page)
            }

            FileOutputStream(destFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        destFile
    }

    fun createShareIntent(context: Context, pdfFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Annotated PDF: ${pdfFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
