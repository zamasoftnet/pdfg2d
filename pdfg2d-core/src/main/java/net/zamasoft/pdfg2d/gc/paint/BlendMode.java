package net.zamasoft.pdfg2d.gc.paint;

/**
 * Blend modes (PDF 1.4 §11.3.5 / CSS Compositing Level 1 {@code mix-blend-mode}).
 *
 * <p>
 * Added 2026-08-29 as an additive graphics state attribute: {@link net.zamasoft.pdfg2d.gc.GC#setBlendMode}
 * has a no-op default so every existing backend keeps compiling and behaving as before. Only backends that
 * can express the mode natively (PDF ExtGState {@code /BM}, SVG {@code mix-blend-mode}) honour it; the
 * Java2D backend composes the modes itself with a custom {@code java.awt.Composite} (2026-08-29).
 * </p>
 */
public enum BlendMode {
	NORMAL("Normal", "normal"), //
	MULTIPLY("Multiply", "multiply"), //
	SCREEN("Screen", "screen"), //
	OVERLAY("Overlay", "overlay"), //
	DARKEN("Darken", "darken"), //
	LIGHTEN("Lighten", "lighten"), //
	COLOR_DODGE("ColorDodge", "color-dodge"), //
	COLOR_BURN("ColorBurn", "color-burn"), //
	HARD_LIGHT("HardLight", "hard-light"), //
	SOFT_LIGHT("SoftLight", "soft-light"), //
	DIFFERENCE("Difference", "difference"), //
	EXCLUSION("Exclusion", "exclusion"), //
	HUE("Hue", "hue"), //
	SATURATION("Saturation", "saturation"), //
	COLOR("Color", "color"), //
	LUMINOSITY("Luminosity", "luminosity");

	/** PDF name used as the value of the {@code /BM} ExtGState entry. */
	public final String pdfName;

	/** CSS keyword ({@code mix-blend-mode} / SVG {@code mix-blend-mode} style value). */
	public final String cssName;

	BlendMode(final String pdfName, final String cssName) {
		this.pdfName = pdfName;
		this.cssName = cssName;
	}

	/**
	 * Looks up a mode by its CSS keyword (case-insensitive).
	 *
	 * @return the mode, or {@code null} when the keyword is unknown.
	 */
	public static BlendMode fromCssName(final String name) {
		if (name == null) {
			return null;
		}
		for (final BlendMode mode : values()) {
			if (mode.cssName.equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return null;
	}
}
