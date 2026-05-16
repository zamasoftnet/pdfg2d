package net.zamasoft.pdfg2d.font.table;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code loca} (Index to Location) table.
 * <p>
 * Maps glyph indices to byte offsets within the {@code glyf} table.  The
 * offset format is determined by the {@code indexToLocFormat} field of the
 * {@code head} table: short (16-bit) offsets are multiplied by 2 to obtain
 * the actual byte offset; long (32-bit) offsets are used directly.
 * </p>
 *
 * @param offsets the raw offset values read from the table
 * @param factor  multiplication factor applied to each offset (2 for short format, 1 for long format)
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 */
public record LocaTable(int[] offsets, short factor) implements Table {

	/**
	 * Reads a LocaTable from the given file.
	 * 
	 * @param de           the directory entry
	 * @param raf          the file to read from
	 * @param numGlyphs    the number of glyphs
	 * @param shortEntries whether to use short (16-bit) or long (32-bit) entries
	 * @return a new LocaTable
	 * @throws IOException if an I/O error occurs
	 */
	public static LocaTable read(
			final DirectoryEntry de,
			final RandomAccessFile raf,
			final int numGlyphs,
			final boolean shortEntries) throws IOException {
		final byte[] buf;
		synchronized (raf) {
			raf.seek(de.offset());
			buf = new byte[de.length()];
			raf.read(buf);
		}
		final int[] offsets = new int[numGlyphs + 1];
		final short factor;
		final ByteArrayInputStream bais = new ByteArrayInputStream(buf);
		if (shortEntries) {
			factor = 2;
			for (int i = 0; i <= numGlyphs; i++) {
				offsets[i] = (bais.read() << 8 | bais.read());
			}
		} else {
			factor = 1;
			for (int i = 0; i <= numGlyphs; i++) {
				offsets[i] = (bais.read() << 24 | bais.read() << 16 | bais.read() << 8 | bais.read());
			}
		}
		return new LocaTable(offsets, factor);
	}

	/**
	 * Returns the byte offset of the glyph at the given index within the {@code glyf} table.
	 *
	 * @param i the glyph index
	 * @return the byte offset in the {@code glyf} table
	 */
	public int getOffset(final int i) {
		return this.offsets[i] * this.factor;
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return LOCA;
	}
}
