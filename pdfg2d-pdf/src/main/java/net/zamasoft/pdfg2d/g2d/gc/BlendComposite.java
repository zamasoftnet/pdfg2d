package net.zamasoft.pdfg2d.g2d.gc;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import net.zamasoft.pdfg2d.gc.paint.BlendMode;

/**
 * Java2D 用のブレンド合成(PDF 1.7 §11.3.5 / CSS Compositing Level 1、2026-08-29)。
 *
 * <p>
 * {@link AlphaComposite} は SrcOver しか持たないので、
 * {@link BlendMode#NORMAL} 以外はこのクラスが画素単位で
 * {@code Cr = (1 - αs)·Cb + αs·((1 - αb)·Cs + αb·B(Cb, Cs))} を計算する。
 * ソース・デスティネーションの色モデルは問わず({@link ColorModel#getRGB(Object)}
 * 経由で非乗算 sRGB に揃える)、定数アルファ({@code alpha})はソースの
 * アルファに掛かる。NORMAL のときは {@link AlphaComposite} を返すので
 * 既存の描画経路は一切変わらない。
 * </p>
 */
public final class BlendComposite implements Composite {
	private final BlendMode mode;

	private final float alpha;

	private BlendComposite(final BlendMode mode, final float alpha) {
		this.mode = mode;
		this.alpha = alpha;
	}

