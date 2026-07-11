package net.zamasoft.pdfg2d.g2d.util;

import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

import net.zamasoft.pdfg2d.gc.paint.SpotColor;

/**
 * {@link java.awt.Paint} carrier for spot colors, for code that draws
 * through the {@link java.awt.Graphics2D} bridge. PDF output recognizes it
 * and emits the {@code /Separation} color space; when painted by plain AWT
 * (screen preview), it renders as the tinted alternate color.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public final class SpotPaint implements java.awt.Paint {

	private final SpotColor color;

	public SpotPaint(final SpotColor color) {
		if (color == null) {
			throw new NullPointerException("color");
		}
		this.color = color;
	}

	/**
	 * Returns the wrapped spot color.
	 *
	 * @return the spot color
	 */
	public SpotColor getSpotColor() {
		return this.color;
	}

	@Override
	public PaintContext createContext(final ColorModel cm, final Rectangle deviceBounds,
			final Rectangle2D userBounds, final AffineTransform xform, final RenderingHints hints) {
		// AWT rendering falls back to the tinted alternate color
		return G2DUtils.toAwtColor(this.color.effectiveColor()).createContext(cm, deviceBounds, userBounds, xform,
				hints);
	}

	@Override
	public int getTransparency() {
		return OPAQUE;
	}
}
