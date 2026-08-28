package net.zamasoft.pdfg2d.gc.paint;

/**
 * Represents a paint used for drawing operations.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public sealed interface Paint permits Color, Pattern, LinearGradient, RadialGradient, ConicGradient {
	public enum Type {
		COLOR, PATTERN, LINEAR_GRADIENT, RADIAL_GRADIENT, CONIC_GRADIENT
	}

	/**
	 * Returns the type of this paint.
	 * 
	 * @return the paint type
	 */
	public Type getPaintType();
}
