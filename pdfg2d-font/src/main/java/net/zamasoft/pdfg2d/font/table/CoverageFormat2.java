package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Coverage table format 2.
 * 
 * @param rangeRecords array of range records
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @since 1.0
 */
public record CoverageFormat2(RangeRecord[] rangeRecords) implements Coverage {
	private static final long serialVersionUID = 0L;

	/**
	 * Reads a CoverageFormat2 from the given file.
	 * 
	 * @param raf the file to read from
	 * @return a new CoverageFormat2 instance
	 * @throws IOException if an I/O error occurs
	 */
	protected static CoverageFormat2 read(final RandomAccessFile raf) throws IOException {
		final int rangeCount = raf.readUnsignedShort();
		final RangeRecord[] rangeRecords = new RangeRecord[rangeCount];
		for (int i = 0; i < rangeCount; i++) {
			rangeRecords[i] = RangeRecord.read(raf);
		}
		return new CoverageFormat2(rangeRecords);
	}

	@Override
	public int getFormat() {
		return 2;
	}

	@Override
	public int findGlyph(final int glyphId) {
		for (final RangeRecord rangeRecord : this.rangeRecords) {
			final int n = rangeRecord.getCoverageIndex(glyphId);
			if (n > -1) {
				return n;
			}
		}
		return -1;
	}

	@Override
	public int[] getGlyphIds() {
		int count = 0;
		for (final RangeRecord r : this.rangeRecords) {
			count += r.end() - r.start() + 1;
		}
		final int[] ids = new int[count];
		int k = 0;
		for (final RangeRecord r : this.rangeRecords) {
			for (int g = r.start(); g <= r.end(); g++) {
				ids[k++] = g;
			}
		}
		return ids;
	}
}
