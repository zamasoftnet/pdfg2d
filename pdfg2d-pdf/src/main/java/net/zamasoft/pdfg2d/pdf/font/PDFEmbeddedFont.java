package net.zamasoft.pdfg2d.pdf.font;

import java.awt.Shape;

import net.zamasoft.pdfg2d.font.BBox;

/**
 * A {@link PDFFont} that carries embedded font data within the PDF document.
 * Provides access to PostScript font metadata (PS name, bounding box, CIDSystemInfo)
 * as well as per-glyph outlines and CharString data used when writing the embedded
 * font program into a PDF stream.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface PDFEmbeddedFont extends PDFFont {
	/**
	 * Returns the PostScript name of the font.
	 *
	 * @return the PostScript font name
	 */
	public String getPSName();

	/**
	 * Returns the font bounding box that encloses all glyphs.
	 *
	 * @return the font bounding box in glyph-space units
	 */
	public BBox getBBox();

	/**
	 * Returns the CIDSystemInfo Registry string (e.g. {@code "Adobe"}).
	 *
	 * @return the registry name
	 */
	public String getRegistry();

	/**
	 * Returns the CIDSystemInfo Ordering string (e.g. {@code "Identity"}).
	 *
	 * @return the ordering name
	 */
	public String getOrdering();

	/**
	 * Returns the CIDSystemInfo Supplement number.
	 *
	 * @return the supplement value
	 */
	public int getSupplement();

	/**
	 * Returns the outline {@link Shape} for the glyph at the given index.
	 *
	 * @param i the glyph index
	 * @return the glyph outline shape, or {@code null} if not available
	 */
	public Shape getShape(int i);

	/**
	 * Returns the raw CharString byte data for the glyph at the given index.
	 * CharStrings encode glyph outlines in the Type 2 charstring format used by
	 * CFF/Type 1 fonts.
	 *
	 * @param i the glyph index
	 * @return the CharString bytes, or {@code null} if not available
	 */
	public byte[] getCharString(int i);

	/**
	 * Returns the total number of glyphs in this embedded font.
	 *
	 * @return the glyph count
	 */
	public int getGlyphCount();

	/**
	 * Returns the number of character codes covered by this embedded font.
	 *
	 * @return the character count
	 */
	public int getCharCount();
}
