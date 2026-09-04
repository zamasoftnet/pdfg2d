package net.zamasoft.pdfg2d.font;

import java.io.IOException;
import java.io.Serializable;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
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
	 * Converts a display character to a glyph while retaining a possibly
	 * different logical Unicode value for extraction. Back-ends that cannot
	 * assign separate semantic aliases default to the ordinary display glyph.
	 *
	 * @param displayCodePoint the code point whose glyph is displayed
	 * @param logicalCodePoint the code point represented semantically
	 * @param features         the feature settings (never {@code null})
	 * @return the glyph ID used for drawing
	 * @since 1.3
	 */
	public default int toGID(final int displayCodePoint, final int logicalCodePoint,
			final FontFeatureSet features) {
		return this.toGID(displayCodePoint, features);
	}

	/**
	 * Returns the glyph's advance adjustment (in font design units, along the
	 * writing axis) from the enabled features' GPOS single adjustments (e.g.
	 * {@code palt}/{@code vpal}). The default is 0 so existing implementations
	 * keep their behaviour.
	 *
	 * @param gid      the glyph ID
	 * @param features the feature settings (never {@code null})
	 * @return the advance adjustment (usually negative for {@code palt})
	 * @since 1.3
	 */
	public default short getAdvanceAdjustment(final int gid,
			final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		return 0;
	}

	/**
	 * Returns the glyph's placement adjustment (in font design units, along the
	 * writing axis) from the enabled features' GPOS single adjustments — the
	 * visual shift of the glyph shape that does not move the pen (e.g.
	 * {@code palt} xPlacement). The default is 0 so existing implementations
	 * keep their behaviour.
	 *
	 * @param gid      the glyph ID
	 * @param features the feature settings (never {@code null})
	 * @return the placement adjustment (usually negative for {@code palt})
	 * @since 1.3
	 */
	public default short getPlacementAdjustment(final int gid,
			final net.zamasoft.pdfg2d.gc.font.FontFeatureSet features) {
		return 0;
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
	 * Returns a negative value if no ligature exists.
	 * 
	 * @param gid the glyph ID
	 * @param cid the character ID
	 * @return the ligature glyph ID, or a negative value
	 */
	public int getLigature(int gid, int cid);

	/**
	 * Returns the ligature for a sequence of glyphs with OpenType feature
	 * settings applied. The default preserves the existing implementation's
	 * ligature behaviour, except that an explicitly disabled {@code liga}
	 * feature suppresses it.
	 *
	 * @param gid      the glyph ID
	 * @param cid      the character ID
	 * @param features the feature settings (never {@code null})
	 * @return the ligature glyph ID, or a negative value
	 * @since 1.3
	 */
	public default int getLigature(final int gid, final int cid, final FontFeatureSet features) {
		if (features.value(0x6c696761) == 0) { // 'liga'
			return -1;
		}
		return this.getLigature(gid, cid);
	}

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
