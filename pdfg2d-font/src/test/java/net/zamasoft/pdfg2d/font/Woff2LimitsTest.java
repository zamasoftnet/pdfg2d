package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ensures that unpacking a WOFF2 file takes no more memory than the font really holds.
 *
 * <p>
 * A WOFF2 file is unpacked in memory. Its Brotli stream can expand without end and its directory can state any
 * length, so the lengths are checked before buffers are sized from them, decompression stops at the length the
 * directory accounts for, and a glyph cannot state more points than a glyph can have. The fonts here are small:
 * a decoder without these checks either unpacks them or asks for one array that no heap holds.
 * </p>
 */
public class Woff2LimitsTest {
	/** A Brotli stream of 65536 zero bytes. */
	private static final byte[] ZEROS_64K = { 0x1B, (byte) 0xFF, (byte) 0xFF, (byte) 0xF8, 0x27, 0x00, (byte) 0xE2,
			(byte) 0xB1, 0x40, 0x20, (byte) 0xF7, 0x1E, 0x00 };

	/**
	 * Wraps bytes in a Brotli stream of uncompressed meta-blocks (RFC 7932 section 9.2), so that the tests need no
	 * Brotli encoder.
	 *
	 * @param data the bytes to carry; not empty
	 */
	private static byte[] storedBrotli(final byte[] data) {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int pos = 0; pos < data.length; pos += 65536) {
			final int length = Math.min(65536, data.length - pos);
			// ISLAST = 0, MNIBBLES = 4, MLEN - 1, ISUNCOMPRESSED = 1, then zero bits up to the byte boundary.
			// The first block comes after the single zero bit of the stream header (a window of 64 KiB).
			final int header = pos == 0 ? ((length - 1) << 4) | (1 << 20) : ((length - 1) << 3) | (1 << 19);
			out.write(header);
			out.write(header >> 8);
			out.write(header >> 16);
			out.write(data, pos, length);
		}
		// ISLAST = 1, ISLASTEMPTY = 1.
		out.write(0x03);
		return out.toByteArray();
	}

	/** Writes a UIntBase128. */
	private static void base128(final ByteArrayOutputStream out, final long value) {
		boolean started = false;
		for (int shift = 28; shift >= 0; shift -= 7) {
			final int digit = (int) (value >> shift) & 0x7F;
			if (digit != 0 || started || shift == 0) {
				out.write(shift == 0 ? digit : digit | 0x80);
				started = true;
			}
		}
	}

	/** Writes the directory entry of a table that is stored as it is, under a tag of its own. */
	private static void entry(final ByteArrayOutputStream directory, final String tag, final long length) {
		directory.write(0x3F);
		directory.write(tag.getBytes(StandardCharsets.ISO_8859_1), 0, 4);
		base128(directory, length);
	}

	private static byte[] woff2(final int totalSfntSize, final ByteArrayOutputStream directory, final int numTables,
			final byte[] compressed) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(0x774F4632); // 'wOF2'
		out.writeInt(0x00010000); // flavor
		out.writeInt(48 + directory.size() + compressed.length); // length
		out.writeShort(numTables);
		out.writeShort(0); // reserved
		out.writeInt(totalSfntSize);
		out.writeInt(compressed.length); // totalCompressedSize
		out.writeShort(1); // majorVersion
		out.writeShort(0); // minorVersion
		out.writeInt(0); // metaOffset
		out.writeInt(0); // metaLength
		out.writeInt(0); // metaOrigLength
		out.writeInt(0); // privOffset
		out.writeInt(0); // privLength
		directory.writeTo(out);
		out.write(compressed);
		out.flush();
		return bytes.toByteArray();
	}

	private static File write(final Path dir, final byte[] font) throws IOException {
		return Files.write(dir.resolve("font.woff2"), font).toFile();
	}

	private static void assertRejected(final File font) {
		try (FontFile fontFile = new FontFile(font)) {
			fail("the font was unpacked to " + fontFile.getSfntFile().length() + " bytes");
		} catch (final IOException e) {
			// Refused as a broken font.
		}
	}

	/** A font with one small table unpacks to an sfnt with that table. */
	@Test
	public void smallFontIsUnpacked(@TempDir final Path dir) throws Exception {
		final byte[] table = "hello!".getBytes(StandardCharsets.ISO_8859_1);
		final ByteArrayOutputStream directory = new ByteArrayOutputStream();
		entry(directory, "zzzz", table.length);

		final byte[] sfnt;
		try (FontFile fontFile = new FontFile(write(dir, woff2(12 + 16 + 8, directory, 1, storedBrotli(table))))) {
			sfnt = Files.readAllBytes(fontFile.getSfntFile().toPath());
		}
		// Header (12 bytes), one directory entry (16 bytes), then the table, padded to a 4-byte boundary.
		assertEquals(12 + 16 + 8, sfnt.length);
		assertArrayEquals(table, Arrays.copyOfRange(sfnt, 28, 28 + table.length));
	}

	/** Thirteen bytes of Brotli that expand to 64 KiB, in a font of about seventy bytes. */
	@Test
	public void streamThatExpandsFarBeyondTheFileIsRejected(@TempDir final Path dir) throws Exception {
		final ByteArrayOutputStream directory = new ByteArrayOutputStream();
		entry(directory, "zzzz", 65536);
		assertRejected(write(dir, woff2(12 + 16 + 65536, directory, 1, ZEROS_64K)));
	}

	/**
	 * A table of 16 bytes followed by one that states 2 GiB. Added as 32-bit numbers the two lengths wrap around
	 * and pass a comparison with the length of the unpacked data; the buffer of the second table is then sized
	 * from its stated length.
	 */
	@Test
	public void tableLengthsAreCheckedBeforeBuffersAreSized(@TempDir final Path dir) throws Exception {
		final byte[] table = new byte[16];
		final ByteArrayOutputStream directory = new ByteArrayOutputStream();
		entry(directory, "yyyy", table.length);
		entry(directory, "zzzz", Integer.MAX_VALUE);
		assertRejected(write(dir, woff2(12 + 16 * 2 + 16, directory, 2, storedBrotli(table))));
	}

	/**
	 * A transformed glyph table whose only glyph states 32767 contours of 65535 points each. The arrays for the
	 * coordinates are sized from the sum, about two thousand million points, although a glyph numbers its points
	 * with 16 bits and the font is under 100 KiB.
	 */
	@Test
	public void glyphWithMorePointsThanAGlyphCanHaveIsRejected(@TempDir final Path dir) throws Exception {
		final int contours = 32767;
		final ByteArrayOutputStream glyf = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(glyf);
		out.writeShort(0); // reserved
		out.writeShort(0); // optionFlags
		out.writeShort(1); // numGlyphs
		out.writeShort(0); // indexFormat
		out.writeInt(2); // nContourStreamSize
		out.writeInt(3 * contours); // nPointsStreamSize
		out.writeInt(0); // flagStreamSize
		out.writeInt(0); // glyphStreamSize
		out.writeInt(0); // compositeStreamSize
		out.writeInt(4); // bboxStreamSize
		out.writeInt(0); // instructionStreamSize
		out.writeShort(contours); // nContour stream: the one glyph
		for (int i = 0; i < contours; ++i) {
			// nPoints stream: 65535 as a 255UInt16.
			out.writeByte(253);
			out.writeShort(0xFFFF);
		}
		out.writeInt(0); // bbox stream: the bitmap for one glyph, which has no explicit box
		out.flush();

		final ByteArrayOutputStream directory = new ByteArrayOutputStream();
		directory.write(10); // glyf, transformed
		base128(directory, glyf.size()); // origLength
		base128(directory, glyf.size()); // transformLength
		directory.write(11); // loca, transformed
		base128(directory, 8); // origLength
		base128(directory, 0); // transformLength
		assertRejected(write(dir, woff2(12 + 16 * 2 + glyf.size() + 8, directory, 2,
				storedBrotli(glyf.toByteArray()))));
	}
}
