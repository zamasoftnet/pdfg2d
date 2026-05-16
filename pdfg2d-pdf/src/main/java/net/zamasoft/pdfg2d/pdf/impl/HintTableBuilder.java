package net.zamasoft.pdfg2d.pdf.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.pdf.ObjectRef;

/**
 * Builds the linearized PDF Hint Tables required by PDF 1.5+ Appendix F.
 * <p>
 * A linearized PDF begins with a first-page section that allows a PDF viewer
 * to render page&nbsp;1 while the rest of the document is still downloading.
 * Two hint tables enable fast random access to any page:
 * <ul>
 *   <li><strong>Page Offset Hint Table</strong> – records the byte extent of
 *       each page and the location of its content stream.</li>
 *   <li><strong>Shared Object Hint Table</strong> – records resources (fonts,
 *       images, etc.) that are referenced by multiple pages.</li>
 * </ul>
 * Both tables are bit-packed using the format defined in PDF 1.7 Appendix F,
 * Tables F.3 – F.6.
 * </p>
 * <p>
 * Usage:
 * <pre>{@code
 * var builder = new HintTableBuilder();
 * builder.setFirstPageLocation(firstPageOffset);
 * for (var page : pages) { builder.addPage(page); }
 * for (var ref : sharedRefs) { builder.addSharedObject(ref, length); }
 * builder.build(outputStream);
 * }</pre>
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public class HintTableBuilder {

    /**
     * A minimal bit-level output stream that packs values into a byte buffer.
     * <p>
     * Values are written most-significant-bit first.  Partial bytes are
     * accumulated until a full octet is available; call {@link #padToByte()} to
     * flush any remaining bits before reading the result.
     * </p>
     */
    private static class BitOutputStream {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private int currentByte = 0;
        private int bitCount = 0;

        /**
         * Writes the {@code bits} least-significant bits of {@code value},
         * MSB first.
         *
         * @param value unsigned value to write
         * @param bits  number of bits to write (0 = no-op)
         */
        public void write(long value, int bits) {
            if (bits == 0)
                return;
            for (int i = bits - 1; i >= 0; i--) {
                int bit = (int) ((value >> i) & 1);
                currentByte = (currentByte << 1) | bit;
                bitCount++;
                if (bitCount == 8) {
                    buf.write(currentByte);
                    currentByte = 0;
                    bitCount = 0;
                }
            }
        }

        /**
         * Pads the current byte to a byte boundary by appending zero bits.
         * This must be called between the Page Offset Table and the Shared
         * Object Table so that the latter starts at an exact byte offset.
         */
        public void padToByte() {
            if (bitCount > 0) {
                currentByte = currentByte << (8 - bitCount);
                buf.write(currentByte);
                currentByte = 0;
                bitCount = 0;
            }
        }

        /**
         * Returns all accumulated bytes, padding the last byte if necessary.
         *
         * @return complete byte array
         */
        public byte[] toByteArray() {
            padToByte();
            return buf.toByteArray();
        }

        /**
         * Returns the number of <em>complete</em> bytes written plus one if a
         * partial byte is buffered.
         *
         * @return byte count including any partial byte
         */
        public int getByteCount() {
            return buf.size() + (bitCount > 0 ? 1 : 0);
        }
    }

    /**
     * Returns the minimum number of bits required to represent {@code n}.
     * Returns {@code 0} for non-positive values.
     *
     * @param n value to test
     * @return bit width in the range [0, 32]
     */
    private int bitsNeeded(long n) {
        if (n <= 0)
            return 0;
        return 32 - Integer.numberOfLeadingZeros((int) n);
    }

    /**
     * Per-page data used to populate the Page Offset Hint Table.
     * <p>
     * All fields use absolute byte offsets within the linearized file as
     * reported by the fragmented-output position snapshot, before any shift
     * introduced by the first-page xref section has been applied.
     * </p>
     */
    public static class PageEntry {
        /** Number of PDF objects that make up this page (page dict + content + annots). */
        public long objectsPerPage;
        /** Total byte extent of all objects belonging to this page. */
        public long pageLength;
        /** Indices into the shared-object table for resources used by this page. */
        public List<Integer> sharedObjectIndices = new ArrayList<>();
        /** Byte offset of the content stream start relative to the page extent start. */
        public long contentStreamStart;
        /** Byte length of the content stream data. */
        public long contentStreamLength;
    }

    /**
     * Per-entry data used to populate the Shared Object Hint Table.
     */
    private static class SharedObjectEntry {
        final ObjectRef ref;
        final long location;
        final int length;

        SharedObjectEntry(ObjectRef ref, long location, int length) {
            this.ref = ref;
            this.location = location;
            this.length = length;
        }
    }

    private final List<PageEntry> pages = new ArrayList<>();
    private final List<SharedObjectEntry> firstPageEntries = new ArrayList<>();
    private final List<SharedObjectEntry> sharedSectionEntries = new ArrayList<>();

    /** Absolute byte offset of the first page object in the linearized file. */
    private long firstPageLocation = 0;

    /**
     * Sets the absolute byte offset of the first page object.
     * <p>
     * This value is written into the Page Offset Hint Table header and must
     * reflect the final (shifted) position in the output file.
     * </p>
     *
     * @param loc absolute byte offset of the first page
     */
    public void setFirstPageLocation(long loc) {
        this.firstPageLocation = loc;
    }

    /**
     * Appends a page entry to the Page Offset Hint Table.
     *
     * @param page page data; must not be {@code null}
     */
    public void addPage(PageEntry page) {
        pages.add(page);
    }

    /**
     * Registers a shared PDF object and returns its index in the shared-object
     * table.
     * <p>
     * The returned index can be stored in {@link PageEntry#sharedObjectIndices}
     * to declare that a page depends on this shared object.
     * </p>
     *
     * @param ref    the PDF object reference
     * @param length serialized byte length of the object (including overhead)
     * @return zero-based index of the registered shared object
     */
    public int addFirstPageObject(ObjectRef ref, long location, int length) {
        firstPageEntries.add(new SharedObjectEntry(ref, location, length));
        return firstPageEntries.size() - 1;
    }

    /**
     * Registers a shared PDF object in the shared objects section (part 8) and
     * returns its index in the combined shared-object hint table.
     *
     * @param ref      the PDF object reference
     * @param location absolute byte offset of the object in the final file
     * @param length   serialized byte length of the object (including overhead)
     * @return zero-based index of the registered shared object in the combined table
     */
    public int addSharedObject(ObjectRef ref, long location, int length) {
        sharedSectionEntries.add(new SharedObjectEntry(ref, location, length));
        return firstPageEntries.size() + sharedSectionEntries.size() - 1;
    }

    /**
     * Byte offset of the Shared Object Hint Table within the combined hint stream,
     * calculated by {@link #build(OutputStream)} after the Page Offset table has
     * been written and padded.
     */
    private int sharedObjectTableOffset = 0;

    /**
     * Returns the byte offset of the Shared Object Hint Table within the raw
     * (uncompressed) hint stream produced by {@link #build(OutputStream)}.
     * <p>
     * This value is needed for the {@code /S} entry of the hint stream object
     * dictionary and is only valid after {@link #build(OutputStream)} has been
     * called.
     * </p>
     *
     * @return byte offset of the Shared Object Hint Table
     */
    public int getSharedObjectTableOffset() {
        return sharedObjectTableOffset;
    }

    /**
     * Serializes both hint tables into the given stream.
     * <p>
     * The output consists of:
     * <ol>
     *   <li>Page Offset Hint Table (bit-packed, Tables F.3 / F.4)</li>
     *   <li>Zero-padding to a byte boundary</li>
     *   <li>Shared Object Hint Table (bit-packed, Tables F.5 / F.6)</li>
     * </ol>
     * The stream is typically compressed by the caller before embedding it in
     * the PDF.
     * </p>
     *
     * @param out destination stream for the raw hint bytes
     * @throws IOException if an I/O error occurs
     */
    public void build(OutputStream out) throws IOException {
        var bits = new BitOutputStream();

        // 1. Page Offset Hint Table
        buildPageOffsetHintTable(bits);

        // Pad to byte boundary so the Shared Object Table starts at a byte offset
        bits.padToByte();

        // Record the offset for the /S dictionary entry
        this.sharedObjectTableOffset = bits.getByteCount();

        // 2. Shared Object Hint Table
        buildSharedObjectHintTable(bits);

        out.write(bits.toByteArray());
    }

    /**
     * Writes the Page Offset Hint Table (PDF 1.7 Appendix F, Tables F.3 / F.4).
     * <p>
     * The table header encodes minimum values and bit-widths for each field, then
     * per-page delta entries follow in column-major order.
     * </p>
     *
     * @param bits target bit stream
     */
    private void buildPageOffsetHintTable(BitOutputStream bits) {
        if (pages.isEmpty())
            return;

        long minObjectsPerPage = Long.MAX_VALUE;
        long maxObjectsPerPage = 0;
        long minPageLength = Long.MAX_VALUE;
        long maxPageLength = 0;
        long minContentStart = Long.MAX_VALUE;
        long maxContentStart = 0;
        long minContentLength = Long.MAX_VALUE;
        long maxContentLength = 0;
        long maxSharedObjsPerPage = 0;
        long maxSharedObjId = firstPageEntries.size() + sharedSectionEntries.size();

        for (var p : pages) {
            minObjectsPerPage = Math.min(minObjectsPerPage, p.objectsPerPage);
            maxObjectsPerPage = Math.max(maxObjectsPerPage, p.objectsPerPage);
            minPageLength = Math.min(minPageLength, p.pageLength);
            maxPageLength = Math.max(maxPageLength, p.pageLength);
            minContentStart = Math.min(minContentStart, p.contentStreamStart);
            maxContentStart = Math.max(maxContentStart, p.contentStreamStart);
            minContentLength = Math.min(minContentLength, p.contentStreamLength);
            maxContentLength = Math.max(maxContentLength, p.contentStreamLength);
            maxSharedObjsPerPage = Math.max(maxSharedObjsPerPage, p.sharedObjectIndices.size());
        }

        int bObjectsDelta = bitsNeeded(maxObjectsPerPage - minObjectsPerPage);
        int bPageLenDelta = bitsNeeded(maxPageLength - minPageLength);
        int bContentStartDelta = bitsNeeded(maxContentStart - minContentStart);
        int bContentLenDelta = bitsNeeded(maxContentLength - minContentLength);
        int bSharedObjsCount = bitsNeeded(maxSharedObjsPerPage);
        int bSharedObjId = bitsNeeded(Math.max(0, maxSharedObjId - 1));
        int bSharedObjPosNum = 0;
        int sharedObjPosDenom = maxSharedObjsPerPage > 0 ? 4 : 0;

        // Header fields (Table F.3)
        bits.write(minObjectsPerPage, 32);
        bits.write(firstPageLocation, 32);
        bits.write(bObjectsDelta, 16);
        bits.write(minPageLength, 32);
        bits.write(bPageLenDelta, 16);
        bits.write(minContentStart, 32);
        bits.write(bContentStartDelta, 16);
        bits.write(minContentLength, 32);
        bits.write(bContentLenDelta, 16);
        bits.write(bSharedObjsCount, 16);
        bits.write(bSharedObjId, 16);
        bits.write(bSharedObjPosNum, 16);
        bits.write(sharedObjPosDenom, 16);

        // Per-page entries written in column-major order (Table F.4)

        // Item 1: objects-per-page delta
        for (var p : pages) {
            bits.write(p.objectsPerPage - minObjectsPerPage, bObjectsDelta);
        }
        bits.padToByte();
        // Item 2: page-length delta
        for (var p : pages) {
            bits.write(p.pageLength - minPageLength, bPageLenDelta);
        }
        bits.padToByte();
        // Item 3: number of shared objects per page
        for (var p : pages) {
            bits.write(p.sharedObjectIndices.size(), bSharedObjsCount);
        }
        bits.padToByte();
        // Item 4: shared object indices
        for (var p : pages) {
            for (int idx : p.sharedObjectIndices) {
                bits.write(idx, bSharedObjId);
            }
        }
        bits.padToByte();
        // Item 5: shared_object_position_numerators (width = 0, skipped)
        bits.padToByte();

        // Item 6: content-stream-start delta
        for (var p : pages) {
            bits.write(p.contentStreamStart - minContentStart, bContentStartDelta);
        }
        bits.padToByte();
        // Item 7: content-stream-length delta
        for (var p : pages) {
            bits.write(p.contentStreamLength - minContentLength, bContentLenDelta);
        }
        bits.padToByte();
    }

    /**
     * Writes the Shared Object Hint Table (PDF 1.7 Appendix F, Tables F.5 / F.6).
     * <p>
     * All shared objects are treated as belonging to the first-page section
     * (Part 6) of the linearized file.
     * </p>
     *
     * @param bits target bit stream
     */
    private void buildSharedObjectHintTable(BitOutputStream bits) {
        final var sharedObjects = new ArrayList<SharedObjectEntry>(firstPageEntries.size() + sharedSectionEntries.size());
        sharedObjects.addAll(firstPageEntries);
        sharedObjects.addAll(sharedSectionEntries);
        final int firstPageEntryCount = firstPageEntries.size();
        final int count = sharedObjects.size();
        long minGroupLen = Long.MAX_VALUE;
        long maxGroupLen = 0;
        if (count > 0) {
            for (var e : sharedObjects) {
                minGroupLen = Math.min(minGroupLen, e.length);
                maxGroupLen = Math.max(maxGroupLen, e.length);
            }
        } else {
            minGroupLen = 0;
        }

        int bGroupLenDelta = bitsNeeded(maxGroupLen - minGroupLen);

        final long firstSharedObjectNumber = sharedSectionEntries.isEmpty() ? 0 : sharedSectionEntries.get(0).ref.objectNumber();
        final long firstSharedObjectLocation = sharedSectionEntries.isEmpty() ? 0 : sharedSectionEntries.get(0).location;

        // Header fields (Table F.5)
        bits.write(firstSharedObjectNumber, 32);
        bits.write(firstSharedObjectLocation, 32);
        bits.write(firstPageEntryCount, 32);      // num_entries_first_page
        bits.write(count, 32);      // num_entries_total
        bits.write(0, 16);          // bits_for_greatest_objects_in_group
        bits.write(minGroupLen, 32);// least_group_length
        bits.write(bGroupLenDelta, 16); // bits_for_group_length_delta

        // Per-entry data written in column-major order (Table F.6)

        // Item 1: group-length delta
        for (var e : sharedObjects) {
            bits.write(e.length - minGroupLen, bGroupLenDelta);
        }
        bits.padToByte();
        // Item 2: signature-present flag (always 0)
        for (int i = 0; i < count; i++) {
            bits.write(0, 1);
        }
        bits.padToByte();
        // Item 3: signature data (skipped, no signatures)
        // Item 4: objects-in-group minus 1 (bit width = 0, skipped)
    }
}
