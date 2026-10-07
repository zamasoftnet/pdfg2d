package net.zamasoft.pdfg2d.gc.paint;

import java.awt.geom.AffineTransform;

/**
 * Conic gradient (CSS conic-gradient, 2026-08-29).
 *
 * <p>
 * Colors vary with the angle around the center (cx, cy). Angles increase clockwise
 * from {@code startAngle} (radians, 0 points straight up), and {@code fractions} from 0..1 span one turn.
 * {@code transform} maps to user space (for elliptical distortion and rotation).
 * PDF represents this as a Type 4 Gouraud mesh. Callers check
 * {@link net.zamasoft.pdfg2d.gc.GC.Capability#CONIC_GRADIENT} before use.
 * </p>
 */
public record ConicGradient(double cx, double cy, double startAngle, double[] fractions, Color[] colors,
		AffineTransform transform, SpreadMethod spread) implements Paint {
	public ConicGradient {
		if (colors == null) {
			throw new NullPointerException("Colors cannnot be null.");
		}
		if (fractions == null) {
			throw new NullPointerException("Fractions cannnot be null.");
		}
		if (transform == null) {
			throw new NullPointerException("Transform cannnot be null.");
		}
		if (spread == null) {
			spread = SpreadMethod.PAD;
		}
	}

	public ConicGradient(final double cx, final double cy, final double startAngle, final double[] fractions,
			final Color[] colors, final AffineTransform transform) {
		this(cx, cy, startAngle, fractions, colors, transform, SpreadMethod.PAD);
	}

	/**
	 * Returns the color at a turn fraction using straight (non-premultiplied)
	 * RGBA interpolation. Equal-position stops form a hard boundary: at an
	 * internal boundary the last stop at that position wins.
	 *
	 * @param fraction angular position, where one unit is one complete turn
	 * @return the interpolated color
	 */
	public Color colorAt(final double fraction) {
		final int n = Math.min(this.fractions.length, this.colors.length);
		if (n == 0) {
			return RGBAColor.create(0, 0, 0, 0);
		}
		if (n == 1) {
			return straight(this.colors[0]);
		}
		final double first = this.fractions[0];
		final double last = this.fractions[n - 1];
		final double length = last - first;
		double f = fraction;
		switch (this.spread) {
			case REPEAT -> {
				if (length > 0) {
					f = first + mod(fraction - first, length);
				}
			}
			case REFLECT -> {
				if (length > 0) {
					final double repeated = mod(fraction - first, 2 * length);
					f = first + (repeated <= length ? repeated : 2 * length - repeated);
				}
			}
			default -> {
				// PAD leaves the input unchanged; the range checks below clamp it.
			}
		}
		if (f <= first) {
			return straight(this.colors[0]);
		}
		if (f >= last) {
			return straight(this.colors[n - 1]);
		}
		int lower = 0;
		for (int i = 1; i < n - 1; ++i) {
			if (this.fractions[i] <= f) {
				lower = i;
			} else {
				break;
			}
		}
		final double a = this.fractions[lower];
		final double b = this.fractions[lower + 1];
		final float ratio = b > a ? (float) ((f - a) / (b - a)) : 0;
		final Color c0 = this.colors[lower];
		final Color c1 = this.colors[lower + 1];
		return RGBAColor.create(
				c0.getRed() + (c1.getRed() - c0.getRed()) * ratio,
				c0.getGreen() + (c1.getGreen() - c0.getGreen()) * ratio,
				c0.getBlue() + (c1.getBlue() - c0.getBlue()) * ratio,
				c0.getAlpha() + (c1.getAlpha() - c0.getAlpha()) * ratio);
	}

	private static Color straight(final Color color) {
		return RGBAColor.create(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private static double mod(final double value, final double divisor) {
		final double remainder = value % divisor;
		return remainder < 0 ? remainder + divisor : remainder;
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.CONIC_GRADIENT;
	}
}
