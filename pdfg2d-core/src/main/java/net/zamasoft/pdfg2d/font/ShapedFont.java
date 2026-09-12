package net.zamasoft.pdfg2d.font;

import java.awt.Shape;
import java.awt.geom.PathIterator;

/**
 * A font that can return the shape of a glyph.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface ShapedFont extends DrawableFont {
	/**
	 * Returns the shape of a glyph.
	 * 
	 * @param gid the glyph ID
	 * @return the shape of the glyph
	 */
	public abstract Shape getShapeByGID(int gid);

	/**
	 * Returns outline bounds in 1000 units per em, with y increasing downwards,
	 * before font-size scaling, positioning, or synthetic style transforms.
	 * This default implementation does not cache the result.
	 *
	 * @param gid the ID accepted by {@link #getShapeByGID(int)} (a subset CID for
	 *            embedded fonts)
	 * @return immutable bounds, or {@code null} for a missing or blank outline
	 */
	public default GlyphBounds getGlyphBounds(final int gid) {
		final var shape = this.getShapeByGID(gid);
		if (shape == null) {
			return null;
		}
		final var bounds = shape.getBounds2D();
		if (bounds.isEmpty()) {
			return null;
		}
		// Match Glyph.isBlank() without copying arbitrary Shapes into a float path.
		final double[] coords = new double[6];
		for (final var path = shape.getPathIterator(null); !path.isDone(); path.next()) {
			final int type = path.currentSegment(coords);
			if (type != PathIterator.SEG_MOVETO && type != PathIterator.SEG_CLOSE) {
				return new GlyphBounds(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
			}
		}
		return null;
	}
}
