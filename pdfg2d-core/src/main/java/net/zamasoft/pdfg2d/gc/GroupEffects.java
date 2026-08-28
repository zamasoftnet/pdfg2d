package net.zamasoft.pdfg2d.gc;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * 1枚の層(グループ画像)にまとめて掛ける効果(CSSのfilter相当、2026-08-29)。
 *
 * <p>
 * 適用順は CSS の filter 関数列に合わせ、利用側が「色行列 → ぼかし → 落とし影」
 * の順で1つに畳んで渡す。どれも null / 0 なら効果なし。出力先が
 * {@link GC.Capability#GROUP_FILTER} / {@link GC.Capability#GAUSSIAN_BLUR} /
 * {@link GC.Capability#DROP_SHADOW} に対応するときだけ厳密に描かれる。
 * </p>
 *
 * @param colorMatrix 4×5 の色行列(行優先、20要素)。null なら恒等
 * @param blurSigma   ガウスぼかしの標準偏差(ユーザー空間単位)。0 以下なら無し
 * @param dropShadow  落とし影。null なら無し
 * @param opacity     層全体の不透明度 0..1
 */
public record GroupEffects(float[] colorMatrix, double blurSigma, DropShadow dropShadow, double opacity) {
	/** 落とし影: 層のシルエットを (dx, dy) ずらし sigma でぼかして color で塗り、層の下に置く。 */
	public record DropShadow(double dx, double dy, double sigma, Color color) {
	}

	public static final GroupEffects NONE = new GroupEffects(null, 0, null, 1);

	public GroupEffects {
		if (colorMatrix != null && colorMatrix.length != 20) {
			throw new IllegalArgumentException("colorMatrix must have 20 elements");
		}
	}

	public boolean isIdentity() {
		return this.colorMatrix == null && this.blurSigma <= 0 && this.dropShadow == null && this.opacity >= 1;
	}
}
