package com.bookflow.app.presentation.screens.notes.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.notes.ink.InkBrushes
import com.bookflow.app.presentation.screens.notes.editor.EditorPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes notes to the shared exports folder and builds share intents for them. */
object NoteExporter {
    private const val PDF_PAGE_WIDTH = 595 // A4 in PostScript points

    suspend fun exportPdf(context: Context, title: String, pages: List<EditorPage>): Uri = withContext(Dispatchers.IO) {
        val scale = PDF_PAGE_WIDTH / PAGE_WIDTH
        val height = (PAGE_HEIGHT * scale).toInt()
        val renderer = CanvasStrokeRenderer.create(InkBrushes.textures)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, height, index + 1).create())
                NotePageRenderer.draw(context, pdfPage.canvas, page, scale, renderer)
                document.finishPage(pdfPage)
            }
            val file = exportFile(context, title, "pdf")
            file.outputStream().use { document.writeTo(it) }
            uriFor(context, file)
        } finally { document.close() }
    }

    suspend fun exportPng(context: Context, title: String, page: EditorPage, pageNumber: Int): Uri = withContext(Dispatchers.IO) {
        val bitmap = NotePageRenderer.renderBitmap(context, page, widthPx = 2000)
        try {
            val file = exportFile(context, "$title p$pageNumber", "png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uriFor(context, file)
        } finally { bitmap.recycle() }
    }

    fun shareIntent(uri: Uri, mimeType: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        null
    )

    private fun exportFile(context: Context, title: String, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = title.ifBlank { "Note" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifBlank { "Note" }
        return File(dir, "$safe.$extension")
    }

    private fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
