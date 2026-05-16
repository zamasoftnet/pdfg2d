package net.zamasoft.pdfg2d.gc.paint;

/**
 * Represents an RGBA color with an alpha (opacity) component.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record RGBAColor(float red, float green, float blue, float alpha) implements Color {
	public static final int A = 3;

	/**
	 * Creates a new RGB(A) color.
	 * 
	 * @param red   the red component
	 * @param green the green component
	 * @param blue  the blue component
	 * @param alpha the alpha component
	 * @return the RGB or RGBA color
	 */
	public static Color create(final float red, final float green, final float blue, final float alpha) {
		if (alpha == 1) {
			return RGBColor.create(red, green, blue);
		}
		return new RGBAColor(red, green, blue, alpha);
	}

	public RGBAColor {
		red = Math.min(1.0f, Math.max(0f, red));
		green = Math.min(1.0f, Math.max(0f, green));
		blue = Math.min(1.0f, Math.max(0f, blue));
		alpha = Math.min(1.0f, Math.max(0f, alpha));
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.COLOR;
	}

	@Override
	public Color.Type getColorType() {
		return Color.Type.RGBA;
	}

	@Override
	public float getComponent(final int i) {
		return switch (i) {
			case 0 -> this.red;
			case 1 -> this.green;
			case 2 -> this.blue;
			case 3 -> this.alpha;
			default -> throw new IllegalArgumentException();
		};
	}

	@Override
	public float getRed() {
		return this.red;
	}

	@Override
	public float getGreen() {
		return this.green;
	}

	@Override
	public float getBlue() {
		return this.blue;
	}

	@Override
	public float getAlpha() {
		return this.alpha;
	}

	@Override
	public String toString() {
		return "rgba(" + this.red + "," + this.green + "," + this.blue + "," + this.alpha + ")";
	}
}
