package net.zamasoft.pdfg2d.font;

import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;

/**
 * An individual glyph within a font.
 * 
 * @param path       the outline path of the glyph
 * @param charString the raw charstring data for CFF fonts
 * @since 1.0
 * @author <a href="mailto:david@steadystate.co.uk">David Schweinsberg</a>
 */
public record Glyph(GeneralPath path, byte[] charString) {

	/**
	 * Returns whether the glyph has no drawable outlines.
	 *
	 * <p>
	 * Some fonts have cmap entries with empty glyphs (kanji in the JejuGothic family,
	 * all characters in Adobe Blank). Treating them as displayable makes characters disappear
	 * without falling back to another font or issuing a warning, so font selection
	 * uses this check (2026-09-01).
	 * </p>
	 *
	 * @return {@code true} if there are only moves and closes, with no lines or curves
	 */
	public boolean isBlank() {
		if (this.path == null) {
			return true;
		}
		final double[] coords = new double[6];
		for (final PathIterator i = this.path.getPathIterator(null); !i.isDone(); i.next()) {
			final int type = i.currentSegment(coords);
			if (type != PathIterator.SEG_MOVETO && type != PathIterator.SEG_CLOSE) {
				return false;
			}
		}
		return true;
	}
}
