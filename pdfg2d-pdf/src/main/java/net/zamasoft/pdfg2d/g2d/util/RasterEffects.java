package net.zamasoft.pdfg2d.g2d.util;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * 層(ARGB ラスタ)に掛ける画素効果(2026-08-29)。ガウスぼかし・色行列・落とし影。
 *
 * <p>
 * 作業表現は {@code float[4][w*h]}(0=A, 1=R, 2=G, 3=B、0..1)。ぼかしと合成は
 * 乗算済み(premultiplied)で行い縁が暗くならないようにし、色行列だけは
 * CSS/SVG(feColorMatrix)と同じ非乗算 RGBA に対して掛ける。
 * </p>
 */
public final class RasterEffects {
	private RasterEffects() {
		// utility
	}

	/** ぼかし半径(3σ)を超える成分は無視できるとみなす。 */
	public static int kernelRadius(final double sigma) {
		return sigma > 0 ? (int) Math.ceil(3 * sigma) : 0;
	}

	/**
	 * ユーザー空間のσをデバイス空間へ写す(変換の面積倍率の平方根)。
	 * 異方性のある変換(縦横で倍率が違う)は平均化される。
	 */
	public static double deviceSigma(final AffineTransform at, final double sigma) {
		if (!(sigma > 0)) {
			return 0;
		}
		final double det = Math.abs(at.getDeterminant());
		return sigma * Math.sqrt(det);
	}

	/** 正規化したガウス核(長さ 2r+1)。 */
	public static float[] gaussianKernel(final double sigma) {
		final int r = kernelRadius(sigma);
		final float[] k = new float[2 * r + 1];
		double sum = 0;
		for (int i = -r; i <= r; ++i) {
			final double v = Math.exp(-(i * (double) i) / (2 * sigma * sigma));
			k[i + r] = (float) v;
			sum += v;
		}
		for (int i = 0; i < k.length; ++i) {
			k[i] /= (float) sum;
		}
		return k;
	}

	/**
	 * 1面をガウスぼかしする(分離可能な畳み込み、面の外は 0)。
	 *
	 * @param plane w*h の値。書き換えられる
	 */
	public static void gaussianBlur(final float[] plane, final int w, final int h, final double sigma) {
		if (!(sigma > 0) || w <= 0 || h <= 0) {
			return;
		}
		final float[] k = gaussianKernel(sigma);
		final int r = k.length / 2;
		final float[] tmp = new float[w * h];
		// 横
		for (int y = 0; y < h; ++y) {
			final int row = y * w;
			for (int x = 0; x < w; ++x) {
				float sum = 0;
				final int lo = Math.max(-r, -x), hi = Math.min(r, w - 1 - x);
				for (int i = lo; i <= hi; ++i) {
					sum += plane[row + x + i] * k[i + r];
				}
				tmp[row + x] = sum;
			}
		}
		// 縦
		for (int x = 0; x < w; ++x) {
			for (int y = 0; y < h; ++y) {
				float sum = 0;
				final int lo = Math.max(-r, -y), hi = Math.min(r, h - 1 - y);
				for (int i = lo; i <= hi; ++i) {
					sum += tmp[(y + i) * w + x] * k[i + r];
				}
				plane[y * w + x] = sum;
			}
		}
	}

	/** 4面すべてをぼかす。 */
	public static void gaussianBlur(final float[][] planes, final int w, final int h, final double sigma) {
		for (final float[] plane : planes) {
			gaussianBlur(plane, w, h, sigma);
		}
	}

	/**
	 * TYPE_INT_ARGB / TYPE_INT_ARGB_PRE の画像を面へ展開する。
	 * 画像が乗算済みなら面も乗算済み、そうでなければ非乗算のまま。
	 */
	public static float[][] toPlanes(final BufferedImage image) {
		final int w = image.getWidth(), h = image.getHeight();
		final int[] data = intData(image);
		final float[][] p = new float[4][w * h];
		for (int i = 0; i < w * h; ++i) {
			final int v = data[i];
			p[0][i] = (v >>> 24) / 255f;
			p[1][i] = ((v >> 16) & 0xFF) / 255f;
			p[2][i] = ((v >> 8) & 0xFF) / 255f;
			p[3][i] = (v & 0xFF) / 255f;
		}
		return p;
	}

