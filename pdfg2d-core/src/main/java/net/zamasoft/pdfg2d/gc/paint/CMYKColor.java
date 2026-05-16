package net.zamasoft.pdfg2d.gc.paint;

import java.io.Serializable;

/**
 * Represents a CMYK color.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record CMYKColor(float cyan, float magenta, float yellow, float black, byte overprint)
		implements Color, Serializable {
	private static final long serialVersionUID = 1L;
	public static final int C = 0, M = 1, Y = 2, K = 3;

	/**
	 * No overprint.
	 */
	public static final byte OVERPRINT_NONE = 0;

	/**
	 * Standard overprint mode.
	 */
	public static final byte OVERPRINT_STANDARD = 1;

	/**
	 * Adobe Illustrator overprint mode.
	 */
	public static final byte OVERPRINT_ILLUSTRATOR = 2;

	/**
	 * Creates a CMYK color.
	 * 
	 * @param cyan    the cyan component
	 * @param magenta the magenta component
	 * @param yellow  the yellow component
	 * @param black   the black component
	 * @return the CMYK color
	 */
	public static CMYKColor create(final float cyan, final float magenta, final float yellow, final float black) {
		return new CMYKColor(cyan, magenta, yellow, black, OVERPRINT_NONE);
	}

	/**
	 * Creates a CMYK color with overprint.
	 * 
	 * @param cyan      the cyan component
	 * @param magenta   the magenta component
	 * @param yellow    the yellow component
	 * @param black     the black component
	 * @param overprint the overprint mode
	 * @return the CMYK color
	 */
	public static CMYKColor create(final float cyan, final float magenta, final float yellow, final float black,
			final byte overprint) {
		return new CMYKColor(cyan, magenta, yellow, black, overprint);
	}

	/**
	 * Creates a CMYK color with no overprint.
	 *
	 * @param cyan    the cyan component (0.0–1.0)
	 * @param magenta the magenta component (0.0–1.0)
	 * @param yellow  the yellow component (0.0–1.0)
	 * @param black   the black (key) component (0.0–1.0)
	 */
	public CMYKColor(final float cyan, final float magenta, final float yellow, final float black) {
		this(cyan, magenta, yellow, black, OVERPRINT_NONE);
	}

	public CMYKColor {
		cyan = Math.min(1.0f, Math.max(0f, cyan));
		magenta = Math.min(1.0f, Math.max(0f, magenta));
		yellow = Math.min(1.0f, Math.max(0f, yellow));
		black = Math.min(1.0f, Math.max(0f, black));
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.COLOR;
	}

	@Override
	public Color.Type getColorType() {
		return Color.Type.CMYK;
	}

	@Override
	public float getComponent(final int i) {
		return switch (i) {
			case C -> this.cyan;
			case M -> this.magenta;
			case Y -> this.yellow;
			case K -> this.black;
			default -> throw new IllegalArgumentException();
		};
	}

	@Override
	public float getRed() {
		return Math.max(0, 1.0f - (this.cyan + this.black));
	}

	@Override
	public float getGreen() {
		return Math.max(0, 1.0f - (this.magenta + this.black));
	}

	@Override
	public float getBlue() {
		return Math.max(0, 1.0f - (this.yellow + this.black));
	}

	@Override
	public float getAlpha() {
		return 1f;
	}

	/**
	 * Returns the overprint mode for this color.
	 *
	 * @return the overprint mode ({@link #OVERPRINT_NONE}, {@link #OVERPRINT_STANDARD},
	 *         or {@link #OVERPRINT_ILLUSTRATOR})
	 */
	public byte getOverprint() {
		return this.overprint;
	}

	@Override
	public String toString() {
		return "-cssj-cmyk(" + this.cyan + "," + this.magenta + "," + this.yellow + "," + this.black + ","
				+ this.overprint + ")";
	}
}