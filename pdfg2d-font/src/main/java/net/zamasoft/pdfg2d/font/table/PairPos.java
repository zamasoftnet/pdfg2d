package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A GPOS pair adjustment (lookup type 2) subtable, restricted to the horizontal
 * advance of the first glyph — the field that expresses pair kerning. Other
 * value-record fields are parsed only enough to be skipped.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public interface PairPos extends LookupSubtable {

	int VALUE_X_ADVANCE = 0x0004;

	/**
	 * Returns the horizontal kerning applied between the two glyphs (in font
	 * design units), or 0 if the pair is not kerned.
	 *
	 * @param firstGid  the first glyph
	 * @param secondGid the second glyph
	 * @return the x-advance adjustment of the first glyph
	 */
	int getKerning(int firstGid, int secondGid);

	/** Counts the set value-record fields (each a 2-byte value or offset). */
	private static int valueSize(final int valueFormat) {
		return Integer.bitCount(valueFormat & 0xFFFF) * 2;
	}

	/** Reads a value record, returning its x-advance and consuming the rest. */
	private static int readXAdvance(final RandomAccessFile raf, final int valueFormat) throws IOException {
		int xAdvance = 0;
		// Fields appear in flag order: XPlacement, YPlacement, XAdvance, ...
		if ((valueFormat & 0x0001) != 0) {
			raf.readShort();
		}
		if ((valueFormat & 0x0002) != 0) {
			raf.readShort();
		}
		if ((valueFormat & VALUE_X_ADVANCE) != 0) {
			xAdvance = raf.readShort();
		}
		// Remaining fields (YAdvance and the four device offsets) are skipped.
		for (int flag = 0x0008; flag <= 0x0080; flag <<= 1) {
			if ((valueFormat & flag) != 0) {
				raf.readShort();
			}
		}
		return xAdvance;
	}

	/**
	 * Reads a PairPos subtable at {@code offset}.
	 *
	 * @param raf    the file
	 * @param offset the subtable offset
	 * @return the subtable, or {@code null} for an unsupported format
	 * @throws IOException if an I/O error occurs
	 */
	static PairPos read(final RandomAccessFile raf, final int offset) throws IOException {
		synchronized (raf) {
			raf.seek(offset);
			final int format = raf.readUnsignedShort();
			return switch (format) {
				case 1 -> Format1.read(raf, offset);
				case 2 -> Format2.read(raf, offset);
				default -> null;
			};
		}
	}

	/** Format 1: explicit per-pair kerning grouped by the first glyph. */
	record Format1(Coverage coverage, int[][] secondGlyphs, int[][] kernings, boolean sorted) implements PairPos {
		static Format1 read(final RandomAccessFile raf, final int offset) throws IOException {
			final int coverageOffset = raf.readUnsignedShort();
			final int valueFormat1 = raf.readUnsignedShort();
			final int valueFormat2 = raf.readUnsignedShort();
			final int pairSetCount = raf.readUnsignedShort();
			final int[] pairSetOffsets = new int[pairSetCount];
			for (int i = 0; i < pairSetCount; i++) {
				pairSetOffsets[i] = raf.readUnsignedShort();
			}
			raf.seek(offset + coverageOffset);
			final Coverage coverage = Coverage.read(raf);

			final int[][] seconds = new int[pairSetCount][];
			final int[][] kerns = new int[pairSetCount][];
			boolean sorted = true;
			for (int i = 0; i < pairSetCount; i++) {
				raf.seek(offset + pairSetOffsets[i]);
				final int pairCount = raf.readUnsignedShort();
				seconds[i] = new int[pairCount];
				kerns[i] = new int[pairCount];
				for (int j = 0; j < pairCount; j++) {
					seconds[i][j] = raf.readUnsignedShort();
					if (j > 0 && seconds[i][j - 1] >= seconds[i][j]) {
						// 仕様(第二GID昇順)に反するフォント——線形走査へ縮退
						sorted = false;
					}
					kerns[i][j] = readXAdvance(raf, valueFormat1);
					// Consume the second glyph's value record.
					for (int k = 0, bits = valueSize(valueFormat2) / 2; k < bits; k++) {
						raf.readShort();
					}
				}
			}
			return new Format1(coverage, seconds, kerns, sorted);
		}

		@Override
		public int getKerning(final int firstGid, final int secondGid) {
			final int ci = this.coverage.findGlyph(firstGid);
			if (ci < 0 || ci >= this.secondGlyphs.length) {
				return 0;
			}
			final int[] seconds = this.secondGlyphs[ci];
			// グリフ対毎に呼ばれる。仕様どおり整列済みなら二分探索
			// (2026-08-01、95点計画増分4)
			if (this.sorted) {
				final int j = java.util.Arrays.binarySearch(seconds, secondGid);
				return j < 0 ? 0 : this.kernings[ci][j];
			}
			for (int j = 0; j < seconds.length; j++) {
				if (seconds[j] == secondGid) {
					return this.kernings[ci][j];
				}
			}
			return 0;
		}
	}

	/** Format 2: kerning by glyph classes. */
	record Format2(Coverage coverage, ClassDef classDef1, ClassDef classDef2, int class2Count, int[][] kernings)
			implements PairPos {
		static Format2 read(final RandomAccessFile raf, final int offset) throws IOException {
			final int coverageOffset = raf.readUnsignedShort();
			final int valueFormat1 = raf.readUnsignedShort();
			final int valueFormat2 = raf.readUnsignedShort();
			final int classDef1Offset = raf.readUnsignedShort();
			final int classDef2Offset = raf.readUnsignedShort();
			final int class1Count = raf.readUnsignedShort();
			final int class2Count = raf.readUnsignedShort();

			final int[][] kerns = new int[class1Count][class2Count];
			for (int c1 = 0; c1 < class1Count; c1++) {
				for (int c2 = 0; c2 < class2Count; c2++) {
					kerns[c1][c2] = readXAdvance(raf, valueFormat1);
					for (int k = 0, bits = valueSize(valueFormat2) / 2; k < bits; k++) {
						raf.readShort();
					}
				}
			}
			raf.seek(offset + coverageOffset);
			final Coverage coverage = Coverage.read(raf);
			raf.seek(offset + classDef1Offset);
			final ClassDef classDef1 = ClassDef.read(raf);
			raf.seek(offset + classDef2Offset);
			final ClassDef classDef2 = ClassDef.read(raf);
			return new Format2(coverage, classDef1, classDef2, class2Count, kerns);
		}

		@Override
		public int getKerning(final int firstGid, final int secondGid) {
			if (this.coverage.findGlyph(firstGid) < 0) {
				return 0;
			}
			final int c1 = this.classDef1.getClassValue(firstGid);
			final int c2 = this.classDef2.getClassValue(secondGid);
			if (c1 < this.kernings.length && c2 < this.class2Count) {
				return this.kernings[c1][c2];
			}
			return 0;
		}
	}
}
