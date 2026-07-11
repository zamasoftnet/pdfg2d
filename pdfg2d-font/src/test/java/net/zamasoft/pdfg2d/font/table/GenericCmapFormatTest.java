package net.zamasoft.pdfg2d.font.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the cmap subtable parsers in {@link GenericCmapFormat},
 * exercising each format with synthetic binary data. The data starts at the
 * point after the 16-bit format field, matching what
 * {@link GenericCmapFormat#read(int, int, RandomAccessFile)} expects.
 */
public class GenericCmapFormatTest {

	@TempDir
	Path tempDir;

	/**
	 * Runs the parser over the given subtable bytes (excluding the leading
	 * format field).
	 */
	private GenericCmapFormat parse(final int format, final int numGlyphs, final byte[] data) throws IOException {
		final var file = Files.createTempFile(this.tempDir, "cmap", ".bin").toFile();
		Files.write(file.toPath(), data);
		try (final var raf = new RandomAccessFile(file, "r")) {
			return GenericCmapFormat.read(format, numGlyphs, raf);
		}
	}

	/** Formats below 8 start with 16-bit length and language fields. */
	private static void writeShortHeader(final DataOutputStream out) throws IOException {
		out.writeShort(0); // length (unused by the parser)
		out.writeShort(0); // language
	}

	/** Formats 8-13 start with reserved, 32-bit length and language fields. */
	private static void writeLongHeader(final DataOutputStream out) throws IOException {
		out.writeShort(0); // reserved
		out.writeInt(0); // length (unused by the parser)
		out.writeInt(0); // language
	}

	@Test
	public void testFormat0MapsSingleBytes() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeShortHeader(out);
		final var mapping = new byte[256];
		mapping[65] = 3; // 'A' -> glyph 3
		mapping[66] = 4; // 'B' -> glyph 4
		out.write(mapping);

		final var cmap = parse(0, 256, buff.toByteArray());
		assertEquals(3, cmap.mapCharCode(65));
		assertEquals(4, cmap.mapCharCode(66));
		assertEquals(Integer.valueOf(65), cmap.getCharacterCode(3));
	}

	@Test
	public void testFormat4MapsSegmentWithDelta() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeShortHeader(out);
		// Two segments: 'A'..'C' with idDelta, and the mandatory 0xFFFF terminator.
		out.writeShort(4); // segCountX2
		out.writeShort(0); // searchRange (unused)
		out.writeShort(0); // entrySelector (unused)
		out.writeShort(0); // rangeShift (unused)
		out.writeShort(0x43); // endCount[0] = 'C'
		out.writeShort(0xFFFF); // endCount[1]
		out.writeShort(0); // reservedPad
		out.writeShort(0x41); // startCount[0] = 'A'
		out.writeShort(0xFFFF); // startCount[1]
		out.writeShort((10 - 0x41) & 0xFFFF); // idDelta[0]: 'A' -> glyph 10
		out.writeShort(1); // idDelta[1]
		out.writeShort(0); // idRangeOffset[0]
		out.writeShort(0); // idRangeOffset[1]

		final var cmap = parse(4, 32, buff.toByteArray());
		assertEquals(10, cmap.mapCharCode('A'));
		assertEquals(11, cmap.mapCharCode('B'));
		assertEquals(12, cmap.mapCharCode('C'));
		assertEquals(0, cmap.mapCharCode('D'), "Unmapped character must map to glyph 0 (.notdef)");
		assertEquals(Integer.valueOf('B'), cmap.getCharacterCode(11));
	}

	@Test
	public void testFormat6MapsDenseRange() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeShortHeader(out);
		out.writeShort(0x30); // firstCode = '0'
		out.writeShort(3); // entryCount
		out.writeShort(5);
		out.writeShort(6);
		out.writeShort(7);

		final var cmap = parse(6, 16, buff.toByteArray());
		assertEquals(5, cmap.mapCharCode(0x30));
		assertEquals(7, cmap.mapCharCode(0x32));
		assertEquals(0, cmap.mapCharCode(0x33));
	}

	@Test
	public void testFormat10MapsSupplementaryPlaneRange() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeLongHeader(out);
		out.writeInt(0x10000); // startCharCode (first supplementary plane char)
		out.writeInt(4); // numChars
		out.writeShort(1);
		out.writeShort(2);
		out.writeShort(3);
		out.writeShort(4);

		final var cmap = parse(10, 8, buff.toByteArray());
		assertEquals(1, cmap.mapCharCode(0x10000));
		assertEquals(3, cmap.mapCharCode(0x10002));
		assertEquals(0, cmap.mapCharCode(0x10004));
		assertEquals(Integer.valueOf(0x10001), cmap.getCharacterCode(2));
	}

	@Test
	public void testFormat10RejectsInvalidGlyphIndex() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeLongHeader(out);
		out.writeInt(0x20);
		out.writeInt(2);
		out.writeShort(1);
		out.writeShort(999); // out of range for numGlyphs=4

		// Parsing stops at the invalid index instead of throwing.
		final var cmap = parse(10, 4, buff.toByteArray());
		assertEquals(1, cmap.mapCharCode(0x20));
		assertEquals(0, cmap.mapCharCode(0x21));
	}

	@Test
	public void testFormat12MapsGroups() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeLongHeader(out);
		out.writeInt(1); // nGroups
		out.writeInt(0x20); // startCharCode
		out.writeInt(0x22); // endCharCode
		out.writeInt(7); // startGlyphID

		final var cmap = parse(12, 16, buff.toByteArray());
		assertEquals(7, cmap.mapCharCode(0x20));
		assertEquals(8, cmap.mapCharCode(0x21));
		assertEquals(9, cmap.mapCharCode(0x22));
		assertEquals(0, cmap.mapCharCode(0x23));
	}

	@Test
	public void testFormat13MapsManyToOne() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeLongHeader(out);
		out.writeInt(1); // nGroups
		out.writeInt(0x100);
		out.writeInt(0x102);
		out.writeInt(9); // single glyph for the whole range

		final var cmap = parse(13, 16, buff.toByteArray());
		assertEquals(9, cmap.mapCharCode(0x100));
		assertEquals(9, cmap.mapCharCode(0x101));
		assertEquals(9, cmap.mapCharCode(0x102));
	}

	@Test
	public void testUnknownFormatThrows() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeShortHeader(out);
		final var data = buff.toByteArray();
		assertThrows(IOException.class, () -> parse(5, 16, data));
	}

	@Test
	public void testGetCharacterCodeOutOfRangeReturnsNull() throws Exception {
		final var buff = new ByteArrayOutputStream();
		final var out = new DataOutputStream(buff);
		writeShortHeader(out);
		out.write(new byte[256]);
		final var cmap = parse(0, 256, buff.toByteArray());
		assertNull(cmap.getCharacterCode(-1));
		assertNull(cmap.getCharacterCode(100000));
	}
}
