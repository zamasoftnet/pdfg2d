package net.zamasoft.pdfg2d.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * Unit tests for the color space conversions in {@link ColorUtils}, which
 * follow the formulas of the PDF specification (sections 6.2.1-6.2.3).
 */
public class ColorUtilsTest {

	@Test
	public void testRgbToGrayUsesLuminanceWeights() {
		assertEquals(1.0f, ColorUtils.toGray(1, 1, 1), 1e-6, "White stays white");
		assertEquals(0.0f, ColorUtils.toGray(0, 0, 0), 1e-6, "Black stays black");
		assertEquals(0.59f, ColorUtils.toGray(0, 1, 0), 1e-6, "Green carries the largest weight");
	}

	@Test
	public void testCmykToGray() {
		assertEquals(0.0f, ColorUtils.toGray(0, 0, 0, 1), 1e-6, "Full key is black");
		assertEquals(1.0f, ColorUtils.toGray(0, 0, 0, 0), 1e-6, "No ink is white");
	}

	@Test
	public void testRgbToCmykPureRed() {
		final var cmyk = ColorUtils.toCMYK(RGBColor.create(1, 0, 0));
		assertEquals(0f, cmyk.getComponent(CMYKColor.C), 1e-6);
		assertEquals(1f, cmyk.getComponent(CMYKColor.M), 1e-6);
		assertEquals(1f, cmyk.getComponent(CMYKColor.Y), 1e-6);
		assertEquals(0f, cmyk.getComponent(CMYKColor.K), 1e-6);
	}

	@Test
	public void testRgbToCmykBlackExtractsKey() {
		final var cmyk = ColorUtils.toCMYK(RGBColor.BLACK);
		assertEquals(0f, cmyk.getComponent(CMYKColor.C), 1e-6, "Under-color removal moves everything to K");
		assertEquals(0f, cmyk.getComponent(CMYKColor.M), 1e-6);
		assertEquals(0f, cmyk.getComponent(CMYKColor.Y), 1e-6);
		assertEquals(1f, cmyk.getComponent(CMYKColor.K), 1e-6);
	}

	@Test
	public void testGrayToCmykMapsToKey() {
		final var cmyk = ColorUtils.toCMYK(GrayColor.create(0.25f));
		assertEquals(0.75f, cmyk.getComponent(CMYKColor.K), 1e-6);
		assertEquals(0f, cmyk.getComponent(CMYKColor.C), 1e-6);
	}

	@Test
	public void testToGrayIsIdentityForGray() {
		final var gray = GrayColor.create(0.5f);
		assertSame(gray, ColorUtils.toGray(gray));
	}

	@Test
	public void testCmykPassesThroughUnchanged() {
		final var cmyk = CMYKColor.create(0.1f, 0.2f, 0.3f, 0.4f);
		assertSame(cmyk, ColorUtils.toCMYK(cmyk));
	}
}
