package net.zamasoft.pdfg2d.gc.paint;

/**
 * A spot color (特色): a named printing colorant with an alternate color for
 * outputs that cannot produce the actual ink.
 * <p>
 * PDF output realizes this as a {@code /Separation} color space with the
 * colorant name and a tint transform to the alternate color; every other
 * output (screen preview, raster images, SVG) sees the {@code Color}
 * interface, whose components are the alternate color scaled by the tint —
 * so code that does not know about separations still renders a faithful
 * approximation.
 * </p>
 * <p>
 * The same colorant name always resolves to the same color space resource
 * within a document; use {@link #tint(float)} to derive screen percentages
 * of one plate.
 * </p>
 *
 * @param name      the colorant name (e.g. {@code "PANTONE 185 C"}, or
 *                  {@code "All"} for the registration color)
 * @param alternate the alternate color shown when the colorant is
 *                  unavailable (typically CMYK)
 * @param tint      the tint (screen percentage) in {@code 0..1}
 * @param overprint the overprint mode, as in {@link CMYKColor}
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public record SpotColor(String name, Color alternate, float tint, byte overprint) implements Color {

	/**
	 * The registration color: prints on every plate. Used for trim and
	 * registration marks on separated output.
	 */
	public static final SpotColor REGISTRATION = new SpotColor("All", new CMYKColor(1, 1, 1, 1,
			CMYKColor.OVERPRINT_NONE), 1, CMYKColor.OVERPRINT_NONE);

	public SpotColor {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Colorant name is required.");
		}
		if (alternate == null) {
			throw new NullPointerException("alternate");
		}
		if (alternate instanceof SpotColor) {
			throw new IllegalArgumentException("The alternate of a spot color must be a process color.");
		}
		tint = Math.max(0, Math.min(1, tint));
	}

	/**
	 * Creates a spot color at full tint.
	 *
	 * @param name      the colorant name
	 * @param alternate the alternate (process) color
	 * @return the spot color
	 */
	public static SpotColor create(final String name, final Color alternate) {
		return new SpotColor(name, alternate, 1, CMYKColor.OVERPRINT_NONE);
	}

	/**
	 * Creates a spot color with the given tint.
	 *
	 * @param name      the colorant name
	 * @param alternate the alternate (process) color
	 * @param tint      the tint in {@code 0..1}
	 * @return the spot color
	 */
	public static SpotColor create(final String name, final Color alternate, final float tint) {
		return new SpotColor(name, alternate, tint, CMYKColor.OVERPRINT_NONE);
	}

	/**
	 * Returns this plate at another tint.
	 *
	 * @param tint the tint in {@code 0..1}
	 * @return a spot color of the same plate
	 */
	public SpotColor tint(final float tint) {
		return new SpotColor(this.name, this.alternate, tint, this.overprint);
	}

	/**
	 * Returns the alternate color scaled by the tint — what non-separating
	 * outputs should display. The result has the alternate's color type.
	 *
	 * @return the effective process color at this tint
	 */
	public Color effectiveColor() {
		if (this.tint >= 1) {
			return this.alternate;
		}
		switch (this.alternate.getColorType()) {
			case CMYK -> {
				return CMYKColor.create(this.alternate.getComponent(CMYKColor.C) * this.tint,
						this.alternate.getComponent(CMYKColor.M) * this.tint,
						this.alternate.getComponent(CMYKColor.Y) * this.tint,
						this.alternate.getComponent(CMYKColor.K) * this.tint);
			}
			case GRAY -> {
				// Gray: 1 = white; scale the ink amount
				return GrayColor.create(1 - (1 - this.alternate.getComponent(0)) * this.tint);
			}
			default -> {
				// RGB/RGBA: interpolate toward white
				return RGBColor.create(1 - (1 - this.alternate.getRed()) * this.tint,
						1 - (1 - this.alternate.getGreen()) * this.tint,
						1 - (1 - this.alternate.getBlue()) * this.tint);
			}
		}
	}

	@Override
	public Type getColorType() {
		return Type.SPOT;
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
		if (i != 0) {
			throw new IllegalArgumentException(String.valueOf(i));
		}
		return this.tint;
	}

	@Override
	public String toString() {
		return "-cssj-spot(" + this.name + "," + this.alternate + "," + this.tint + "," + this.overprint + ")";
	}
}
