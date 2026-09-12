package net.zamasoft.pdfg2d.font;

import java.io.Serializable;

/**
 * Immutable glyph ink bounds in 1000 units per em, with y increasing downwards.
 * Coordinates retain outline precision, before font-size scaling, positioning,
 * or synthetic style transforms. Recorded vertical outline rotation is included.
 *
 * @param minX the left edge
 * @param minY the top edge
 * @param maxX the right edge
 * @param maxY the bottom edge
 */
public record GlyphBounds(double minX, double minY, double maxX, double maxY) implements Serializable {
}
