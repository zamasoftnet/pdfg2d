package net.zamasoft.pdfg2d.gc.paint;

/**
 * Represents a color or paint that can be applied to graphics.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public sealed interface Color extends Paint permits RGBColor, CMYKColor, GrayColor, RGBAColor, SpotColor {
	public enum Type {
		RGB, CMYK, GRAY, RGBA,
		/** A named spot colorant with an alternate process color. */
		SPOT
	}

	/**
	 * Returns the type of this color.
	 * 
	 * @return the color type
	 */
	public Type getColorType();

	/**
	 * Returns the red component.
	 * 
	 * @return the red component (0-1)
	 */
	public float getRed();

	/**
	 * Returns the green component.
	 * 
	 * @return the green component (0-1)
	 */
	public float getGreen();

	/**
	 * Returns the blue component.
	 * 
	 * @return the blue component (0-1)
	 */
	public float getBlue();

	/**
	 * Returns the alpha component.
	 * 
	 * @return the alpha component (0-1)
	 */
	public float getAlpha();

	/**
	 * Returns the component at the specified index.
	 * 
	 * @param i the index
	 * @return the component value (0-1)
	 */
	public float getComponent(int i);
}