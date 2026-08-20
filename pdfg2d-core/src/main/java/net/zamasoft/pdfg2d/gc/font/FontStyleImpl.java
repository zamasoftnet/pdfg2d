package net.zamasoft.pdfg2d.gc.font;

import java.io.Serializable;

/**
 * Implementation of FontStyle.
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public record FontStyleImpl(
		FontFamilyList families,
		double size,
		Style style,
		Weight weight,
		Direction direction,
		FontPolicyList policy,
		FontFeatureSet features,
		boolean synthesisWeight,
		boolean synthesisStyle) implements FontStyle, Serializable {

	public FontStyleImpl {
		if (features == null) {
			features = FontFeatureSet.EMPTY;
		}
	}

	/** Synthesis-permitting form (CSS font-synthesis initial value). */
	public FontStyleImpl(final FontFamilyList families, final double size, final Style style, final Weight weight,
			final Direction direction, final FontPolicyList policy, final FontFeatureSet features) {
		this(families, size, style, weight, direction, policy, features, true, true);
	}

	/** Feature-less form: every OpenType feature is left unspecified. */
	public FontStyleImpl(final FontFamilyList families, final double size, final Style style, final Weight weight,
			final Direction direction, final FontPolicyList policy) {
		this(families, size, style, weight, direction, policy, FontFeatureSet.EMPTY);
	}

	@Override
	public FontFamilyList getFamily() {
		return this.families;
	}

	@Override
	public double getSize() {
		return this.size;
	}

	@Override
	public Style getStyle() {
		return this.style;
	}

	@Override
	public Weight getWeight() {
		return this.weight;
	}

	@Override
	public Direction getDirection() {
		return this.direction;
	}

	@Override
	public FontPolicyList getPolicy() {
		return this.policy;
	}

	@Override
	public FontFeatureSet getFeatures() {
		return this.features;
	}

	@Override
	public boolean getSynthesisWeight() {
		return this.synthesisWeight;
	}

	@Override
	public boolean getSynthesisStyle() {
		return this.synthesisStyle;
	}

	@Override
	public String toString() {
		return "FontStyleImpl[families=" + this.families + ", size=" + this.size + ", style=" + this.style
				+ ", weight=" + this.weight + ", direction=" + this.direction + ", policy=" + this.policy
				+ ", features=" + this.features + "]";
	}
}
