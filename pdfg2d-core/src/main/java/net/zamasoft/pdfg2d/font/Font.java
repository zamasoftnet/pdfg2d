package net.zamasoft.pdfg2d.font;

import java.io.IOException;
import java.io.Serializable;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * Represents a font.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface Font extends Serializable {
	/**
	 * Returns the font source.
	 * 
	 * @return the font source
	 */
	public FontSource getFontSource();

	/**
	 * Converts a character to a glyph ID (GID).
	 *
	 * @param c the character to convert
	 * @return the glyph ID
	 */
	public int toGID(int c);

	/**
	 * Converts a character to a glyph ID with OpenType feature settings
	 * applied (e.g. GSUB single substitutions for {@code jp78}, {@code pwid}).
	 * The default ignores the features so existing implementations keep their
	 * behaviour.
	 *
	 * @param c        the character to convert
	 * @param features the feature settings (never {@code null})
	 * @return the glyph ID
	 * @since 1.3
	 */
	public default int toGID(final int c, final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		return this.toGID(c);
	}

	/**
	 * Returns the advance width of the glyph.
	 * 
	 * @param gid the glyph ID
	 * @return the advance width
	 */
	public short getAdvance(int gid);

	/**
	 * Returns the width of the glyph.
	 * 
	 * @param gid the glyph ID
	 * @return the width
	 */
	public short getWidth(int gid);

	/**
	 * Returns the kerning value between two glyphs.
	 * 
	 * @param sgid the previous glyph ID
	 * @param gid  the current glyph ID
	 * @return the kerning value
	 */
	public short getKerning(int sgid, int gid);

	/**
	 * Returns the ligature for a sequence of glyphs.
	 * Returns 0 if no ligature exists.
	 * 
	 * @param gid the glyph ID
	 * @param cid the character ID
	 * @return the ligature glyph ID, or 0
	 */
	public int getLigature(int gid, int cid);

	/**
	 * Draws the text run to the graphics context.
	 * 
	 * @param gc   the graphics context
	 * @param text the text to draw
	 * @throws IOException       if an I/O error occurs
	 * @throws GraphicsException if a graphics error occurs
	 */
	public void drawTo(GC gc, Text text) throws IOException, GraphicsException;
}
