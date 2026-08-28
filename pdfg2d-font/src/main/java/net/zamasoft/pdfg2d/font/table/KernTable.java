package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code kern} (Kerning) table.
 * <p>
 * Contains one or more kerning subtables ({@link KernSubtable}) that provide
 * pairwise kerning adjustments.  Format 0 subtables ({@link KernSubtableFormat0})
 * list specific glyph pairs; format 2 subtables ({@link KernSubtableFormat2})
 * use class-based kerning.
 * </p>
 *
 * @param tables the array of kerning subtables
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record KernTable(KernSubtable[] tables) implements Table {

	protected KernTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private static KernSubtable[] readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			raf.readUnsignedShort(); // version
			final int nTables = raf.readUnsignedShort();
			final KernSubtable[] tables = new KernSubtable[nTables];
			for (int i = 0; i < nTables; i++) {
				tables[i] = KernSubtable.read(raf);
			}
			return tables;
		}
	}

	/**
	 * Returns the number of kerning subtables.
	 *
	 * @return the subtable count
	 */
	public int getSubtableCount() {
		return this.tables.length;
	}

	/**
	 * Returns the kerning subtable at the given index.
	 *
	 * @param i the zero-based index
	 * @return the {@link KernSubtable}
	 */
	public KernSubtable getSubtable(final int i) {
		return this.tables[i];
	}

	/**
	 * Collects the horizontal pair kerning of this legacy {@code kern} table as
	 * {@link PairPos} lookups — the same shape as
	 * {@link GposTable#collectKernPairPos()}, so callers can treat both sources
	 * uniformly. Only format 0 subtables with horizontal coverage contribute
	 * (format 2 is a non-providing stub).
	 *
	 * @return the pair-positioning lookups (possibly empty)
	 */
	public java.util.List<PairPos> collectHorizontalPairPos() {
		final java.util.List<PairPos> result = new java.util.ArrayList<>();
		for (final KernSubtable st : this.tables) {
			if (st == null || !st.isHorizontal()) {
				continue;
			}
			final int n = st.getKerningPairCount();
			if (n == 0) {
				continue;
			}
			// (left<<32 | right<<16 | value) に詰めて整列し、上位48bitの
			// 二分探索で引く(値のbitは同一ペア内の順序にしか影響しない)
			final long[] entries = new long[n];
			for (int i = 0; i < n; i++) {
				final KerningPair p = st.getKerningPair(i);
				entries[i] = ((long) (p.left() & 0xFFFF) << 32) | ((long) (p.right() & 0xFFFF) << 16)
						| (p.value() & 0xFFFFL);
			}
			java.util.Arrays.sort(entries);
			result.add(new LegacyPairPos(entries));
		}
		return result;
	}

	/** 整列済みエントリ配列に対する二分探索の{@link PairPos}アダプタ。 */
	private record LegacyPairPos(long[] entries) implements PairPos {
		@Override
		public int getKerning(final int firstGid, final int secondGid) {
			if (firstGid < 0 || firstGid > 0xFFFF || secondGid < 0 || secondGid > 0xFFFF) {
				return 0;
			}
			final long pairKey = ((long) firstGid << 32) | ((long) secondGid << 16);
			int lo = java.util.Arrays.binarySearch(this.entries, pairKey);
			if (lo < 0) {
				lo = -lo - 1;
			}
			if (lo < this.entries.length && (this.entries[lo] >>> 16) == (pairKey >>> 16)) {
				return (short) this.entries[lo];
			}
			return 0;
		}
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return KERN;
	}
}
