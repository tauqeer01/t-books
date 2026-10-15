package com.bookflow.app.esign.data

import android.content.Context
import android.graphics.BitmapFactory
import com.bookflow.app.esign.domain.FieldKind
import com.bookflow.app.esign.domain.PdfPlacement
import com.bookflow.app.esign.domain.SignField
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Writes a signed copy of a PDF: signatures, text, dates, and marks are drawn into each page's content
 * ("flattened"), so they look the same in every viewer and cannot be moved or removed like annotations.
 * The source document is only read.
 */
class PdfSigner(private val context: Context) {

    /** [title] becomes the copy's document title, so the library lists it as the signed version. */
    suspend fun sign(source: () -> InputStream, fields: List<SignField>, output: File, title: String? = null) = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val scratch = File(context.cacheDir, "pdfbox").apply { mkdirs() }
        // Temp-file buffering keeps large scanned documents out of the heap (see PdfTextIndex)
        source().use { input ->
            PDDocument.load(input, MemoryUsageSetting.setupMixed(4L * 1024 * 1024).setTempDir(scratch)).use { doc ->
                fields.groupBy { it.page }.forEach { (pageIndex, pageFields) ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@forEach
                    val page = doc.getPage(pageIndex)
                    val crop = page.cropBox.let { PdfPlacement.Box(it.lowerLeftX, it.lowerLeftY, it.width, it.height) }
                    val (displayW, displayH) = PdfPlacement.displaySize(crop, page.rotation)
                    val toUser = PdfPlacement.displayToUser(crop, page.rotation)
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                        // Work in display points (origin top-left, y down) so fields land where they were placed
                        content.transform(Matrix(toUser.a, toUser.b, toUser.c, toUser.d, toUser.e, toUser.f))
                        pageFields.forEach { field ->
                            val x = field.x * displayW
                            val y = field.y * displayH
                            val w = field.width * displayW
                            val h = field.height * displayH
                            when {
                                field.kind.isImage -> drawImage(doc, content, field, x, y, w, h)
                                field.kind.isText -> drawText(content, field, x, y, h)
                                field.kind == FieldKind.CHECK -> drawCheck(content, field, x, y, w, h)
                                field.kind == FieldKind.CROSS -> drawCross(content, field, x, y, w, h)
                            }
                        }
                    }
                }
                title?.let { doc.documentInformation.title = it }
                output.parentFile?.mkdirs()
                val temp = File(output.parentFile, output.name + ".tmp")
                doc.save(temp)
                if (!temp.renameTo(output)) { output.delete(); temp.renameTo(output) }
            }
        }
        output
    }

    private fun drawImage(doc: PDDocument, content: PDPageContentStream, field: SignField, x: Float, y: Float, w: Float, h: Float) {
        val path = field.imagePath ?: return
        val bitmap = BitmapFactory.decodeFile(path) ?: return
        val image = LosslessFactory.createFromImage(doc, bitmap) // keeps transparency
        bitmap.recycle()
        content.saveGraphicsState()
        // The y-down display space would mirror the image; flip it back inside the field's box
        content.transform(Matrix(w, 0f, 0f, -h, x, y + h))
        content.drawImage(image, 0f, 0f, 1f, 1f)
        content.restoreGraphicsState()
    }

    private fun drawText(content: PDPageContentStream, field: SignField, x: Float, y: Float, h: Float) {
        val text = field.text.replace(Regex("[^\\x20-\\x7E\\u00A0-\\u00FF]"), "?") // standard fonts cover Latin-1
        if (text.isBlank()) return
        val size = h * 0.8f
        content.setNonStrokingColor(r(field), g(field), b(field))
        content.beginText()
        content.setFont(PDType1Font.HELVETICA, size)
        // Baseline near the bottom of the field; d = -1 undoes the y-down flip for glyphs.
        // The font size already scales the glyphs, so the text matrix must not scale them again.
        content.setTextMatrix(Matrix(1f, 0f, 0f, -1f, x, y + h * 0.82f))
        content.showText(text)
        content.endText()
    }

    private fun drawCheck(content: PDPageContentStream, field: SignField, x: Float, y: Float, w: Float, h: Float) {
        stroke(content, field, minOf(w, h) * 0.12f) {
            content.moveTo(x + w * 0.12f, y + h * 0.55f)
            content.lineTo(x + w * 0.4f, y + h * 0.85f)
            content.lineTo(x + w * 0.9f, y + h * 0.15f)
        }
    }

    private fun drawCross(content: PDPageContentStream, field: SignField, x: Float, y: Float, w: Float, h: Float) {
        stroke(content, field, minOf(w, h) * 0.12f) {
            content.moveTo(x + w * 0.15f, y + h * 0.15f); content.lineTo(x + w * 0.85f, y + h * 0.85f)
            content.moveTo(x + w * 0.85f, y + h * 0.15f); content.lineTo(x + w * 0.15f, y + h * 0.85f)
        }
    }

    private inline fun stroke(content: PDPageContentStream, field: SignField, width: Float, path: () -> Unit) {
        content.saveGraphicsState()
        content.setStrokingColor(r(field), g(field), b(field))
        content.setLineWidth(width)
        content.setLineCapStyle(1)
        content.setLineJoinStyle(1)
        path()
        content.stroke()
        content.restoreGraphicsState()
    }

    private fun r(f: SignField) = (f.colorArgb shr 16 and 0xFF) / 255f
    private fun g(f: SignField) = (f.colorArgb shr 8 and 0xFF) / 255f
    private fun b(f: SignField) = (f.colorArgb and 0xFF) / 255f
}