	/**
	 * ブレンドモードと定数アルファに対応する {@link Composite} を返す。
	 *
	 * @param mode  ブレンドモード。null は NORMAL
	 * @param alpha 定数アルファ 0..1
	 * @return NORMAL なら {@link AlphaComposite}(SrcOver)、それ以外はブレンド合成
	 */
	public static Composite getInstance(final BlendMode mode, final float alpha) {
		final float a = Math.max(0f, Math.min(1f, alpha));
		if (mode == null || mode == BlendMode.NORMAL) {
			return a >= 1f ? AlphaComposite.SrcOver : AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a);
		}
		return new BlendComposite(mode, a);
	}

	public BlendMode getMode() {
		return this.mode;
	}

	public float getAlpha() {
		return this.alpha;
	}

	@Override
	public CompositeContext createContext(final ColorModel srcColorModel, final ColorModel dstColorModel,
			final RenderingHints hints) {
		return new Context(srcColorModel, dstColorModel);
	}

	@Override
	public int hashCode() {
		return this.mode.hashCode() * 31 + Float.floatToIntBits(this.alpha);
	}

	@Override
	public boolean equals(final Object obj) {
		return obj instanceof BlendComposite other && other.mode == this.mode && other.alpha == this.alpha;
	}

	private final class Context implements CompositeContext {
		private final ColorModel srcCM, dstCM;

		Context(final ColorModel srcCM, final ColorModel dstCM) {
			this.srcCM = srcCM;
			this.dstCM = dstCM;
		}

		@Override
		public void compose(final Raster src, final Raster dstIn, final WritableRaster dstOut) {
			final int w = Math.min(src.getWidth(), dstIn.getWidth());
			final int h = Math.min(src.getHeight(), dstIn.getHeight());
			final int sx0 = src.getMinX(), sy0 = src.getMinY();
			final int dx0 = dstIn.getMinX(), dy0 = dstIn.getMinY();
			final int ox0 = dstOut.getMinX(), oy0 = dstOut.getMinY();
			Object sp = null, dp = null, op = null;
			final float[] cs = new float[3], cb = new float[3], cr = new float[3];
			for (int y = 0; y < h; ++y) {
				for (int x = 0; x < w; ++x) {
					sp = src.getDataElements(sx0 + x, sy0 + y, sp);
					dp = dstIn.getDataElements(dx0 + x, dy0 + y, dp);
					final int s = this.srcCM.getRGB(sp);
					final int d = this.dstCM.getRGB(dp);
					final int result = blendPixel(s, d, cs, cb, cr);
					op = this.dstCM.getDataElements(result, op);
					dstOut.setDataElements(ox0 + x, oy0 + y, op);
				}
			}
		}

		@Override
		public void dispose() {
			// nothing to release
		}
	}

	/**
	 * 非乗算 ARGB 1画素のブレンド。テスト用に公開。
	 *
	 * @param s ソース(非乗算 ARGB)
	 * @param d デスティネーション(非乗算 ARGB)
	 * @return 合成結果(非乗算 ARGB)
	 */
	public int blend(final int s, final int d) {
		return this.blendPixel(s, d, new float[3], new float[3], new float[3]);
	}

	private int blendPixel(final int s, final int d, final float[] cs, final float[] cb, final float[] cr) {
		final float as = ((s >>> 24) / 255f) * this.alpha;
		if (as <= 0f) {
			return d;
		}
		final float ab = (d >>> 24) / 255f;
		cs[0] = ((s >> 16) & 0xFF) / 255f;
		cs[1] = ((s >> 8) & 0xFF) / 255f;
		cs[2] = (s & 0xFF) / 255f;
		cb[0] = ((d >> 16) & 0xFF) / 255f;
		cb[1] = ((d >> 8) & 0xFF) / 255f;
		cb[2] = (d & 0xFF) / 255f;
		blendColor(this.mode, cb, cs, cr);
		final float ar = as + ab * (1f - as);
		int out = Math.round(ar * 255f) << 24;
		for (int i = 0; i < 3; ++i) {
			// PDF 11.3.8: Cr = (1 - as/ar)·Cb + (as/ar)·((1 - ab)·Cs + ab·B(Cb, Cs))
			final float c = (1f - as / ar) * cb[i] + (as / ar) * ((1f - ab) * cs[i] + ab * cr[i]);
			out |= clamp8(c) << (16 - 8 * i);
		}
		return out;
	}

	private static int clamp8(final float v) {
		final int i = Math.round(v * 255f);
		return i < 0 ? 0 : (i > 255 ? 255 : i);
	}

	/**
	 * ブレンド関数 B(Cb, Cs) を計算する(0..1 の sRGB、非乗算)。
	 *
	 * @param mode モード
	 * @param cb   背景色 (r, g, b)
	 * @param cs   前景色 (r, g, b)
	 * @param out  結果 (r, g, b)
	 */
	public static void blendColor(final BlendMode mode, final float[] cb, final float[] cs, final float[] out) {
		switch (mode) {
			case HUE -> setLum(setSat(cs, sat(cb), out), lum(cb), out);
			case SATURATION -> setLum(setSat(cb, sat(cs), out), lum(cb), out);
			case COLOR -> setLum(cs, lum(cb), out);
			case LUMINOSITY -> setLum(cb, lum(cs), out);
			default -> {
				for (int i = 0; i < 3; ++i) {
					out[i] = separable(mode, cb[i], cs[i]);
				}
			}
		}
	}

	private static float separable(final BlendMode mode, final float cb, final float cs) {
		return switch (mode) {
			case NORMAL -> cs;
			case MULTIPLY -> cb * cs;
			case SCREEN -> cb + cs - cb * cs;
			case OVERLAY -> hardLight(cs, cb);
			case DARKEN -> Math.min(cb, cs);
			case LIGHTEN -> Math.max(cb, cs);
			case COLOR_DODGE -> cb <= 0f ? 0f : (cs >= 1f ? 1f : Math.min(1f, cb / (1f - cs)));
			case COLOR_BURN -> cb >= 1f ? 1f : (cs <= 0f ? 0f : 1f - Math.min(1f, (1f - cb) / cs));
			case HARD_LIGHT -> hardLight(cb, cs);
			case SOFT_LIGHT -> {
				if (cs <= 0.5f) {
					yield cb - (1f - 2f * cs) * cb * (1f - cb);
				}
				final float d = cb <= 0.25f ? ((16f * cb - 12f) * cb + 4f) * cb : (float) Math.sqrt(cb);
				yield cb + (2f * cs - 1f) * (d - cb);
			}
			case DIFFERENCE -> Math.abs(cb - cs);
			case EXCLUSION -> cb + cs - 2f * cb * cs;
			default -> throw new IllegalArgumentException(mode.name());
		};
	}

	private static float hardLight(final float cb, final float cs) {
		if (cs <= 0.5f) {
			return cb * 2f * cs;
		}
		final float s = 2f * cs - 1f;
		return cb + s - cb * s;
	}

	private static float lum(final float[] c) {
		return 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2];
	}

	private static float sat(final float[] c) {
		return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2]));
	}

	private static float[] setLum(final float[] c, final float l, final float[] out) {
		final float d = l - lum(c);
		out[0] = c[0] + d;
		out[1] = c[1] + d;
		out[2] = c[2] + d;
		// ClipColor
		final float ll = lum(out);
		final float n = Math.min(out[0], Math.min(out[1], out[2]));
		final float x = Math.max(out[0], Math.max(out[1], out[2]));
		if (n < 0f) {
			for (int i = 0; i < 3; ++i) {
				out[i] = ll + (out[i] - ll) * ll / (ll - n);
			}
		}
		if (x > 1f) {
			for (int i = 0; i < 3; ++i) {
				out[i] = ll + (out[i] - ll) * (1f - ll) / (x - ll);
			}
		}
		return out;
	}

	private static float[] setSat(final float[] c, final float s, final float[] out) {
		int max = 0, min = 0;
		for (int i = 1; i < 3; ++i) {
			if (c[i] > c[max]) {
				max = i;
			}
			if (c[i] < c[min]) {
				min = i;
			}
		}
		if (max == min) {
			out[0] = out[1] = out[2] = 0f;
			return out;
		}
		final int mid = 3 - max - min;
		final float cmax = c[max], cmid = c[mid], cmin = c[min];
		out[mid] = (cmid - cmin) * s / (cmax - cmin);
		out[max] = s;
		out[min] = 0f;
		return out;
	}
}
