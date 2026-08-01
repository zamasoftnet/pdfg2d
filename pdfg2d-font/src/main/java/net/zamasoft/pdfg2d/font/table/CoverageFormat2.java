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
public record CoverageFormat2(RangeRecord[] rangeRecords, boolean sorted) implements Coverage {
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
		boolean sorted = true;
		for (int i = 0; i < rangeCount; i++) {
			rangeRecords[i] = RangeRecord.read(raf);
			if (i > 0 && rangeRecords[i - 1].start() >= rangeRecords[i].start()) {
				// 仕様(開始GID昇順)に反するフォント——線形走査へ縮退
				sorted = false;
			}
		}
		return new CoverageFormat2(rangeRecords, sorted);
	}

	@Override
	public int getFormat() {
		return 2;
	}

	@Override
	public int findGlyph(final int glyphId) {
		// 整形中グリフ毎に呼ばれる。仕様どおり整列済みなら二分探索
		// (2026-08-01、95点計画増分2)
		if (this.sorted) {
			int low = 0, high = this.rangeRecords.length - 1;
			while (low <= high) {
				final int mid = (low + high) >>> 1;
				if (this.rangeRecords[mid].start() <= glyphId) {
					low = mid + 1;
				} else {
					high = mid - 1;
				}
			}
			return low == 0 ? -1 : this.rangeRecords[low - 1].getCoverageIndex(glyphId);
		}
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
