package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A GPOS single adjustment (lookup type 1) subtable — per-glyph position and
 * advance adjustments, used by features such as {@code palt}/{@code vpal}
 * (proportional alternate metrics) and {@code halt}/{@code vhal}. All four
 * scalar value-record fields are retained; device/variation offsets are
 * parsed only enough to be skipped.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public interface SinglePos extends LookupSubtable {

	/**
	 * A glyph's position adjustment in font design units.
	 *
	 * @param xPlacement the x offset of the glyph shape (does not move the pen)
	 * @param yPlacement the y offset of the glyph shape
	 * @param xAdvance   the pen x-advance adjustment
	 * @param yAdvance   the pen y-advance adjustment
	 */
	record GlyphPosition(short xPlacement, short yPlacement, short xAdvance, short yAdvance) {
	}

	/**
	 * Returns the adjustment for the glyph, or {@code null} when the glyph is
	 * not covered by this subtable.
	 *
	 * @param gid the glyph
	 * @return the adjustment, or {@code null}
	 */
	GlyphPosition getPosition(int gid);

	/** Reads a value record's scalar fields and skips its device offsets. */
	private static GlyphPosition readValueRecord(final RandomAccessFile raf, final int valueFormat)
			throws IOException {
		short xPlacement = 0, yPlacement = 0, xAdvance = 0, yAdvance = 0;
		// Fields appear in flag order: XPlacement, YPlacement, XAdvance,
		// YAdvance, then the four device/variation offsets.
		if ((valueFormat & 0x0001) != 0) {
			xPlacement = raf.readShort();
		}
		if ((valueFormat & 0x0002) != 0) {
			yPlacement = raf.readShort();
		}
		if ((valueFormat & 0x0004) != 0) {
			xAdvance = raf.readShort();
		}
		if ((valueFormat & 0x0008) != 0) {
			yAdvance = raf.readShort();
		}
		for (int flag = 0x0010; flag <= 0x0080; flag <<= 1) {
			if ((valueFormat & flag) != 0) {
				raf.readShort();
			}
		}
		return new GlyphPosition(xPlacement, yPlacement, xAdvance, yAdvance);
	}

	/**
	 * Reads a SinglePos subtable at {@code offset}.
	 *
	 * @param raf    the file
	 * @param offset the subtable offset
	 * @return the subtable, or {@code null} for an unsupported format
	 * @throws IOException if an I/O error occurs
	 */
	static SinglePos read(final RandomAccessFile raf, final int offset) throws IOException {
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

	/** Format 1: one shared adjustment for every covered glyph. */
	record Format1(Coverage coverage, GlyphPosition value) implements SinglePos {
		static Format1 read(final RandomAccessFile raf, final int offset) throws IOException {
			final int coverageOffset = raf.readUnsignedShort();
			final int valueFormat = raf.readUnsignedShort();
			final GlyphPosition value = readValueRecord(raf, valueFormat);
			raf.seek(offset + coverageOffset);
			final Coverage coverage = Coverage.read(raf);
			return new Format1(coverage, value);
		}

		@Override
		public GlyphPosition getPosition(final int gid) {
			return this.coverage.findGlyph(gid) >= 0 ? this.value : null;
		}
	}

	/** Format 2: one adjustment per covered glyph. */
	record Format2(Coverage coverage, GlyphPosition[] values) implements SinglePos {
		static Format2 read(final RandomAccessFile raf, final int offset) throws IOException {
			final int coverageOffset = raf.readUnsignedShort();
			final int valueFormat = raf.readUnsignedShort();
			final int valueCount = raf.readUnsignedShort();
			final GlyphPosition[] values = new GlyphPosition[valueCount];
			for (int i = 0; i < valueCount; i++) {
				values[i] = readValueRecord(raf, valueFormat);
			}
			raf.seek(offset + coverageOffset);
			final Coverage coverage = Coverage.read(raf);
			return new Format2(coverage, values);
		}

		@Override
		public GlyphPosition getPosition(final int gid) {
			final int ci = this.coverage.findGlyph(gid);
			return ci >= 0 && ci < this.values.length ? this.values[ci] : null;
		}
	}
}
