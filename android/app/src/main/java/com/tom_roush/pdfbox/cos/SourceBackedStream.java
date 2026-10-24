package com.tom_roush.pdfbox.cos;

import com.tom_roush.pdfbox.filter.DecodeOptions;
import com.tom_roush.pdfbox.filter.Filter;
import com.tom_roush.pdfbox.filter.FilterFactory;
import com.tom_roush.pdfbox.io.ScratchFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stream that remembers where its bytes are in the PDF file instead of copying them.
 *
 * PDFBox 2 copies every stream into scratch buffers while parsing, and each buffer keeps a 4 KB page on the heap.
 * An illustrated handbook with 22,000 streams spent 90 MB on those pages and crashed the reader. Here, while the
 * parser copies a stream straight from the file, only the file offset and length are recorded; the bytes are read
 * back when someone actually decodes the stream (text extraction reads page content, fonts and forms, never images).
 * If the copied bytes are not a plain run of the file (a wrong /Length, so the parser scanned for "endstream"),
 * the bytes themselves are kept. Any later rewrite (decryption, edits) falls back to PDFBox's own storage.
 *
 * Lives in PDFBox's package because COSInputStream.create is package-private.
 */
public final class SourceBackedStream extends COSStream {
    /** The PDF file the parser is reading. */
    public interface Source {
        /** True while the parser is copying a stream's bytes straight from the file. */
        boolean isCopyingStream();

        long position() throws IOException;

        byte[] read(long offset, int length) throws IOException;
    }

    private enum Storage { FILE, BYTES, PDFBOX }

    private final ScratchFile scratch;
    private final Source source;
    private Storage storage = Storage.PDFBOX;
    private long offset;
    private int length;
    private byte[] bytes;

    public SourceBackedStream(ScratchFile scratch, Source source) {
        super(scratch);
        this.scratch = scratch;
        this.source = source;
    }

    /** True when the bytes are read from the file on demand rather than held in memory. */
    public boolean isSourceBacked() {
        return storage == Storage.FILE;
    }

    @Override
    public OutputStream createRawOutputStream() throws IOException {
        if (source.isCopyingStream()) return new Recorder();
        storage = Storage.PDFBOX;
        bytes = null;
        return super.createRawOutputStream();
    }

    @Override
    public OutputStream createOutputStream(COSBase filters) throws IOException {
        storage = Storage.PDFBOX;
        bytes = null;
        return super.createOutputStream(filters);
    }

    @Override
    public InputStream createRawInputStream() throws IOException {
        switch (storage) {
            case FILE: return new ByteArrayInputStream(source.read(offset, length));
            case BYTES: return new ByteArrayInputStream(bytes);
            default: return super.createRawInputStream();
        }
    }

    @Override
    public COSInputStream createInputStream() throws IOException {
        return createInputStream(DecodeOptions.DEFAULT);
    }

    @Override
    public COSInputStream createInputStream(DecodeOptions options) throws IOException {
        if (storage == Storage.PDFBOX) return super.createInputStream(options);
        return COSInputStream.create(filters(), this, createRawInputStream(), scratch, options);
    }

    private List<Filter> filters() throws IOException {
        COSBase value = getFilters();
        if (value instanceof COSName) return Collections.singletonList(FilterFactory.INSTANCE.getFilter((COSName) value));
        List<Filter> list = new ArrayList<Filter>();
        if (value instanceof COSArray) {
            for (COSBase item : (COSArray) value) {
                if (item instanceof COSName) list.add(FilterFactory.INSTANCE.getFilter((COSName) item));
            }
        }
        return list;
    }

    /** Receives the parser's copy: records where it came from, or keeps the bytes when that can't be trusted. */
    private final class Recorder extends OutputStream {
        private long start = -1;
        private long written;
        private ByteArrayOutputStream kept;

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len <= 0) return;
            if (kept == null) {
                long end = source.position();
                if (start < 0) start = end - len;
                // Copied straight from the file: these bytes end exactly where the parser is now
                if (end - start == written + len) {
                    written += len;
                    return;
                }
                kept = new ByteArrayOutputStream();
                if (written > 0) kept.write(source.read(start, (int) written));
            }
            kept.write(b, off, len);
        }

        @Override
        public void close() {
            if (kept != null) {
                storage = Storage.BYTES;
                bytes = kept.toByteArray();
                length = bytes.length;
            } else {
                storage = Storage.FILE;
                offset = Math.max(start, 0);
                length = (int) written;
            }
            // COSStream's own output stream records the length on close
            setItem(COSName.LENGTH, COSInteger.get(length));
        }
    }
}
