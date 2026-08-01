package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;

/**
 * An OpenType class definition table, mapping glyph ids to class values (used
 * by class-based GPOS/GSUB subtables). Glyphs not listed are class 0.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public interface ClassDef extends Serializable {

	/**
	 * Returns the class of the given glyph (0 if unlisted).
	 *
	 * @param glyphId the glyph id
	 * @return the class value
	 */
	int getClassValue(int glyphId);

	/**
	 * Reads a class definition table at the current file position.
	 *
	 * @param raf the file
	 * @return the class definition, or {@code null} for an unknown format
	 * @throws IOException if an I/O error occurs
	 */
	static ClassDef read(final RandomAccessFile raf) throws IOException {
		final int format = raf.readUnsignedShort();
		return switch (format) {
			case 1 -> Format1.read(raf);
			case 2 -> Format2.read(raf);
			default -> null;
		};
	}

	/** Format 1: a contiguous range of glyphs with per-glyph class values. */
	record Format1(int startGlyph, int[] classValues) implements ClassDef {
		static Format1 read(final RandomAccessFile raf) throws IOException {
			final int startGlyph = raf.readUnsignedShort();
			final int count = raf.readUnsignedShort();
			final int[] values = new int[count];
			for (int i = 0; i < count; i++) {
				values[i] = raf.readUnsignedShort();
			}
			return new Format1(startGlyph, values);
		}

		@Override
		public int getClassValue(final int glyphId) {
			final int i = glyphId - this.startGlyph;
			return (i >= 0 && i < this.classValues.length) ? this.classValues[i] : 0;
		}
	}

	/** Format 2: glyph ranges each assigned a class value. */
	record Format2(int[] starts, int[] ends, int[] classes, boolean sorted) implements ClassDef {
		static Format2 read(final RandomAccessFile raf) throws IOException {
			final int count = raf.readUnsignedShort();
			final int[] starts = new int[count];
			final int[] ends = new int[count];
			final int[] classes = new int[count];
			boolean sorted = true;
			for (int i = 0; i < count; i++) {
				starts[i] = raf.readUnsignedShort();
				ends[i] = raf.readUnsignedShort();
				classes[i] = raf.readUnsignedShort();
				if (i > 0 && starts[i - 1] >= starts[i]) {
					// 仕様(開始GID昇順)に反するフォント——線形走査へ縮退
					sorted = false;
				}
			}
			return new Format2(starts, ends, classes, sorted);
		}

		@Override
		public int getClassValue(final int glyphId) {
			// グリフ対毎に呼ばれる。仕様どおり整列済みなら二分探索
			// (2026-08-01、95点計画増分4)
			if (this.sorted) {
				int low = 0, high = this.starts.length - 1;
				while (low <= high) {
					final int mid = (low + high) >>> 1;
					if (this.starts[mid] <= glyphId) {
						low = mid + 1;
					} else {
						high = mid - 1;
					}
				}
				return (low > 0 && glyphId <= this.ends[low - 1]) ? this.classes[low - 1] : 0;
			}
			for (int i = 0; i < this.starts.length; i++) {
				if (glyphId >= this.starts[i] && glyphId <= this.ends[i]) {
					return this.classes[i];
				}
			}
			return 0;
		}
	}
}
