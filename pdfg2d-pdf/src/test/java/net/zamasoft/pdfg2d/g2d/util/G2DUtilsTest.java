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
}
