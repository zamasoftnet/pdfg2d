package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;

/**
 * Coverage table interface.
 * 
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public interface Coverage extends Serializable {

	int getFormat();

	/**
	 * Finds a glyph in the coverage table.
	 * 
	 * @param glyphId The ID of the glyph to find.
	 * @return The index of the glyph within the coverage, or -1 if the glyph can't
	 *         be found.
	 */
	int findGlyph(int glyphId);

	/**
	 * Returns the covered glyph ids in coverage-index order.
	 *
	 * @return the covered glyph ids
	 */
	int[] getGlyphIds();

	static Coverage read(final RandomAccessFile raf) throws IOException {
		final int format = raf.readUnsignedShort();
		if (format == 1) {
			return CoverageFormat1.read(raf);
		} else if (format == 2) {
			return CoverageFormat2.read(raf);
		}
		return null;
	}
}
