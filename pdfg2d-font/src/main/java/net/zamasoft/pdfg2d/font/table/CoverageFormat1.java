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
public record CoverageFormat1(int[] glyphIds, boolean sorted) implements Coverage {
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
		boolean sorted = true;
		for (int i = 0; i < glyphCount; i++) {
			glyphIds[i] = raf.readUnsignedShort();
			if (i > 0 && glyphIds[i - 1] >= glyphIds[i]) {
				// Font violates the specification (numeric order): fall back to a linear scan.
				sorted = false;
			}
		}
		return new CoverageFormat1(glyphIds, sorted);
	}

	@Override
	public int getFormat() {
		return 1;
	}

	@Override
	public int findGlyph(final int glyphId) {
		// Called for each glyph during shaping. Use binary search if sorted as specified
		// (2026-08-01, 95-point plan increment 2: CJK font Coverage tables contain thousands of glyphs).
		if (this.sorted) {
			final int i = java.util.Arrays.binarySearch(this.glyphIds, glyphId);
			return i < 0 ? -1 : i;
		}
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
