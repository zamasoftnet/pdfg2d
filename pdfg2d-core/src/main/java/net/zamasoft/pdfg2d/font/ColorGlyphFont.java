package net.zamasoft.pdfg2d.font;

import java.awt.geom.AffineTransform;

import net.zamasoft.pdfg2d.gc.GC;

/**
 * A font whose glyphs may carry color layers (OpenType COLR/CPAL). Color
 * glyphs are drawn by stacking their layer outlines, each filled with a
 * palette color, rather than as a single monochrome outline.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public interface ColorGlyphFont {

	/**
	 * Returns whether the glyph has color layers.
	 *
	 * @param gid the glyph id (in this font's glyph-id space)
	 * @return {@code true} if the glyph is a color glyph
	 */
	boolean isColorGlyph(int gid);

	/**
	 * Draws the color layers of the glyph under the given transform. The
	 * caller's fill paint is used for any layer flagged as "text color".
	 *
	 * @param gc  the graphics context
	 * @param gid the glyph id
	 * @param at  the glyph-to-user transform (design units to user space)
	 */
	void drawColorGlyph(GC gc, int gid, AffineTransform at);
}
