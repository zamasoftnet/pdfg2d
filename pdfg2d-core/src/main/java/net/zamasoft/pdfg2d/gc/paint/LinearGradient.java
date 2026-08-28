package net.zamasoft.pdfg2d.gc.paint;

import java.awt.geom.AffineTransform;

/**
 * Represents a linear gradient paint.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record LinearGradient(double x1, double y1, double x2, double y2, double[] fractions, Color[] colors,
		AffineTransform transform, SpreadMethod spread) implements Paint {

	/**
	 * Creates a new LinearGradient.
	 * 
	 * @param x1        the X coordinate of the start point
	 * @param y1        the Y coordinate of the start point
	 * @param x2        the X coordinate of the end point
	 * @param y2        the Y coordinate of the end point
	 * @param fractions the fractions for color distribution
	 * @param colors    the colors to distribute
	 * @param transform the transform to apply
	 * @throws NullPointerException if colors, fractions, or transform is null
	 */
	public LinearGradient {
		if (spread == null) {
			spread = SpreadMethod.PAD;
		}
		if (colors == null) {
			throw new NullPointerException("Colors cannnot be null.");
		}
		if (fractions == null) {
			throw new NullPointerException("Fractions cannnot be null.");
		}
		if (transform == null) {
			throw new NullPointerException("Transform cannnot be null.");
		}
	}

	/** 定義域外は端の色で埋める(PAD)。 */
	public LinearGradient(final double x1, final double y1, final double x2, final double y2, final double[] fractions, final Color[] colors,
			final AffineTransform transform) {
		this(x1, y1, x2, y2, fractions, colors, transform, SpreadMethod.PAD);
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.LINEAR_GRADIENT;
	}

	@Override
	public String toString() {
		return new StringBuilder().append("LinearGradient[x1=").append(this.x1).append(",y1=")
				.append(this.y1)
				.append(",x2=").append(this.x2).append(",y2=").append(this.y2).append(",fractions=")
				.append(this.fractions)
				.append(",colors=").append(this.colors).append(",transform=").append(this.transform).append("]")
				.toString();
	}
}
