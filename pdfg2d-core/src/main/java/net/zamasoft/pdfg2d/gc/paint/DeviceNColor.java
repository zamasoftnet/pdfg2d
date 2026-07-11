package net.zamasoft.pdfg2d.gc.paint;

import java.util.Arrays;

/**
 * A multi-colorant device color: several named printing inks applied
 * together, each with its own tint. Typical uses are duotones (a spot color
 * plus black) and overprinting two spot inks.
 * <p>
 * PDF output realizes this as a {@code /DeviceN} color space whose tint
 * transform combines the colorants' alternate colors; every other output
 * sees the {@code Color} interface, whose components are the multiplicative
 * (ink-overlay) combination of the tinted alternates.
 * </p>
 * <p>
 * The colorant set (by name, in order) identifies the color space: the same
 * set always resolves to the same resource within a document. The
 * {@code tint} of each {@link SpotColor} element is ignored here; the
 * per-colorant tints of this color are given by {@code tints}.
 * </p>
 *
 * @param colorants the colorants (2 or more; names must be distinct)
 * @param tints     one tint in {@code 0..1} per colorant
 * @author MIYABE Tatsuhiko
 * @since 1.3
 */
public record DeviceNColor(SpotColor[] colorants, float[] tints) implements Color {

	/** The PDF limit for the number of DeviceN colorants. */
	public static final int MAX_COLORANTS = 32;

	public DeviceNColor {
		if (colorants == null || colorants.length < 2) {
			throw new IllegalArgumentException("DeviceN requires at least two colorants.");
		}
		if (colorants.length > MAX_COLORANTS) {
			throw new IllegalArgumentException("DeviceN allows at most " + MAX_COLORANTS + " colorants.");
		}
		if (tints == null || tints.length != colorants.length) {
			throw new IllegalArgumentException("One tint per colorant is required.");
		}
		final var names = new java.util.HashSet<String>();
		for (final var colorant : colorants) {
			if (colorant == null) {
				throw new NullPointerException("colorant");
			}
			if (!names.add(colorant.name())) {
				throw new IllegalArgumentException("Duplicate colorant: " + colorant.name());
			}
		}
		colorants = colorants.clone();
		tints = tints.clone();
		for (var i = 0; i < tints.length; ++i) {
			tints[i] = Math.max(0, Math.min(1, tints[i]));
		}
	}

	/**
	 * Creates a DeviceN color with all colorants at full tint.
	 *
	 * @param colorants the colorants
	 * @return the DeviceN color
	 */
	public static DeviceNColor create(final SpotColor... colorants) {
		final var tints = new float[colorants.length];
		Arrays.fill(tints, 1);
		return new DeviceNColor(colorants, tints);
	}

	/**
	 * Returns this ink combination at other tints.
	 *
	 * @param tints one tint in {@code 0..1} per colorant
	 * @return a DeviceN color over the same colorants
	 */
	public DeviceNColor tints(final float... tints) {
		return new DeviceNColor(this.colorants, tints);
	}

	/**
	 * Returns the overlay of the tinted alternates — what non-separating
	 * outputs should display. Inks are combined multiplicatively per RGB
	 * channel, which models overprinting layers of translucent ink.
	 *
	 * @return the effective process color
	 */
	public Color effectiveColor() {
		float r = 1, g = 1, b = 1;
		for (var i = 0; i < this.colorants.length; ++i) {
			final var c = this.colorants[i].tint(this.tints[i]).effectiveColor();
			r *= c.getRed();
			g *= c.getGreen();
			b *= c.getBlue();
		}
		return RGBColor.create(r, g, b);
	}

	@Override
	public Type getColorType() {
		return Type.DEVICEN;
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.COLOR;
	}

	@Override
	public float getRed() {
		return this.effectiveColor().getRed();
	}

	@Override
	public float getGreen() {
		return this.effectiveColor().getGreen();
	}

	@Override
	public float getBlue() {
		return this.effectiveColor().getBlue();
	}

	@Override
	public float getAlpha() {
		return 1;
	}

	@Override
	public float getComponent(final int i) {
		return this.tints[i];
	}

	@Override
	public boolean equals(final Object o) {
		return o instanceof DeviceNColor other && Arrays.equals(this.colorants, other.colorants)
				&& Arrays.equals(this.tints, other.tints);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.colorants) * 31 + Arrays.hashCode(this.tints);
	}

	@Override
	public String toString() {
		return "DeviceN" + Arrays.toString(this.colorants) + Arrays.toString(this.tints);
	}
}
