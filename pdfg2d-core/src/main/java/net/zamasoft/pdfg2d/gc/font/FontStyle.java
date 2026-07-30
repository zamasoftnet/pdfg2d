package net.zamasoft.pdfg2d.gc.font;

/**
 * Represents font style attributes.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public interface FontStyle {
	/**
	 * Returns the writing direction.
	 * 
	 * @return the direction
	 */
	public Direction getDirection();

	/**
	 * Represents the writing direction.
	 */
	public enum Direction {
		LTR, RTL, TB
	}

	/**
	 * Returns the font weight.
	 * 
	 * @return the font weight
	 */
	public Weight getWeight();

	/**
	 * Represents the font weight.
	 */
	public enum Weight {
		W_100((short) 100), W_200((short) 200), W_300((short) 300), W_400((short) 400), W_500((short) 500),
		W_600((short) 600), W_700((short) 700), W_800((short) 800), W_900((short) 900);

		public final short w;

		private Weight(final short w) {
			this.w = w;
		}
	}

	/**
	 * Returns the font style (e.g., normal, italic).
	 * 
	 * @return the font style
	 */
	public Style getStyle();

	/**
	 * Represents the font style.
	 */
	public enum Style {
		NORMAL, ITALIC, OBLIQUE;
	}

	/**
	 * Returns the font family list.
	 * 
	 * @return the font family list
	 */
	public FontFamilyList getFamily();

	/**
	 * Returns the font size.
	 * 
	 * @return the font size
	 */
	public double getSize();

	/**
	 * Returns the font policy list.
	 *
	 * @return the font policy list
	 */
	public FontPolicyList getPolicy();

	/**
	 * Returns the OpenType feature settings (CSS {@code font-feature-settings}
	 * / {@code font-variant-east-asian}). The default is
	 * {@link FontFeatureSet#EMPTY} so existing implementations keep the
	 * engine's default shaping behaviour.
	 *
	 * @return the feature settings (never {@code null})
	 * @since 1.3
	 */
	public default FontFeatureSet getFeatures() {
		return FontFeatureSet.EMPTY;
	}
}
