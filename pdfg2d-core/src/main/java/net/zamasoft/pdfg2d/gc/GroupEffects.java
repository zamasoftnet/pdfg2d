package net.zamasoft.pdfg2d.gc;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * Effects applied together to a single layer (group image), equivalent to CSS filter (2026-08-29).
 *
 * <p>
 * The caller combines the effects in the order "color matrix -> blur -> drop shadow",
 * following the order of CSS filter functions. All null / 0 means no effects.
 * Accurate rendering requires output target support for
 * {@link GC.Capability#GROUP_FILTER} / {@link GC.Capability#GAUSSIAN_BLUR} /
 * {@link GC.Capability#DROP_SHADOW}.
 * </p>
 *
 * @param colorMatrix 4x5 color matrix (row-major, 20 elements). Null means identity
 * @param blurSigma   Gaussian blur standard deviation (user space units). No blur if 0 or less
 * @param dropShadow  drop shadow. Null means none
 * @param opacity     opacity of the entire layer, 0..1
 */
public record GroupEffects(float[] colorMatrix, double blurSigma, DropShadow dropShadow, double opacity) {
	/** Drop shadow: shifts the layer silhouette by (dx, dy), blurs by sigma, fills with color, and places it below the layer. */
	public record DropShadow(double dx, double dy, double sigma, Color color) {
	}

	public static final GroupEffects NONE = new GroupEffects(null, 0, null, 1);

	public GroupEffects {
		if (colorMatrix != null && colorMatrix.length != 20) {
			throw new IllegalArgumentException("colorMatrix must have 20 elements");
		}
	}

	public boolean isIdentity() {
		return this.colorMatrix == null && this.blurSigma <= 0 && this.dropShadow == null && this.opacity >= 1;
	}
}
