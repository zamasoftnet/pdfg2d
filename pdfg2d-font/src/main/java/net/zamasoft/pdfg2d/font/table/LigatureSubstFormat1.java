package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Ligature substitution format 1. The {@link Coverage} table identifies the
 * first glyph of each ligature; its coverage index selects the matching
 * {@link LigatureSet}.
 *
 * @param coverage     the coverage of first (starting) glyphs
 * @param ligatureSets the ligature sets, parallel to the coverage indices
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record LigatureSubstFormat1(Coverage coverage, LigatureSet[] ligatureSets) implements LigatureSubst {

	/**
	 * Reads a LigatureSubstFormat1 from the given file.
	 *
	 * @param raf    the file to read from
	 * @param offset the offset of this subtable
	 * @return a new LigatureSubstFormat1
	 * @throws IOException if an I/O error occurs
	 */
	protected static LigatureSubstFormat1 read(final RandomAccessFile raf, final int offset) throws IOException {
		final int coverageOffset = raf.readUnsignedShort();
		final int ligSetCount = raf.readUnsignedShort();
		final int[] ligatureSetOffsets = new int[ligSetCount];
		for (int i = 0; i < ligSetCount; i++) {
			ligatureSetOffsets[i] = raf.readUnsignedShort();
		}
		final Coverage coverage;
		final LigatureSet[] ligatureSets = new LigatureSet[ligSetCount];
		synchronized (raf) {
			raf.seek(offset + coverageOffset);
			coverage = Coverage.read(raf);
			for (int i = 0; i < ligSetCount; i++) {
				raf.seek(offset + ligatureSetOffsets[i]);
				ligatureSets[i] = new LigatureSet(raf, offset + ligatureSetOffsets[i]);
			}
		}
		return new LigatureSubstFormat1(coverage, ligatureSets);
	}

	@Override
	public int getFormat() {
		return 1;
	}

	public int getLigatureSetCount() {
		return this.ligatureSets.length;
	}

	public LigatureSet getLigatureSet(final int i) {
		return this.ligatureSets[i];
	}

	/**
	 * Returns the ligature set for the given starting glyph, or {@code null}
	 * if the glyph starts no ligature.
	 *
	 * @param startGlyphId the first glyph of a potential ligature
	 * @return the ligature set, or {@code null}
	 */
	public LigatureSet getLigatureSetForGlyph(final int startGlyphId) {
		final int i = this.coverage.findGlyph(startGlyphId);
		return (i >= 0 && i < this.ligatureSets.length) ? this.ligatureSets[i] : null;
	}
}
