package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code head} (Font Header) table.
 * <p>
 * Contains global font properties such as the units-per-em value, font
 * bounding box, creation/modification timestamps, and the format indicator
 * used by the {@code loca} table ({@link #indexToLocFormat}).
 * </p>
 *
 * @param versionNumber      the table version number (Fixed)
 * @param fontRevision       the font revision number set by the font manufacturer (Fixed)
 * @param checkSumAdjustment adjustment value used to compute the checksum for the whole font file
 * @param magicNumber        magic number; must be {@code 0x5F0F3CF5}
 * @param flags              bit-field of font flags as defined by the OpenType specification
 * @param unitsPerEm         number of font design units per em; typically 1000 or 2048
 * @param created            number of seconds since 12:00 midnight on 1 January 1904 (creation time)
 * @param modified           number of seconds since 12:00 midnight on 1 January 1904 (modification time)
 * @param xMin               minimum X coordinate across all glyph bounding boxes
 * @param yMin               minimum Y coordinate across all glyph bounding boxes
 * @param xMax               maximum X coordinate across all glyph bounding boxes
 * @param yMax               maximum Y coordinate across all glyph bounding boxes
 * @param macStyle           bit-field indicating bold, italic, and other stylistic attributes
 * @param lowestRecPPEM      smallest readable size in pixels per em
 * @param fontDirectionHint  deprecated font direction hint (should be set to 2)
 * @param indexToLocFormat   format of the {@code loca} table: 0 = short offsets, 1 = long offsets
 * @param glyphDataFormat    format of the {@code glyf} table (0 for current version)
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record HeadTable(
		int versionNumber,
		int fontRevision,
		int checkSumAdjustment,
		int magicNumber,
		short flags,
		short unitsPerEm,
		long created,
		long modified,
		short xMin,
		short yMin,
		short xMax,
		short yMax,
		short macStyle,
		short lowestRecPPEM,
		short fontDirectionHint,
		short indexToLocFormat,
		short glyphDataFormat) implements Table {

	protected HeadTable(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private HeadTable(HeadTable other) {
		this(
				other.versionNumber,
				other.fontRevision,
				other.checkSumAdjustment,
				other.magicNumber,
				other.flags,
				other.unitsPerEm,
				other.created,
				other.modified,
				other.xMin,
				other.yMin,
				other.xMax,
				other.yMax,
				other.macStyle,
				other.lowestRecPPEM,
				other.fontDirectionHint,
				other.indexToLocFormat,
				other.glyphDataFormat);
	}

	private static HeadTable readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			return new HeadTable(
					raf.readInt(),
					raf.readInt(),
					raf.readInt(),
					raf.readInt(),
					raf.readShort(),
					raf.readShort(),
					raf.readLong(),
					raf.readLong(),
					raf.readShort(),
					raf.readShort(),
					raf.readShort(),
					raf.readShort(),
					raf.readShort(),
					raf.readShort(),
					raf.readShort(),
					raf.readShort(),
					raf.readShort());
		}
	}

	/**
	 * Returns the format of the {@code loca} table.
	 *
	 * @return {@code 0} for short (16-bit) offsets, {@code 1} for long (32-bit) offsets
	 */
	public short getIndexToLocFormat() {
		return this.indexToLocFormat;
	}

	/**
	 * Returns the number of font design units per em.
	 *
	 * @return units per em (typically 1000 or 2048)
	 */
	public short getUnitsPerEm() {
		return this.unitsPerEm;
	}

	/**
	 * Returns the maximum X coordinate across all glyph bounding boxes.
	 *
	 * @return xMax value in font design units
	 */
	public short getXMax() {
		return this.xMax;
	}

	/**
	 * Returns the minimum X coordinate across all glyph bounding boxes.
	 *
	 * @return xMin value in font design units
	 */
	public short getXMin() {
		return this.xMin;
	}

	/**
	 * Returns the maximum Y coordinate across all glyph bounding boxes.
	 *
	 * @return yMax value in font design units
	 */
	public short getYMax() {
		return this.yMax;
	}

	/**
	 * Returns the minimum Y coordinate across all glyph bounding boxes.
	 *
	 * @return yMin value in font design units
	 */
	public short getYMin() {
		return this.yMin;
	}

	/**
	 * Returns the Mac style bit-field for this font.
	 * <p>
	 * Bit 0: bold; bit 1: italic; bit 2: underline; bit 3: outline;
	 * bit 4: shadow; bit 5: condensed; bit 6: extended.
	 * </p>
	 *
	 * @return the macStyle flags
	 */
	public short getMacStyle() {
		return this.macStyle;
	}

	@Override
	public int getType() {
		return HEAD;
	}
}
