package net.zamasoft.pdfg2d.gc.paint;

import java.awt.geom.AffineTransform;

/**
 * Represents a radial gradient paint.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record RadialGradient(double cx, double cy, double radius, double fx, double fy, double[] fractions,
		Color[] colors, AffineTransform transform, SpreadMethod spread) implements Paint {

	/**
	 * Creates a new RadialGradient.
	 * 
	 * @param cx        the X coordinate of the center point
	 * @param cy        the Y coordinate of the center point
	 * @param radius    the radius of the circle
	 * @param fx        the X coordinate of the focus point
	 * @param fy        the Y coordinate of the focus point
	 * @param fractions the fractions for color distribution
	 * @param colors    the colors to distribute
	 * @param transform the transform to apply
	 */
	public RadialGradient {
		if (spread == null) {
			spread = SpreadMethod.PAD;
		}
		// Default constructor
	}

	/** 定義域外は端の色で埋める(PAD)。 */
	public RadialGradient(final double cx, final double cy, final double radius, final double fx, final double fy, final double[] fractions, final Color[] colors,
			final AffineTransform transform) {
		this(cx, cy, radius, fx, fy, fractions, colors, transform, SpreadMethod.PAD);
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.RADIAL_GRADIENT;
	}

	@Override
	public String toString() {
		return new StringBuilder().append("RadialGradient[cx=").append(this.cx).append(",cy=")
				.append(this.cy)
				.append(",radius=").append(this.radius).append(",fx=").append(this.fx).append(",fy=").append(this.fy)
				.append(",fractions=")
				.append(this.fractions)
				.append(",colors=").append(this.colors).append(",transform=").append(this.transform).append("]")
				.toString();
	}
}
