package net.zamasoft.pdfg2d.font;

import java.io.Serializable;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;

/**
 * Represents a source of a font, such as a TTF file or a system font.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface FontSource extends Serializable {
	/**
	 * Returns the font name.
	 * 
	 * @return the font name
	 */
	public String getFontName();

	/**
	 * Returns the aliases of the font.
	 * 
	 * @return an array of aliases
	 */
	public String[] getAliases();

	/**
	 * Returns the direction of the font.
	 * 
	 * @return the direction
	 */
	public Direction getDirection();

	/**
	 * Returns whether the font is italic.
	 * 
	 * @return true if italic, false otherwise
	 */
	public boolean isItalic();

	/**
	 * Returns the weight of the font.
	 * 
	 * @return the font weight
	 */
	public Weight getWeight();

	/** OS/2 {@code usWidthClass}の通常幅(medium)。 */
	public static final int NORMAL_WIDTH_CLASS = 5;

	/**
	 * 幅級(OpenType OS/2 {@code usWidthClass}、1=ultra-condensed〜
	 * 9=ultra-expanded、5=normal)を返します(2026-08-29)。CSSの
	 * {@code font-stretch}による書体選択に使う。OS/2表を持たない
	 * ソース(Type1/CFF単体・AWT経由)は通常幅とみなす。
	 *
	 * @return 幅級 1..9
	 */
	public default int getWidthClass() {
		return NORMAL_WIDTH_CLASS;
	}

	/**
	 * The default units per em. CFF output is based on this unit count.
	 */
	public static final short DEFAULT_UNITS_PER_EM = 1000;

	/**
	 * Returns whether the character can be displayed.
	 * 
	 * @param c the character to check
	 * @return true if displayable, false otherwise
	 */
	public boolean canDisplay(int c);

	/**
	 * Returns the bounding box of the font.
	 * 
	 * @return the bounding box
	 */
	public BBox getBBox();

	/**
	 * Returns the ascent of the font (height above baseline).
	 * 
	 * @return the ascent
	 */
	public short getAscent();

	/**
	 * Returns the cap height of the font (height of uppercase letters).
	 * 
	 * @return the cap height
	 */
	public short getCapHeight();

	/**
	 * Returns the descent of the font (height below baseline).
	 * 
	 * @return the descent
	 */
	public short getDescent();

	/**
	 * Returns the horizontal stem width.
	 * 
	 * @return the horizontal stem width
	 */
	public short getStemH();

	/**
	 * Returns the vertical stem width.
	 * 
	 * @return the vertical stem width
	 */
	public short getStemV();

	/**
	 * Returns the x-height of the font (height of lowercase 'x').
	 * 
	 * @return the x-height
	 */
	public short getXHeight();

	/**
	 * Returns the advance width of a space character.
	 * 
	 * @return the space advance width
	 */
	public short getSpaceAdvance();

	/**
	 * Returns the OpenType OS/2 {@code fsType} embedding permission flags.
	 *
	 * <p>Font sources without an OS/2 table return {@code 0}. Consumers that
	 * redistribute a generated font must still obey the font's license; this
	 * value only exposes the machine-readable embedding restrictions.</p>
	 *
	 * @return the unsigned 16-bit flags stored in a {@code short}
	 */
	public default short getEmbeddingLicenseFlags() {
		return 0;
	}

	/**
	 * Creates a font instance from this source.
	 * 
	 * @return the created font
	 */
	public Font createFont();
}
