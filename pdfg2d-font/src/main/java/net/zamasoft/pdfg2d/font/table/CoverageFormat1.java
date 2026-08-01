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
				// 仕様(数値順)に反するフォント——線形走査へ縮退
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
		// 整形中グリフ毎に呼ばれる。仕様どおり整列済みなら二分探索
		// (2026-08-01、95点計画増分2——CJKフォントのCoverageは数千グリフ)
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