	/** 乗算済みの面を TYPE_INT_ARGB_PRE の画像にする。 */
	public static BufferedImage toPremultipliedImage(final float[][] p, final int w, final int h) {
		final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
		final int[] data = intData(image);
		for (int i = 0; i < w * h; ++i) {
			final int a = to8(p[0][i]);
			final int r = Math.min(a, to8(p[1][i]));
			final int g = Math.min(a, to8(p[2][i]));
			final int b = Math.min(a, to8(p[3][i]));
			data[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
		return image;
	}

	private static int[] intData(final BufferedImage image) {
		if (image.getType() != BufferedImage.TYPE_INT_ARGB && image.getType() != BufferedImage.TYPE_INT_ARGB_PRE) {
			throw new IllegalArgumentException("ARGB image required: " + image.getType());
		}
		return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	}

	private static int to8(final float v) {
		final int i = Math.round(v * 255f);
		return i < 0 ? 0 : (i > 255 ? 255 : i);
	}

	/** 非乗算 → 乗算済み。 */
	public static void premultiply(final float[][] p) {
		final float[] a = p[0];
		for (int c = 1; c < 4; ++c) {
			final float[] plane = p[c];
			for (int i = 0; i < plane.length; ++i) {
				plane[i] *= a[i];
			}
		}
	}

	/**
	 * 乗算済み TYPE_INT_ARGB_PRE 画像をぼかした新しい画像を返す。
	 */
	public static BufferedImage blurPremultiplied(final BufferedImage image, final double sigma) {
		final int w = image.getWidth(), h = image.getHeight();
		final float[][] p = toPlanes(image);
		gaussianBlur(p, w, h, sigma);
		return toPremultipliedImage(p, w, h);
	}

	/**
	 * 4×5 の色行列(行優先、CSS/SVG feColorMatrix と同じ並び、値域 0..1)を
	 * 非乗算の面に掛けて 0..1 に丸める。
	 *
	 * <pre>
	 * R' = m0·R + m1·G + m2·B + m3·A + m4
	 * G' = m5·R + ...              + m9
	 * B' = m10·R + ...             + m14
	 * A' = m15·R + ...             + m19
	 * </pre>
	 */
	public static void applyColorMatrix(final float[][] p, final float[] m) {
		final float[] a = p[0], r = p[1], g = p[2], b = p[3];
		for (int i = 0; i < a.length; ++i) {
			final float ri = r[i], gi = g[i], bi = b[i], ai = a[i];
			r[i] = clamp(m[0] * ri + m[1] * gi + m[2] * bi + m[3] * ai + m[4]);
			g[i] = clamp(m[5] * ri + m[6] * gi + m[7] * bi + m[8] * ai + m[9]);
			b[i] = clamp(m[10] * ri + m[11] * gi + m[12] * bi + m[13] * ai + m[14]);
			a[i] = clamp(m[15] * ri + m[16] * gi + m[17] * bi + m[18] * ai + m[19]);
		}
	}

	private static float clamp(final float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	/** 全面を係数倍する(不透明度)。乗算済みの面に使う。 */
	public static void scale(final float[][] p, final float k) {
		for (final float[] plane : p) {
			for (int i = 0; i < plane.length; ++i) {
				plane[i] *= k;
			}
		}
	}

	/**
	 * 面を (dx, dy) 画素ずらした新しい面を返す(双一次補間、外は 0)。
	 */
	public static float[] shift(final float[] plane, final int w, final int h, final double dx, final double dy) {
		final float[] out = new float[w * h];
		final int ix = (int) Math.floor(dx), iy = (int) Math.floor(dy);
		final float fx = (float) (dx - ix), fy = (float) (dy - iy);
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				// out(x, y) = in(x - dx, y - dy)
				final int sx = x - ix, sy = y - iy;
				final float v00 = sample(plane, w, h, sx, sy);
				final float v10 = fx > 0 ? sample(plane, w, h, sx - 1, sy) : 0;
				final float v01 = fy > 0 ? sample(plane, w, h, sx, sy - 1) : 0;
				final float v11 = fx > 0 && fy > 0 ? sample(plane, w, h, sx - 1, sy - 1) : 0;
				out[y * w + x] = (1 - fx) * (1 - fy) * v00 + fx * (1 - fy) * v10 + (1 - fx) * fy * v01
						+ fx * fy * v11;
			}
		}
		return out;
	}

	private static float sample(final float[] plane, final int w, final int h, final int x, final int y) {
		return x < 0 || y < 0 || x >= w || y >= h ? 0f : plane[y * w + x];
	}

	/**
	 * 乗算済みの層に落とし影を付ける(層のシルエットをずらしてぼかし、色を付けて
	 * 層の下に置く)。層自体は書き換えず、合成結果の新しい面を返す。
	 *
	 * @param p     乗算済みの層
	 * @param dx    デバイス空間のずれ
	 * @param sigma デバイス空間のσ
	 * @param rgba  影の色(非乗算、0..1)
	 */
	public static float[][] dropShadow(final float[][] p, final int w, final int h, final double dx,
			final double dy, final double sigma, final float[] rgba) {
		final float[] sa = shift(p[0], w, h, dx, dy);
		gaussianBlur(sa, w, h, sigma);
		final float[][] out = new float[4][w * h];
		final float ca = rgba[3];
		final float[] cc = { rgba[0] * ca, rgba[1] * ca, rgba[2] * ca };
		for (int i = 0; i < w * h; ++i) {
			final float shadowA = sa[i] * ca;
			final float ia = p[0][i];
			// 層 over 影(どちらも乗算済み)
			out[0][i] = ia + shadowA * (1 - ia);
			out[1][i] = p[1][i] + sa[i] * cc[0] * (1 - ia);
			out[2][i] = p[2][i] + sa[i] * cc[1] * (1 - ia);
			out[3][i] = p[3][i] + sa[i] * cc[2] * (1 - ia);
		}
		return out;
	}
}
