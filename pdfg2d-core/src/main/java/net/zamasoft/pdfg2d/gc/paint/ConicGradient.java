package net.zamasoft.pdfg2d.gc.paint;

import java.awt.geom.AffineTransform;

/**
 * 円錐グラデーション(CSSのconic-gradient、2026-08-29)。
 *
 * <p>
 * 中心 (cx, cy) から角度で色が変わる。角度は {@code startAngle}(ラジアン、
 * 真上=0)から時計回りに増え、{@code fractions} は 0..1 で1周に対応する。
 * {@code transform} はユーザー空間へ写す変換(楕円化・回転用)。
 * PDFには対応するシェーディングが無いので、利用側は
 * {@link net.zamasoft.pdfg2d.gc.GC.Capability#CONIC_GRADIENT} を確認してから使う。
 * </p>
 */
public record ConicGradient(double cx, double cy, double startAngle, double[] fractions, Color[] colors,
		AffineTransform transform, SpreadMethod spread) implements Paint {
	public ConicGradient {
		if (colors == null) {
			throw new NullPointerException("Colors cannnot be null.");
		}
		if (fractions == null) {
			throw new NullPointerException("Fractions cannnot be null.");
		}
		if (transform == null) {
			throw new NullPointerException("Transform cannnot be null.");
		}
		if (spread == null) {
			spread = SpreadMethod.PAD;
		}
	}

	public ConicGradient(final double cx, final double cy, final double startAngle, final double[] fractions,
			final Color[] colors, final AffineTransform transform) {
		this(cx, cy, startAngle, fractions, colors, transform, SpreadMethod.PAD);
	}

	@Override
	public Paint.Type getPaintType() {
		return Paint.Type.CONIC_GRADIENT;
	}
}
