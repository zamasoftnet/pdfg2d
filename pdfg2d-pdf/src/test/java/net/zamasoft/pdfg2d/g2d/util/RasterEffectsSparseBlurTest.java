package net.zamasoft.pdfg2d.g2d.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The blur of solid layers that convolves only near the edges (2026-10-09): it must give what the full blur gives
 * (up to rounding), and its work must stay near the edges. The work is checked by the number of convolved pixels,
 * not by time.
 */
public class RasterEffectsSparseBlurTest {
	private static final double[] SIGMAS = { 0.4, 1.5625, 4, 12 };

	private static List<Shape> shapes() {
		return List.of(new Rectangle2D.Double(20.3, 15.7, 300.4, 180.2), // a code block's shadow
				new RoundRectangle2D.Double(10, 12, 200, 120, 24, 24), //
				new Ellipse2D.Double(30, 20, 150, 90), //
				new Rectangle2D.Double(0, 0, 360, 230), // reaches the edges of the layer
				new Rectangle2D.Double(40, 40, 1.5, 120)); // thinner than the kernel
	}

	private static BufferedImage layer(final Shape shape, final Color color, final int w, final int h) {
		final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
		final var g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setPaint(color);
			g.fill(shape);
		} finally {
			g.dispose();
		}
		return image;
	}

	private static int[] data(final BufferedImage image) {
		return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	}

	@Test
	public void testSparseBlurIsTheFullBlur() {
		final int w = 360, h = 230;
		for (final Shape shape : shapes()) {
			final int[] src = data(layer(shape, Color.BLACK, w, h));
			for (final double sigma : SIGMAS) {
				final float[] full = new float[w * h], sparse = new float[w * h];
				for (int i = 0; i < src.length; ++i) {
					full[i] = sparse[i] = (src[i] >>> 24) / 255f;
				}
				RasterEffects.gaussianBlur(full, w, h, sigma);
				RasterEffects.gaussianBlurSparse(sparse, w, h, sigma);
				for (int i = 0; i < full.length; ++i) {
					assertEquals(full[i], sparse[i], 1e-5, shape + " sigma=" + sigma + " at " + (i % w) + "," + (i / w));
				}
			}
		}
	}

	@Test
	public void testSparseBlurOfNoiseConvolvesEverything() {
		// Nothing is uniform: every pixel is convolved and the result is the full blur's, bit for bit.
		final int w = 64, h = 48;
		final Random random = new Random(20261009L);
		final float[] full = new float[w * h], sparse = new float[w * h];
		for (int i = 0; i < full.length; ++i) {
			full[i] = sparse[i] = random.nextInt(256) / 255f;
		}
		RasterEffects.gaussianBlur(full, w, h, 2);
		final long work = RasterEffects.gaussianBlurSparse(sparse, w, h, 2);
		assertTrue(work > 2L * w * h * 9 / 10, "work=" + work);
		for (int i = 0; i < full.length; ++i) {
			assertEquals(Float.floatToIntBits(full[i]), Float.floatToIntBits(sparse[i]), "at " + i);
		}
	}

	@Test
	public void testSolidBlurIsBlurPremultiplied() {
		final int w = 360, h = 230;
		final Color[] colors = { new Color(0, 0, 0, 26), new Color(0, 0, 0, 255), new Color(30, 144, 255, 153),
				new Color(255, 200, 0, 64) };
		for (final Shape shape : shapes()) {
			for (final Color color : colors) {
				for (final double sigma : SIGMAS) {
					final BufferedImage expected = RasterEffects.blurPremultiplied(layer(shape, color, w, h), sigma);
					final BufferedImage actual = layer(shape, color, w, h);
					RasterEffects.blurSolidInPlace(actual, sigma, color.getRed() / 255f, color.getGreen() / 255f,
							color.getBlue() / 255f);
					final int[] e = data(expected), a = data(actual);
					final boolean black = color.getRed() == 0 && color.getGreen() == 0 && color.getBlue() == 0;
					int alphaDiffs = 0;
					for (int i = 0; i < e.length; ++i) {
						final String at = shape + " " + color + " sigma=" + sigma + " at " + (i % w) + "," + (i / w);
						final int alpha = Math.abs((e[i] >>> 24) - (a[i] >>> 24));
						assertTrue(alpha <= 1, at);
						alphaDiffs += alpha;
						for (int shift = 0; shift < 24; shift += 8) {
							final int d = Math.abs(((e[i] >> shift) & 0xFF) - ((a[i] >> shift) & 0xFF));
							// Black has no color planes to round; other colors are rebuilt from the alpha plane.
							assertTrue(d <= (black ? 0 : 1), at + " channel " + shift + ": " + d);
						}
					}
					// Copied values skip the kernel's float rounding; it may move a handful of alphas by one.
					assertTrue(alphaDiffs <= w * h / 1000, shape + " " + color + " sigma=" + sigma + ": " + alphaDiffs);
				}
			}
		}
	}

	@Test
	public void testWorkStaysNearTheEdges() {
		// A 499x350pt code block at 150dpi with a 2px (sigma 0.75pt) shadow: the layer Docusaurus' pages blurred.
		final double scale = 150 / 72.0, sigma = 0.75 * scale;
		final int pad = RasterEffects.kernelRadius(sigma);
		final int w = (int) Math.ceil(499 * scale) + 2 * pad, h = (int) Math.ceil(350 * scale) + 2 * pad;
		final BufferedImage image = layer(new Rectangle2D.Double(pad, pad, 499 * scale, 350 * scale),
				new Color(0, 0, 0, 26), w, h);
		final long work = RasterEffects.blurSolidInPlace(image, sigma, 0, 0, 0);
		// Each pass convolves about the perimeter times the kernel width (2r+1 = 11 here), not the area.
		final long band = 2L * (w + h) * (2 * pad + 1) * 2;
		assertTrue(work > 0 && work <= band, "work=" + work + " band=" + band + " area=" + (2L * w * h));
		assertTrue(work * 10 < 2L * w * h, "work=" + work + " area=" + (2L * w * h));
	}
}
