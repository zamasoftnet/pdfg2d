package net.zamasoft.pdfg2d.g2d.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.awt.BasicStroke;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.GC.LineCap;
import net.zamasoft.pdfg2d.gc.GC.LineJoin;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;

/**
 * Unit tests for the AWT-to-pdfg2d conversion helpers in {@link G2DUtils}.
 */
public class G2DUtilsTest {

	@Test
	public void testDecodeLineCap() {
		assertEquals(LineCap.BUTT, G2DUtils.decodeLineCap((short) BasicStroke.CAP_BUTT));
		assertEquals(LineCap.ROUND, G2DUtils.decodeLineCap((short) BasicStroke.CAP_ROUND));
		assertEquals(LineCap.SQUARE, G2DUtils.decodeLineCap((short) BasicStroke.CAP_SQUARE));
	}

	@Test
	public void testDecodeLineJoin() {
		assertEquals(LineJoin.MITER, G2DUtils.decodeLineJoin((short) BasicStroke.JOIN_MITER));
		assertEquals(LineJoin.ROUND, G2DUtils.decodeLineJoin((short) BasicStroke.JOIN_ROUND));
		assertEquals(LineJoin.BEVEL, G2DUtils.decodeLineJoin((short) BasicStroke.JOIN_BEVEL));
	}

	@Test
	public void testFromAwtColorPreservesComponents() {
		final var color = G2DUtils.fromAwtColor(new java.awt.Color(255, 0, 0));
		assertEquals(1f, color.getRed(), 1e-6);
		assertEquals(0f, color.getGreen(), 1e-6);
		assertEquals(0f, color.getBlue(), 1e-6);
	}

	@Test
	public void testFromAwtColorWithAlpha() {
		final var color = G2DUtils.fromAwtColor(new java.awt.Color(0, 0, 255, 128));
		assertInstanceOf(RGBAColor.class, color);
		assertEquals(128f / 255f, ((RGBAColor) color).getComponent(RGBAColor.A), 1e-2);
	}

	@Test
	public void testToAwtColorRoundTrip() {
		final var awt = new java.awt.Color(12, 34, 56);
		final var back = G2DUtils.toAwtColor(G2DUtils.fromAwtColor(awt));
		assertEquals(awt.getRed(), back.getRed());
		assertEquals(awt.getGreen(), back.getGreen());
		assertEquals(awt.getBlue(), back.getBlue());
	}

	private static final net.zamasoft.pdfg2d.gc.paint.Color RED = net.zamasoft.pdfg2d.gc.paint.RGBColor.create(1, 0, 0);
	private static final net.zamasoft.pdfg2d.gc.paint.Color BLUE = net.zamasoft.pdfg2d.gc.paint.RGBColor.create(0, 0, 1);

	/**
	 * Gradients AWT refuses (2026-11-25): stops at the same position (a hard color change) and a gradient of no length
	 * or radius made the conversion fail when an element with a filter was rasterized (web.dev, stripe.com).
	 */
	@Test
	public void testHardStopsBecomeIncreasingFractions() {
		final var linear = G2DUtils.toAwtPaint(new net.zamasoft.pdfg2d.gc.paint.LinearGradient(0, 0, 10, 0,
				new double[] { 0, 0.1, 0.1, 1 }, new net.zamasoft.pdfg2d.gc.paint.Color[] { RED, RED, BLUE, BLUE },
				new java.awt.geom.AffineTransform(), null));
		assertInstanceOf(java.awt.LinearGradientPaint.class, linear);
		final float[] f = ((java.awt.LinearGradientPaint) linear).getFractions();
		for (int i = 1; i < f.length; ++i) {
			org.junit.jupiter.api.Assertions.assertTrue(f[i] > f[i - 1], java.util.Arrays.toString(f));
		}
		assertEquals(0.1f, f[2], 1e-6);
		final var radial = G2DUtils.toAwtPaint(new net.zamasoft.pdfg2d.gc.paint.RadialGradient(0, 0, 10, 0, 0,
				new double[] { 0, 1, 1 }, new net.zamasoft.pdfg2d.gc.paint.Color[] { RED, BLUE, RED },
				new java.awt.geom.AffineTransform(), null));
		assertInstanceOf(java.awt.RadialGradientPaint.class, radial);
		final float[] g = ((java.awt.RadialGradientPaint) radial).getFractions();
		assertEquals(1f, g[2]);
		org.junit.jupiter.api.Assertions.assertTrue(g[0] < g[1] && g[1] < g[2], java.util.Arrays.toString(g));
	}

	@Test
	public void testGradientOfNoLengthIsItsLastColor() {
		final var linear = G2DUtils.toAwtPaint(new net.zamasoft.pdfg2d.gc.paint.LinearGradient(5, 5, 5, 5,
				new double[] { 0, 1 }, new net.zamasoft.pdfg2d.gc.paint.Color[] { RED, BLUE },
				new java.awt.geom.AffineTransform(), null));
		assertEquals(java.awt.Color.BLUE, linear);
		final var radial = G2DUtils.toAwtPaint(new net.zamasoft.pdfg2d.gc.paint.RadialGradient(5, 5, 0, 5, 5,
				new double[] { 0, 1 }, new net.zamasoft.pdfg2d.gc.paint.Color[] { RED, BLUE },
				new java.awt.geom.AffineTransform(), null));
		assertEquals(java.awt.Color.BLUE, radial);
		// a radius that is 0 as a float
		final var tiny = G2DUtils.toAwtPaint(new net.zamasoft.pdfg2d.gc.paint.RadialGradient(5, 5, 1e-50, 5, 5,
				new double[] { 0, 1 }, new net.zamasoft.pdfg2d.gc.paint.Color[] { RED, BLUE },
				new java.awt.geom.AffineTransform(), null));
		assertEquals(java.awt.Color.BLUE, tiny);
	}
}
