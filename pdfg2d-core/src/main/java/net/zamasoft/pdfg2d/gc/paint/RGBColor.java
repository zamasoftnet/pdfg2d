package net.zamasoft.pdfg2d.gc.paint;

/**
 * Represents an RGB color.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record RGBColor(float red, float green, float blue) implements Color {
	public static final int R = 0, G = 1, B = 2;

	/**
	 * Black color (0, 0, 0).
	 */
	public static final RGBColor BLACK = new RGBColor(0, 0, 0);

	/**
	 * White color (1, 1, 1).
	 */
	public static final RGBColor WHITE = new RGBColor(1, 1, 1);

	/**
	 * Creates a new RGB color.
	 * 
	 * @param red   the red component (0.0 - 1.0)
	 * @param green the green component (0.0 - 1.0)
	 * @param blue  the blue component (0.0 - 1.0)
	 * @return the RGB color
	 */
	public static RGBColor create(final float red, final float green, final float blue) {
		if (red <= 0 && green <= 0 && blue <= 0) {
			return RGBColor.BLACK;
		}
		if (red >= 1 && green >= 1 && blue >= 1) {
			return RGBColor.WHITE;
		}
		return new RGBColor(red, green, blue);
	}

	public RGBColor {
		red = Math.min(1.0f, Math.max(0f, red));
		green = Math.min(1.0f, Math.max(0f, green));
		blue = Math.min(1.0f, Math.max(0f, blue));
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.COLOR;
	}

	@Override
	public Color.Type getColorType() {
		return Color.Type.RGB;
	}

	@Override
	public float getComponent(final int i) {
		return switch (i) {
			case 0 -> this.red;
			case 1 -> this.green;
			case 2 -> this.blue;
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
		return 1f;
	}

	@Override
	public String toString() {
		return "rgb(" + this.red + "," + this.green + "," + this.blue + ")";
	}
}