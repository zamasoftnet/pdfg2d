package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Coverage table format 1.
 * 
 * @param glyphIds array of glyph IDs
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record CoverageFormat1(int[] glyphIds) implements Coverage {
	private static final long serialVersionUID = 0L;

	/**
	 * Reads a CoverageFormat1 from the given file.
	 * 
	 * @param raf the file to read from
	 * @return a new CoverageFormat1 instance
	 * @throws IOException if an I/O error occurs
	 */
	protected static CoverageFormat1 read(final RandomAccessFile raf) throws IOException {
		final int glyphCount = raf.readUnsignedShort();
		final int[] glyphIds = new int[glyphCount];
		for (int i = 0; i < glyphCount; i++) {
			glyphIds[i] = raf.readUnsignedShort();
		}
		return new CoverageFormat1(glyphIds);
	}

	@Override
	public int getFormat() {
		return 1;
	}

	@Override
	public int findGlyph(final int glyphId) {
		for (int i = 0; i < this.glyphIds.length; i++) {
			if (this.glyphIds[i] == glyphId) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int[] getGlyphIds() {
		return this.glyphIds.clone();
	}
}
