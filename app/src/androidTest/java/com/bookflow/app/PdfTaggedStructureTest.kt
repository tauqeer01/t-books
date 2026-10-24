package com.bookflow.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.pdf.engine.PdfSource
import com.bookflow.app.pdf.engine.PdfTextIndex
import com.bookflow.app.pdf.engine.TaglessPdfParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNull
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.SourceBackedStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccessBufferedFileInputStream
import com.tom_roush.pdfbox.io.ScratchFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Large illustrated handbooks (the FAA AMT handbook: 677 pages, 22,564 streams, a tagged structure tree with 644
 * null-padded arrays) crashed the reader with OutOfMemoryError while PDFBox loaded the text layer. PDFBox 2 parses
 * every object up front and copies every stream into scratch buffers that keep 4 KB each on the heap. The text
 * parser must skip the structure tree and read streams from the file on demand, and text features must still work.
 */
@RunWith(AndroidJUnit4::class)
class PdfTaggedStructureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File

    @Before fun writeTaggedPdf() {
        PDFBoxResourceLoader.init(context)
        file = File(context.cacheDir, "tagged_structure_fixture.pdf")
        PDDocument().use { doc ->
            val page = PDPage().also(doc::addPage)
            // A figure (Form XObject) with its own label, as in illustrated handbooks
            val figure = PDFormXObject(doc).apply { bBox = PDRectangle(0f, 0f, 300f, 100f); resources = com.tom_roush.pdfbox.pdmodel.PDResources() }
            PDPageContentStream(doc, figure, figure.contentStream.createOutputStream()).use { content ->
                content.beginText(); content.setFont(PDType1Font.HELVETICA, 12f); content.newLineAtOffset(10f, 40f)
                content.showText("Turbine label"); content.endText()
            }
            val photo = LosslessFactory.createFromImage(doc, Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) })
            PDPageContentStream(doc, page).use { content ->
                content.beginText(); content.setFont(PDType1Font.HELVETICA, 18f); content.newLineAtOffset(72f, 700f)
                content.showText("Cobalt tagged page"); content.endText()
                content.drawImage(photo, 72f, 500f, 64f, 64f)
                content.saveGraphicsState(); content.transform(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(72f, 300f))
                content.drawForm(figure); content.restoreGraphicsState()
            }
            // A parent tree padded with nulls, as in Adobe-exported tagged handbooks
            val padded = COSArray().apply { repeat(NULLS) { add(COSNull.NULL) } }
            val parentTree = COSDictionary().apply { setItem(COSName.NUMS, COSArray().apply { add(COSInteger.ZERO); add(padded) }) }
            val structTree = COSDictionary().apply { setItem(COSName.TYPE, COSName.STRUCT_TREE_ROOT); setItem(COSName.PARENT_TREE, parentTree) }
            doc.documentCatalog.cosObject.setItem(COSName.STRUCT_TREE_ROOT, structTree)
            doc.save(file)
        }
    }

    @After fun cleanUp() { file.delete() }

    private fun parse(tagless: Boolean): PDDocument {
        val source = RandomAccessBufferedFileInputStream(file)
        val scratch = ScratchFile(MemoryUsageSetting.setupMainMemoryOnly())
        val parser = if (tagless) TaglessPdfParser(source, scratch) else com.tom_roush.pdfbox.pdfparser.PDFParser(source, scratch)
        parser.parse()
        return parser.pdDocument
    }

    @Test fun fixtureReallyHasTheNullPaddedTree() = parse(tagless = false).use { doc ->
        val tree = doc.documentCatalog.cosObject.getDictionaryObject(COSName.STRUCT_TREE_ROOT) as COSDictionary
        val nums = (tree.getDictionaryObject(COSName.PARENT_TREE) as COSDictionary).getDictionaryObject(COSName.NUMS) as COSArray
        assertEquals(NULLS, (nums.getObject(1) as COSArray).size())
    }

    @Test fun structureTreeIsNotLoaded() = parse(tagless = true).use { doc ->
        assertNull("the structure tree stays unparsed", doc.documentCatalog.cosObject.getDictionaryObject(COSName.STRUCT_TREE_ROOT))
        assertEquals(1, doc.numberOfPages)
    }

    @Test fun streamsAreReadFromTheFileNotCopied() = parse(tagless = true).use { doc ->
        val streams = doc.document.objects.mapNotNull { it.`object` as? COSStream }
        assertTrue("fixture has page content, a form and an image", streams.size >= 3)
        streams.forEach { assertTrue("stream ${it.keySet()} is read from the file", it is SourceBackedStream && it.isSourceBacked) }
    }

    @Test fun textStillExtractsAfterGarbageCollection() = parse(tagless = true).use { doc ->
        // The parser's first document shares the scratch file and closes it when finalized
        repeat(3) { Runtime.getRuntime().gc(); System.runFinalization() }
        assertTrue(com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc).contains("Cobalt"))
    }

    @Test fun textSearchStillWorksOnATaggedPdf(): Unit = runBlocking {
        val index = PdfTextIndex(context)
        index.open(PdfSource.FileSource(file))
        try {
            assertTrue("found the page text", index.search("Cobalt").isNotEmpty())
            assertTrue("found the text inside the figure", index.search("Turbine").isNotEmpty())
        } finally { index.close() }
    }

    private companion object { const val NULLS = 200_000 }
}
