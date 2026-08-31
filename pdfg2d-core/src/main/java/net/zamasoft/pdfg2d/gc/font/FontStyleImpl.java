package net.zamasoft.pdfg2d.gc.font;

import java.io.Serializable;
import java.util.Locale;

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
		boolean synthesisStyle,
		TextOrientation textOrientation,
		int widthClass,
		Locale lang) implements FontStyle, Serializable {

	public FontStyleImpl {
		if (features == null) {
			features = FontFeatureSet.EMPTY;
		}
		if (textOrientation == null) {
			textOrientation = TextOrientation.MIXED;
		}
		if (widthClass < 1 || widthClass > 9) {
			widthClass = net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS;
		}
	}

	/** 言語別フォント選択導入前と同じ言語指定なしを使う互換コンストラクタ。 */
	public FontStyleImpl(final FontFamilyList families, final double size, final Style style, final Weight weight,
			final Direction direction, final FontPolicyList policy, final FontFeatureSet features,
			final boolean synthesisWeight, final boolean synthesisStyle, final TextOrientation textOrientation,
			final int widthClass) {
		this(families, size, style, weight, direction, policy, features, synthesisWeight, synthesisStyle,
				textOrientation, widthClass, null);
	}

	/** font-stretch導入前(2026-08-29)と同じ通常幅を使う互換コンストラクタ。 */
	public FontStyleImpl(final FontFamilyList families, final double size, final Style style, final Weight weight,
			final Direction direction, final FontPolicyList policy, final FontFeatureSet features,
			final boolean synthesisWeight, final boolean synthesisStyle, final TextOrientation textOrientation) {
		this(families, size, style, weight, direction, policy, features, synthesisWeight, synthesisStyle,
				textOrientation, net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS);
	}

	/** text-orientation導入前と同じmixedを使う互換コンストラクタ。 */
	public FontStyleImpl(final FontFamilyList families, final double size, final Style style, final Weight weight,
			final Direction direction, final FontPolicyList policy, final FontFeatureSet features,
			final boolean synthesisWeight, final boolean synthesisStyle) {
		this(families, size, style, weight, direction, policy, features, synthesisWeight, synthesisStyle,
				TextOrientation.MIXED);
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
	public TextOrientation getTextOrientation() {
		return this.textOrientation;
	}

	@Override
	public int getWidthClass() {
		return this.widthClass;
	}

	@Override
	public Locale getLang() {
		return this.lang;
	}

	@Override
	public String toString() {
		return "FontStyleImpl[families=" + this.families + ", size=" + this.size + ", style=" + this.style
				+ ", weight=" + this.weight + ", direction=" + this.direction + ", textOrientation="
				+ this.textOrientation + ", widthClass=" + this.widthClass + ", policy=" + this.policy
				+ ", features=" + this.features + ", lang=" + this.lang + "]";
	}
}
