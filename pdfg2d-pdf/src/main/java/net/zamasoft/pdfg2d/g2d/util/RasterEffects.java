package net.zamasoft.pdfg2d.g2d.util;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import net.zamasoft.pdfg2d.util.ColorUtils;

/**
 * Pixel effects for layers (ARGB rasters) (2026-08-29): Gaussian blur, color matrices, and drop shadows.
 *
 * <p>
 * The working representation is {@code float[4][w*h]} (0=A, 1=R, 2=G, 3=B, 0..1).
 * Blur and compositing use premultiplied values to avoid dark edges; only color matrices
 * operate on non-premultiplied RGBA, as in CSS/SVG (feColorMatrix).
 * </p>
 */
public final class RasterEffects {
	private RasterEffects() {
		// utility
	}

	/** Treats contributions beyond the blur radius (3σ) as negligible. */
	public static int kernelRadius(final double sigma) {
		return sigma > 0 ? (int) Math.ceil(3 * sigma) : 0;
	}

	/**
	 * Maps user-space σ to device space (square root of the transform's area scale).
	 * Averages anisotropic transforms (different horizontal and vertical scales).
	 */
	public static double deviceSigma(final AffineTransform at, final double sigma) {
		if (!(sigma > 0)) {
			return 0;
		}
		final double det = Math.abs(at.getDeterminant());
		return sigma * Math.sqrt(det);
	}

	/** Normalized Gaussian kernel (length 2r+1). */
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
	 * Applies Gaussian blur to one plane (separable convolution; values outside the plane are 0).
	 *
	 * @param plane w*h values; modified in place
	 */
	public static void gaussianBlur(final float[] plane, final int w, final int h, final double sigma) {
		if (!(sigma > 0) || w <= 0 || h <= 0) {
			return;
		}
		final float[] k = gaussianKernel(sigma);
		final int r = k.length / 2;
		final float[] tmp = new float[w * h];
		// Horizontal.
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
		// Vertical.
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

	/** Blurs all four planes. */
	public static void gaussianBlur(final float[][] planes, final int w, final int h, final double sigma) {
		for (final float[] plane : planes) {
			gaussianBlur(plane, w, h, sigma);
		}
	}

	/**
	 * Expands a TYPE_INT_ARGB / TYPE_INT_ARGB_PRE image into planes.
	 * Planes remain premultiplied if the image is premultiplied, otherwise non-premultiplied.
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

	/** Converts premultiplied planes to a TYPE_INT_ARGB_PRE image. */
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

	/** Non-premultiplied -> premultiplied. */
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
	 * Returns a new image with blur applied to a premultiplied TYPE_INT_ARGB_PRE image.
	 */
	public static BufferedImage blurPremultiplied(final BufferedImage image, final double sigma) {
		final int w = image.getWidth(), h = image.getHeight();
		final float[][] p = toPlanes(image);
		gaussianBlur(p, w, h, sigma);
		return toPremultipliedImage(p, w, h);
	}

	/**
	 * Applies a 4x5 color matrix (row-major, same order as CSS/SVG feColorMatrix, range 0..1)
	 * to non-premultiplied planes and clamps to 0..1.
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
			r[i] = ColorUtils.clamp01(m[0] * ri + m[1] * gi + m[2] * bi + m[3] * ai + m[4]);
			g[i] = ColorUtils.clamp01(m[5] * ri + m[6] * gi + m[7] * bi + m[8] * ai + m[9]);
			b[i] = ColorUtils.clamp01(m[10] * ri + m[11] * gi + m[12] * bi + m[13] * ai + m[14]);
			a[i] = ColorUtils.clamp01(m[15] * ri + m[16] * gi + m[17] * bi + m[18] * ai + m[19]);
		}
	}

	/** Multiplies all planes by a factor (opacity). Used for premultiplied planes. */
	public static void scale(final float[][] p, final float k) {
		for (final float[] plane : p) {
			for (int i = 0; i < plane.length; ++i) {
				plane[i] *= k;
			}
		}
	}

	/**
	 * Returns a new plane shifted by (dx, dy) pixels (bilinear interpolation; values outside are 0).
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
	 * Adds a drop shadow to a premultiplied layer (shifts and blurs the layer silhouette,
	 * colors it, and places it below the layer). Returns new planes containing the composited result
	 * without modifying the layer itself.
	 *
	 * @param p     premultiplied layer
	 * @param dx    device-space offset
	 * @param sigma device-space σ
	 * @param rgba  shadow color (non-premultiplied, 0..1)
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
			// Layer over shadow (both premultiplied).
			out[0][i] = ia + shadowA * (1 - ia);
			out[1][i] = p[1][i] + sa[i] * cc[0] * (1 - ia);
			out[2][i] = p[2][i] + sa[i] * cc[1] * (1 - ia);
			out[3][i] = p[3][i] + sa[i] * cc[2] * (1 - ia);
		}
		return out;
	}
}
