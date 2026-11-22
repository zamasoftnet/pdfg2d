package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ensures that unpacking a WOFF file writes no more than the font really holds.
 *
 * <p>
 * The table directory of a WOFF file states how long each table is once unpacked. A font of a hundred bytes can
 * state two gigabytes per table, so the lengths are checked against the size the header declares and against the
 * length of the file, a table that ends early is an error rather than something to fill with zeros, and the
 * unpacked copy of a font that is refused is deleted. The fonts here are kept small enough that a decoder without
 * these checks writes a few megabytes and returns.
 * </p>
 */
public class WoffLimitsTest {
	private static final int TRUETYPE = 0x00010000;

	/** A table of the directory: where its data starts in the data block, its stored and its unpacked length. */
	private record Table(int offset, int compLength, int origLength) {
	}

	/** The size of the sfnt that the tables unpack to, as the header of a well-formed font declares it. */
	private static int sfntSize(final Table... tables) {
		int size = 12 + 16 * tables.length;
		for (final Table table : tables) {
			size += (table.origLength() + 3) & ~3;
		}
		return size;
	}

	private static byte[] woff(final int flavor, final int totalSfntSize, final byte[] data, final Table... tables)
			throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		final int dataStart = 44 + 20 * tables.length;
		out.writeInt(0x774F4646); // 'wOFF'
		out.writeInt(flavor);
		out.writeInt(dataStart + data.length); // length
		out.writeShort(tables.length);
		out.writeShort(0); // reserved
		out.writeInt(totalSfntSize);
		out.writeShort(1); // majorVersion
		out.writeShort(0); // minorVersion
		out.writeInt(0); // metaOffset
		out.writeInt(0); // metaLength
		out.writeInt(0); // metaOrigLength
		out.writeInt(0); // privOffset
		out.writeInt(0); // privLength
		for (int i = 0; i < tables.length; ++i) {
			out.writeBytes("tb" + (char) ('a' + i / 26) + (char) ('a' + i % 26));
			out.writeInt(dataStart + tables[i].offset());
			out.writeInt(tables[i].compLength());
			out.writeInt(tables[i].origLength());
			out.writeInt(0); // origChecksum
		}
		out.write(data);
		out.flush();
		return bytes.toByteArray();
	}

	private static byte[] deflate(final byte[] data) {
		final Deflater deflater = new Deflater();
		deflater.setInput(data);
		deflater.finish();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buff = new byte[4096];
		while (!deflater.finished()) {
			out.write(buff, 0, deflater.deflate(buff));
		}
		deflater.end();
		return out.toByteArray();
	}

	private static File write(final Path dir, final byte[] font) throws IOException {
		return Files.write(dir.resolve("font.woff"), font).toFile();
	}

	private static void assertRejected(final File font) {
		try (FontFile fontFile = new FontFile(font)) {
			fail("the font was unpacked to " + fontFile.getSfntFile().length() + " bytes");
		} catch (final IOException e) {
			// Refused as a broken font.
		}
	}

	/** A font with a stored and a deflated table unpacks to the sfnt that its directory describes. */
	@Test
	public void wellFormedFontIsUnpacked(@TempDir final Path dir) throws Exception {
		final byte[] stored = "0123456789".getBytes(StandardCharsets.ISO_8859_1);
		final byte[] plain = new byte[2000];
		for (int i = 0; i < plain.length; ++i) {
			plain[i] = (byte) (i % 7);
		}
		final byte[] deflated = deflate(plain);
		assertTrue(deflated.length < plain.length);
		final byte[] data = new byte[stored.length + deflated.length];
		System.arraycopy(stored, 0, data, 0, stored.length);
		System.arraycopy(deflated, 0, data, stored.length, deflated.length);
		final Table[] tables = { new Table(0, stored.length, stored.length),
				new Table(stored.length, deflated.length, plain.length) };

		final byte[] sfnt;
		try (FontFile fontFile = new FontFile(write(dir, woff(TRUETYPE, sfntSize(tables), data, tables)))) {
			sfnt = Files.readAllBytes(fontFile.getSfntFile().toPath());
		}
		assertEquals(sfntSize(tables), sfnt.length);
		// Header (12 bytes), two directory entries (16 bytes each), then the tables on 4-byte boundaries.
		assertArrayEquals(stored, Arrays.copyOfRange(sfnt, 44, 44 + stored.length));
		assertArrayEquals(plain, Arrays.copyOfRange(sfnt, 56, 56 + plain.length));
	}

	/** One table that states 8 MiB, with no data behind it. */
	@Test
	public void tableWithoutItsDataIsRejected(@TempDir final Path dir) throws Exception {
		final int claimed = 8 * 1024 * 1024;
		final Table table = new Table(0, claimed, claimed);
		assertRejected(write(dir, woff(TRUETYPE, sfntSize(table), new byte[0], table)));
	}

	/** Two tables that are both present, in a font whose header has room for one of them. */
	@Test
	public void tablesBeyondTheDeclaredFontSizeAreRejected(@TempDir final Path dir) throws Exception {
		final Table first = new Table(0, 16, 16);
		final Table second = new Table(16, 16, 16);
		final int roomForOne = 12 + 16 * 2 + 16;
		assertRejected(write(dir, woff(TRUETYPE, roomForOne, new byte[32], first, second)));
	}

	/**
	 * Eight tables that share one deflated megabyte of zeros: a file of about a kilobyte whose header and
	 * directory agree with each other and describe 8 MiB.
	 */
	@Test
	public void fontThatUnpacksFarBeyondItsOwnSizeIsRejected(@TempDir final Path dir) throws Exception {
		final int length = 1024 * 1024;
		final byte[] deflated = deflate(new byte[length]);
		final Table[] tables = new Table[8];
		Arrays.fill(tables, new Table(0, deflated.length, length));
		assertRejected(write(dir, woff(TRUETYPE, sfntSize(tables), deflated, tables)));
	}

	/**
	 * A font that fails while it is being unpacked (its second table is not the zlib data it claims to be) leaves
	 * no unpacked copy in the temporary directory.
	 */
	@Test
	public void rejectedFontLeavesNoTemporaryFile(@TempDir final Path dir) throws Exception {
		// The unpacked copy starts with the flavor of the font. A random one tells the copy of this font from
		// those that other tests and other processes keep in the same directory.
		final int flavor = new Random().nextInt();
		final byte[] data = new byte[24];
		Arrays.fill(data, 16, 24, (byte) 0xFF);
		final Table stored = new Table(0, 16, 16);
		final Table notDeflated = new Table(16, 8, 64);
		assertRejected(write(dir, woff(flavor, sfntSize(stored, notDeflated), data, stored, notDeflated)));

		final List<File> left = new ArrayList<>();
		final File[] files = new File(System.getProperty("java.io.tmpdir"))
				.listFiles((parent, name) -> name.startsWith("pdfg2d-font-") && name.endsWith(".dat"));
		if (files != null) {
			for (final File file : files) {
				try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
					if (raf.length() >= 4 && raf.readInt() == flavor) {
						left.add(file);
					}
				} catch (final IOException e) {
					// A file of another process that is already gone.
				}
			}
		}
		for (final File file : left) {
			file.delete();
		}
		assertTrue(left.isEmpty(), "the unpacked copy of a rejected font was left behind: " + left);
	}
}
