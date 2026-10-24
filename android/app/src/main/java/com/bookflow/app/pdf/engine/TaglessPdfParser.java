package com.bookflow.app.pdf.engine;

import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSDocument;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSStream;
import com.tom_roush.pdfbox.cos.SourceBackedStream;
import com.tom_roush.pdfbox.io.RandomAccessRead;
import com.tom_roush.pdfbox.io.ScratchFile;
import com.tom_roush.pdfbox.pdfparser.PDFParser;

import java.io.EOFException;
import java.io.IOException;
import java.util.Map;

/**
 * A PDFBox parser for the text layer that stays small on large, illustrated, tagged PDFs.
 *
 * PDFBox 2 parses every object reachable from the catalog up front and copies every stream into scratch buffers.
 * The FAA AMT handbook (677 pages, 22,564 streams, a structure tree with 644 null-padded arrays) needed ~180 MB of
 * heap that way and crashed the reader with OutOfMemoryError. This parser:
 * - skips the structure tree (for screen readers), which text extraction, search, the outline and links never use;
 * - reads stream bytes from the file on demand instead of copying them (see {@link SourceBackedStream}).
 *
 * Java rather than Kotlin: PDFBox passes a null array for "no exclusions", which a Kotlin vararg override rejects.
 */
public final class TaglessPdfParser extends PDFParser implements SourceBackedStream.Source {
    private final RandomAccessRead file;
    private boolean copyingStream;

    public TaglessPdfParser(RandomAccessRead source, ScratchFile scratch) throws IOException {
        super(source, scratch);
        this.file = source;
        // Nothing has been parsed yet, so the document can still be swapped for one that makes file-backed streams
        document = new FileBackedDocument(scratch, this, document);
    }

    private static final class FileBackedDocument extends COSDocument {
        private final ScratchFile scratch;
        private final SourceBackedStream.Source streams;
        /**
         * The document PDFParser created first shares our scratch file, and COSDocument.finalize() closes it. Holding
         * it here keeps it from being finalized (closing the scratch file under us) while this document is in use.
         */
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final COSDocument replaced;

        FileBackedDocument(ScratchFile scratch, SourceBackedStream.Source streams, COSDocument replaced) {
            super(scratch);
            this.scratch = scratch;
            this.streams = streams;
            this.replaced = replaced;
        }

        @Override
        public COSStream createCOSStream(COSDictionary dictionary) {
            SourceBackedStream stream = new SourceBackedStream(scratch, streams);
            for (Map.Entry<COSName, com.tom_roush.pdfbox.cos.COSBase> entry : dictionary.entrySet()) {
                stream.setItem(entry.getKey(), entry.getValue());
            }
            return stream;
        }
    }

    @Override
    protected void parseDictObjects(COSDictionary dict, COSName... excludeObjects) throws IOException {
        boolean catalog = COSName.CATALOG.equals(dict.getCOSName(COSName.TYPE));
        if (catalog && (excludeObjects == null || excludeObjects.length == 0)) {
            super.parseDictObjects(dict, COSName.STRUCT_TREE_ROOT);
        } else {
            super.parseDictObjects(dict, excludeObjects);
        }
    }

    @Override
    protected COSStream parseCOSStream(COSDictionary dic) throws IOException {
        copyingStream = true;
        try {
            return super.parseCOSStream(dic);
        } finally {
            copyingStream = false;
        }
    }

    @Override
    public boolean isCopyingStream() {
        return copyingStream;
    }

    @Override
    public long position() throws IOException {
        return file.getPosition();
    }

    @Override
    public byte[] read(long offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        // The parser and text extraction share one file handle; put it back where it was
        synchronized (file) {
            long resume = file.getPosition();
            file.seek(offset);
            int done = 0;
            while (done < length) {
                int n = file.read(bytes, done, length - done);
                if (n < 0) throw new EOFException("PDF ended inside a stream");
                done += n;
            }
            file.seek(resume);
        }
        return bytes;
    }
}
