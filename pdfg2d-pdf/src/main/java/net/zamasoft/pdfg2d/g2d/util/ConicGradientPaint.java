package net.zamasoft.pdfg2d.g2d.util;

import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.SpreadMethod;

/**
 * {@link ConicGradient} を厳密に描く Java2D の {@link java.awt.Paint}(2026-08-29)。
 *
 * <p>
 * デバイス画素の中心を(GCの変換 ∘ グラデーションの変換)の逆で
 * グラデーション空間へ戻し、中心 (cx, cy) から見た角度を「真上=0、時計回り」
 * で測って {@code startAngle} を引き、1周=1 に正規化した位置で色を引く。
 * 色は CSS と同じく非乗算 sRGB で線形補間し、同じ位置の色停止は硬い境界になる。
 * {@link SpreadMethod#PAD} は色停止の範囲外を端の色で埋め(CSS の
 * conic-gradient で停止が 0..1 を覆わない場合と同じ)、REPEAT / REFLECT は
 * 停止範囲を周期として繰り返す。
 * </p>
 */
public final class ConicGradientPaint implements java.awt.Paint {
	private final double cx, cy, startAngle;

	private final double[] fractions;

	/** 非乗算 RGBA 0..1。 */
	private final float[][] rgba;

	private final AffineTransform transform;

	private final SpreadMethod spread;

	private final boolean opaque;

	public ConicGradientPaint(final ConicGradient gradient) {
		this.cx = gradient.cx();
		this.cy = gradient.cy();
		this.startAngle = gradient.startAngle();
		final double[] fs = gradient.fractions();
		final Color[] cs = gradient.colors();
		final int n = Math.min(fs.length, cs.length);
		this.fractions = new double[n];
		this.rgba = new float[n][4];
		boolean opaque = true;
		for (int i = 0; i < n; ++i) {
			this.fractions[i] = fs[i];
			final Color c = cs[i];
			this.rgba[i][0] = c.getRed();
			this.rgba[i][1] = c.getGreen();
			this.rgba[i][2] = c.getBlue();
			this.rgba[i][3] = c.getAlpha();
			opaque &= c.getAlpha() >= 1f;
		}
		this.opaque = opaque && n > 0;
		this.transform = gradient.transform() != null ? new AffineTransform(gradient.transform())
				: new AffineTransform();
		this.spread = gradient.spread() != null ? gradient.spread() : SpreadMethod.PAD;
	}

	@Override
	public int getTransparency() {
		return this.opaque ? Transparency.OPAQUE : Transparency.TRANSLUCENT;
	}

	@Override
	public PaintContext createContext(final ColorModel cm, final Rectangle deviceBounds,
			final Rectangle2D userBounds, final AffineTransform xform, final RenderingHints hints) {
		final AffineTransform toDevice = xform != null ? new AffineTransform(xform) : new AffineTransform();
		toDevice.concatenate(this.transform);
		AffineTransform deviceToGradient;
		try {
			deviceToGradient = toDevice.createInverse();
		} catch (final NoninvertibleTransformException e) {
			deviceToGradient = null;
		}
		return new Context(deviceToGradient);
	}

	/**
	 * 1周を 0..1 とした位置 {@code t}(任意の実数)の色を非乗算 ARGB で返す。
	 * テスト用に公開。
	 */
	public int colorAt(final double t) {
		final int n = this.fractions.length;
		if (n == 0) {
			return 0;
		}
		if (n == 1) {
			return pack(this.rgba[0]);
		}
		final double f0 = this.fractions[0];
		final double fl = this.fractions[n - 1];
		final double len = fl - f0;
		double u = t;
		switch (this.spread) {
			case REPEAT -> {
				if (len > 0) {
					u = f0 + mod(t - f0, len);
				}
			}
			case REFLECT -> {
				if (len > 0) {
					final double p = mod(t - f0, 2 * len);
					u = f0 + (p <= len ? p : 2 * len - p);
				}
			}
			default -> {
				// PAD: 停止範囲外は端の色
			}
		}
		if (u <= f0) {
			return pack(this.rgba[0]);
		}
		if (u >= fl) {
			return pack(this.rgba[n - 1]);
		}
		// u 以下の最後の停止(同値の停止は後ろを採る=硬い境界)
		int i = 0;
		for (int k = 1; k < n - 1; ++k) {
			if (this.fractions[k] <= u) {
				i = k;
			} else {
				break;
			}
		}
		final double fa = this.fractions[i], fb = this.fractions[i + 1];
		final float[] ca = this.rgba[i], cb = this.rgba[i + 1];
		final float w = fb > fa ? (float) ((u - fa) / (fb - fa)) : 0f;
		int argb = to8(ca[3] + (cb[3] - ca[3]) * w) << 24;
		argb |= to8(ca[0] + (cb[0] - ca[0]) * w) << 16;
		argb |= to8(ca[1] + (cb[1] - ca[1]) * w) << 8;
		argb |= to8(ca[2] + (cb[2] - ca[2]) * w);
		return argb;
	}

	private static double mod(final double v, final double m) {
		final double r = v % m;
		return r < 0 ? r + m : r;
	}

	private static int pack(final float[] c) {
		return (to8(c[3]) << 24) | (to8(c[0]) << 16) | (to8(c[1]) << 8) | to8(c[2]);
	}

	private static int to8(final float v) {
		final int i = Math.round(v * 255f);
		return i < 0 ? 0 : (i > 255 ? 255 : i);
	}

	private final class Context implements PaintContext {
		private final AffineTransform deviceToGradient;

		Context(final AffineTransform deviceToGradient) {
			this.deviceToGradient = deviceToGradient;
		}

		@Override
		public void dispose() {
			// nothing to release
		}

		@Override
		public ColorModel getColorModel() {
			return ColorModel.getRGBdefault();
		}

		@Override
		public Raster getRaster(final int x, final int y, final int w, final int h) {
			final WritableRaster raster = this.getColorModel().createCompatibleWritableRaster(w, h);
			final int[] pixels = new int[w * h];
			final AffineTransform inv = this.deviceToGradient;
			if (inv == null) {
				java.util.Arrays.fill(pixels, colorAt(0));
			} else {
				final double m00 = inv.getScaleX(), m01 = inv.getShearX(), m02 = inv.getTranslateX();
				final double m10 = inv.getShearY(), m11 = inv.getScaleY(), m12 = inv.getTranslateY();
				final double twoPi = 2 * Math.PI;
				int p = 0;
				for (int j = 0; j < h; ++j) {
					final double dy = y + j + 0.5;
					for (int i = 0; i < w; ++i) {
						final double dx = x + i + 0.5;
						final double gx = m00 * dx + m01 * dy + m02 - ConicGradientPaint.this.cx;
						final double gy = m10 * dx + m11 * dy + m12 - ConicGradientPaint.this.cy;
						// 真上 (0, -1) を 0 とし時計回り(画面座標)に増える角度
						final double angle = Math.atan2(gx, -gy) - ConicGradientPaint.this.startAngle;
						double t = angle / twoPi;
						t -= Math.floor(t);
						if (t >= 1) {
							t = 0;
						}
						pixels[p++] = colorAt(t);
					}
				}
			}
			raster.setDataElements(0, 0, w, h, pixels);
			return raster;
		}
	}
}
