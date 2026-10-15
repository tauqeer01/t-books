package com.bookflow.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.esign.data.PdfSigner
import com.bookflow.app.esign.data.SignatureStore
import com.bookflow.app.esign.domain.FieldKind
import com.bookflow.app.esign.domain.SignField
import com.bookflow.app.esign.domain.SignatureKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ESignTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun blankPdf(rotation: Int): File {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "sign_src_$rotation.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(400f, 600f)).apply { this.rotation = rotation })
            doc.addPage(PDPage(PDRectangle(400f, 600f)))
            doc.save(file)
        }
        return file
    }

    /** Renders [page] as displayed (PdfRenderer applies /Rotate) at 1000 px wide. */
    private fun render(file: File, page: Int): Bitmap =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            renderer.openPage(page).use { p ->
                Bitmap.createBitmap(1000, (1000f * p.height / p.width).toInt(), Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                    p.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }

    private fun Bitmap.at(fx: Float, fy: Float) = getPixel((fx * width).toInt(), (fy * height).toInt())

    private fun signsWhereFieldsWerePlaced(rotation: Int) = runBlocking {
        val source = blankPdf(rotation)
        val red = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val image = File(context.cacheDir, "red.png").apply { outputStream().use { red.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val fields = listOf(
            SignField("sig", 0, FieldKind.SIGNATURE, 0.1f, 0.1f, 0.3f, 0.2f, imagePath = image.path),
            SignField("chk", 0, FieldKind.CHECK, 0.7f, 0.7f, 0.15f, 0.15f, colorArgb = Color.BLUE),
            SignField("txt", 1, FieldKind.TEXT, 0.1f, 0.5f, 0.6f, 0.05f, text = "Signed by Test")
        )
        val output = File(context.cacheDir, "signed_$rotation.pdf")
        PdfSigner(context).sign({ source.inputStream() }, fields, output)

        val page = render(output, 0)
        assertEquals("signature fills its box ($rotation°)", Color.RED, page.at(0.25f, 0.2f))
        assertEquals("outside the signature stays white ($rotation°)", Color.WHITE, page.at(0.5f, 0.2f))
        assertEquals("nothing above the signature ($rotation°)", Color.WHITE, page.at(0.25f, 0.05f))
        val checkArea = (0 until 20).flatMap { i -> (0 until 20).map { j -> page.at(0.7f + i * 0.0075f, 0.7f + j * 0.0075f) } }
        assertTrue("check mark drawn inside its box ($rotation°)", checkArea.any { Color.blue(it) > 200 && Color.red(it) < 80 })
        val page2 = render(output, 1)
        val textArea = (0 until 60).map { i -> page2.at(0.1f + i * 0.005f, 0.53f) }
        assertTrue("text drawn on page 2", textArea.any { Color.red(it) < 100 })
        // Glyphs stay inside the field's box: nothing dark above, below, or past its right edge
        val outside = (0 until 100).flatMap { i -> listOf(page2.at(i * 0.01f, 0.45f), page2.at(i * 0.01f, 0.6f)) } +
            (0 until 10).map { j -> page2.at(0.75f, 0.5f + j * 0.005f) }
        assertTrue("text stays within its field ($rotation°)", outside.all { Color.red(it) > 200 })
        // Original untouched
        assertEquals(Color.WHITE, render(source, 0).at(0.25f, 0.2f))
    }

    @Test fun signsUnrotatedPage() = signsWhereFieldsWerePlaced(0)
    @Test fun signsRotatedPage() = signsWhereFieldsWerePlaced(90)
    @Test fun signsUpsideDownPage() = signsWhereFieldsWerePlaced(180)

    @Test fun savedSignatureIsCroppedTransparentInkAndReplacesTheOldOne() {
        val store = SignatureStore(context)
        store.list().forEach(store::delete)
        val drawn = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888).apply {
            for (x in 100 until 300) for (y in 120 until 140) setPixel(x, y, Color.BLACK)
        }
        val first = store.save(SignatureKind.SIGNATURE, drawn)
        val saved = android.graphics.BitmapFactory.decodeFile(first.path)
        assertTrue("cropped to the ink", saved.width in 200..220 && saved.height in 20..40)
        assertEquals("background stays transparent", 0, Color.alpha(saved.getPixel(1, 1)))
        store.save(SignatureKind.SIGNATURE, drawn)
        store.save(SignatureKind.INITIALS, drawn)
        assertEquals("one of each kind", setOf(SignatureKind.INITIALS, SignatureKind.SIGNATURE), store.list().map { it.kind }.toSet())
        assertEquals(2, store.list().size)
        assertThrows(java.io.IOException::class.java) { store.save(SignatureKind.SIGNATURE, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)) }

        // Photo of paper: white becomes transparent, ink keeps the chosen color
        val photo = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE); setPixel(5, 5, Color.BLACK) }
        val ink = SignatureStore.inkOnly(photo, Color.BLUE)
        assertEquals(0, Color.alpha(ink.getPixel(0, 0)))
        assertEquals(255, Color.alpha(ink.getPixel(5, 5)))
        assertEquals(255, Color.blue(ink.getPixel(5, 5)))
        store.list().forEach(store::delete)
    }
}
