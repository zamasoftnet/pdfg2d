package net.zamasoft.pdfg2d.font.table;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * OpenType {@code OS/2} and Windows Metrics table.
 * <p>
 * Contains a collection of metrics, code-page ranges, Unicode ranges, and
 * other data required for correct rendering on Windows and OS/2 platforms.
 * All record components correspond directly to the fields defined in the
 * OpenType specification for the {@code OS/2} table.
 * </p>
 *
 * @param version             table version number
 * @param xAvgCharWidth       arithmetic mean of the advance widths of all non-zero width glyphs
 * @param usWeightClass       visual weight (degree of blackness or thickness of strokes)
 * @param usWidthClass        relative change from the normal aspect ratio
 * @param fsType              type embedding licensing bits
 * @param ySubscriptXSize     recommended subscript horizontal glyph size in font units
 * @param ySubscriptYSize     recommended subscript vertical glyph size in font units
 * @param ySubscriptXOffset   recommended subscript horizontal glyph offset in font units
 * @param ySubscriptYOffset   recommended subscript vertical glyph offset (downward) in font units
 * @param ySuperscriptXSize   recommended superscript horizontal glyph size in font units
 * @param ySuperscriptYSize   recommended superscript vertical glyph size in font units
 * @param ySuperscriptXOffset recommended superscript horizontal glyph offset in font units
 * @param ySuperscriptYOffset recommended superscript vertical glyph offset (upward) in font units
 * @param yStrikeoutSize      width of the strikeout stroke in font units
 * @param yStrikeoutPosition  position of the top of the strikeout stroke in font units
 * @param sFamilyClass        classification of the font-face design (IBM class and subclass)
 * @param panose              10-byte PANOSE classification number
 * @param ulUnicodeRange1     Unicode character range bitmask 1 (bits 0–31)
 * @param ulUnicodeRange2     Unicode character range bitmask 2 (bits 32–63)
 * @param ulUnicodeRange3     Unicode character range bitmask 3 (bits 64–95)
 * @param ulUnicodeRange4     Unicode character range bitmask 4 (bits 96–127)
 * @param achVendorID         four-character identifier for the font vendor
 * @param fsSelection         font selection flags (bold, italic, etc.)
 * @param usFirstCharIndex    minimum Unicode code point covered by this font
 * @param usLastCharIndex     maximum Unicode code point covered by this font
 * @param sTypoAscender       typographic ascender in font units
 * @param sTypoDescender      typographic descender in font units (negative value)
 * @param sTypoLineGap        typographic line gap in font units
 * @param usWinAscent         Windows ascender metric in font units
 * @param usWinDescent        Windows descender metric in font units (positive value)
 * @param ulCodePageRange1    code-page character range bitmask 1 (bits 0–31)
 * @param ulCodePageRange2    code-page character range bitmask 2 (bits 32–63)
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record Os2Table(
		int version,
		short xAvgCharWidth,
		int usWeightClass,
		int usWidthClass,
		short fsType,
		short ySubscriptXSize,
		short ySubscriptYSize,
		short ySubscriptXOffset,
		short ySubscriptYOffset,
		short ySuperscriptXSize,
		short ySuperscriptYSize,
		short ySuperscriptXOffset,
		short ySuperscriptYOffset,
		short yStrikeoutSize,
		short yStrikeoutPosition,
		short sFamilyClass,
		Panose panose,
		int ulUnicodeRange1,
		int ulUnicodeRange2,
		int ulUnicodeRange3,
		int ulUnicodeRange4,
		int achVendorID,
		short fsSelection,
		int usFirstCharIndex,
		int usLastCharIndex,
		short sTypoAscender,
		short sTypoDescender,
		short sTypoLineGap,
		int usWinAscent,
		int usWinDescent,
		int ulCodePageRange1,
		int ulCodePageRange2) implements Table {

	protected Os2Table(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		this(readData(de, raf));
	}

	private Os2Table(Os2Table other) {
		this(
				other.version,
				other.xAvgCharWidth,
				other.usWeightClass,
				other.usWidthClass,
				other.fsType,
				other.ySubscriptXSize,
				other.ySubscriptYSize,
				other.ySubscriptXOffset,
				other.ySubscriptYOffset,
				other.ySuperscriptXSize,
				other.ySuperscriptYSize,
				other.ySuperscriptXOffset,
				other.ySuperscriptYOffset,
				other.yStrikeoutSize,
				other.yStrikeoutPosition,
				other.sFamilyClass,
				other.panose,
				other.ulUnicodeRange1,
				other.ulUnicodeRange2,
				other.ulUnicodeRange3,
				other.ulUnicodeRange4,
				other.achVendorID,
				other.fsSelection,
				other.usFirstCharIndex,
				other.usLastCharIndex,
				other.sTypoAscender,
				other.sTypoDescender,
				other.sTypoLineGap,
				other.usWinAscent,
				other.usWinDescent,
				other.ulCodePageRange1,
				other.ulCodePageRange2);
	}

	private static Os2Table readData(final DirectoryEntry de, final RandomAccessFile raf) throws IOException {
		synchronized (raf) {
			raf.seek(de.offset());
			final int version = raf.readUnsignedShort();
			final short xAvgCharWidth = raf.readShort();
			final int usWeightClass = raf.readUnsignedShort();
			final int usWidthClass = raf.readUnsignedShort();
			final short fsType = raf.readShort();
			final short ySubscriptXSize = raf.readShort();
			final short ySubscriptYSize = raf.readShort();
			final short ySubscriptXOffset = raf.readShort();
			final short ySubscriptYOffset = raf.readShort();
			final short ySuperscriptXSize = raf.readShort();
			final short ySuperscriptYSize = raf.readShort();
			final short ySuperscriptXOffset = raf.readShort();
			final short ySuperscriptYOffset = raf.readShort();
			final short yStrikeoutSize = raf.readShort();
			final short yStrikeoutPosition = raf.readShort();
			final short sFamilyClass = raf.readShort();
			final byte[] buf = new byte[10];
			raf.read(buf);
			final Panose panose = new Panose(buf);
			final int ulUnicodeRange1 = raf.readInt();
			final int ulUnicodeRange2 = raf.readInt();
			final int ulUnicodeRange3 = raf.readInt();
			final int ulUnicodeRange4 = raf.readInt();
			final int achVendorID = raf.readInt();
			final short fsSelection = raf.readShort();
			final int usFirstCharIndex = raf.readUnsignedShort();
			final int usLastCharIndex = raf.readUnsignedShort();
			final short sTypoAscender = raf.readShort();
			final short sTypoDescender = raf.readShort();
			final short sTypoLineGap = raf.readShort();
			final int usWinAscent = raf.readUnsignedShort();
			final int usWinDescent = raf.readUnsignedShort();
			final int ulCodePageRange1 = raf.readInt();
			final int ulCodePageRange2 = raf.readInt();
			return new Os2Table(
					version,
					xAvgCharWidth,
					usWeightClass,
					usWidthClass,
					fsType,
					ySubscriptXSize,
					ySubscriptYSize,
					ySubscriptXOffset,
					ySubscriptYOffset,
					ySuperscriptXSize,
					ySuperscriptYSize,
					ySuperscriptXOffset,
					ySuperscriptYOffset,
					yStrikeoutSize,
					yStrikeoutPosition,
					sFamilyClass,
					panose,
					ulUnicodeRange1,
					ulUnicodeRange2,
					ulUnicodeRange3,
					ulUnicodeRange4,
					achVendorID,
					fsSelection,
					usFirstCharIndex,
					usLastCharIndex,
					sTypoAscender,
					sTypoDescender,
					sTypoLineGap,
					usWinAscent,
					usWinDescent,
					ulCodePageRange1,
					ulCodePageRange2);
		}
	}

	/**
	 * Returns the table version number.
	 *
	 * @return the version number
	 */
	public int getVersion() {
		return this.version;
	}

	/**
	 * Returns the arithmetic mean of the advance widths of all non-zero width glyphs.
	 *
	 * @return average character width in font units
	 */
	public short getAvgCharWidth() {
		return this.xAvgCharWidth;
	}

	/**
	 * Returns the visual weight (degree of blackness) of the font.
	 *
	 * @return weight class value (100 = Thin … 900 = Black)
	 */
	public int getWeightClass() {
		return this.usWeightClass;
	}

	/**
	 * Returns the relative change from normal aspect ratio.
	 *
	 * @return width class value (1 = Ultra-condensed … 9 = Ultra-expanded)
	 */
	public int getWidthClass() {
		return this.usWidthClass;
	}

	/**
	 * Returns the type embedding licensing bits.
	 *
	 * @return the {@code fsType} flags
	 */
	public short getLicenseType() {
		return this.fsType;
	}

	/**
	 * Returns the recommended subscript horizontal glyph size.
	 *
	 * @return subscript x size in font units
	 */
	public short getSubscriptXSize() {
		return this.ySubscriptXSize;
	}

	/**
	 * Returns the recommended subscript vertical glyph size.
	 *
	 * @return subscript y size in font units
	 */
	public short getSubscriptYSize() {
		return this.ySubscriptYSize;
	}

	/**
	 * Returns the recommended subscript horizontal glyph offset.
	 *
	 * @return subscript x offset in font units
	 */
	public short getSubscriptXOffset() {
		return this.ySubscriptXOffset;
	}

	/**
	 * Returns the recommended subscript vertical glyph offset (downward).
	 *
	 * @return subscript y offset in font units
	 */
	public short getSubscriptYOffset() {
		return this.ySubscriptYOffset;
	}

	/**
	 * Returns the recommended superscript horizontal glyph size.
	 *
	 * @return superscript x size in font units
	 */
	public short getSuperscriptXSize() {
		return this.ySuperscriptXSize;
	}

	/**
	 * Returns the recommended superscript vertical glyph size.
	 *
	 * @return superscript y size in font units
	 */
	public short getSuperscriptYSize() {
		return this.ySuperscriptYSize;
	}

	/**
	 * Returns the recommended superscript horizontal glyph offset.
	 *
	 * @return superscript x offset in font units
	 */
	public short getSuperscriptXOffset() {
		return this.ySuperscriptXOffset;
	}

	/**
	 * Returns the recommended superscript vertical glyph offset (upward).
	 *
	 * @return superscript y offset in font units
	 */
	public short getSuperscriptYOffset() {
		return this.ySuperscriptYOffset;
	}

	/**
	 * Returns the width of the strikeout stroke.
	 *
	 * @return strikeout size in font units
	 */
	public short getStrikeoutSize() {
		return this.yStrikeoutSize;
	}

	/**
	 * Returns the position of the top of the strikeout stroke relative to the baseline.
	 *
	 * @return strikeout position in font units
	 */
	public short getStrikeoutPosition() {
		return this.yStrikeoutPosition;
	}

	/**
	 * Returns the IBM font family class and subclass.
	 *
	 * @return the {@code sFamilyClass} value
	 */
	public short getFamilyClass() {
		return this.sFamilyClass;
	}

	/**
	 * Returns the 10-byte PANOSE classification number.
	 *
	 * @return the {@link Panose} record
	 */
	public Panose getPanose() {
		return this.panose;
	}

	/**
	 * Returns the first Unicode character range bitmask (bits 0–31).
	 *
	 * @return {@code ulUnicodeRange1}
	 */
	public int getUnicodeRange1() {
		return this.ulUnicodeRange1;
	}

	/**
	 * Returns the second Unicode character range bitmask (bits 32–63).
	 *
	 * @return {@code ulUnicodeRange2}
	 */
	public int getUnicodeRange2() {
		return this.ulUnicodeRange2;
	}

	/**
	 * Returns the third Unicode character range bitmask (bits 64–95).
	 *
	 * @return {@code ulUnicodeRange3}
	 */
	public int getUnicodeRange3() {
		return this.ulUnicodeRange3;
	}

	/**
	 * Returns the fourth Unicode character range bitmask (bits 96–127).
	 *
	 * @return {@code ulUnicodeRange4}
	 */
	public int getUnicodeRange4() {
		return this.ulUnicodeRange4;
	}

	/**
	 * Returns the four-character vendor identifier.
	 *
	 * @return the {@code achVendID} value
	 */
	public int getVendorID() {
		return this.achVendorID;
	}

	/**
	 * Returns the font selection flags (bold, italic, etc.).
	 *
	 * @return the {@code fsSelection} flags
	 */
	public short getSelection() {
		return this.fsSelection;
	}

	/**
	 * Returns the minimum Unicode code point covered by this font.
	 *
	 * @return the first character index
	 */
	public int getFirstCharIndex() {
		return this.usFirstCharIndex;
	}

	/**
	 * Returns the maximum Unicode code point covered by this font.
	 *
	 * @return the last character index
	 */
	public int getLastCharIndex() {
		return this.usLastCharIndex;
	}

	/**
	 * Returns the typographic ascender in font units.
	 *
	 * @return typographic ascender
	 */
	public short getTypoAscender() {
		return this.sTypoAscender;
	}

	/**
	 * Returns the typographic descender in font units (negative value).
	 *
	 * @return typographic descender
	 */
	public short getTypoDescender() {
		return this.sTypoDescender;
	}

	/**
	 * Returns the typographic line gap in font units.
	 *
	 * @return typographic line gap
	 */
	public short getTypoLineGap() {
		return this.sTypoLineGap;
	}

	/**
	 * Returns the Windows ascender metric in font units.
	 *
	 * @return Windows ascender
	 */
	public int getWinAscent() {
		return this.usWinAscent;
	}

	/**
	 * Returns the Windows descender metric in font units (positive value).
	 *
	 * @return Windows descender
	 */
	public int getWinDescent() {
		return this.usWinDescent;
	}

	/**
	 * Returns the first code-page character range bitmask (bits 0–31).
	 *
	 * @return {@code ulCodePageRange1}
	 */
	public int getCodePageRange1() {
		return this.ulCodePageRange1;
	}

	/**
	 * Returns the second code-page character range bitmask (bits 32–63).
	 *
	 * @return {@code ulCodePageRange2}
	 */
	public int getCodePageRange2() {
		return this.ulCodePageRange2;
	}

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return OS_2;
	}
}
