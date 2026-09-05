package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.color.ColorConverter;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;

/** 出力インテントICCによるsRGB→CMYK変換の性質試験です。 */
public class ColorConverterTest {
	private static ColorConverter converter() throws IOException {
		try (final var in = PDFWriterImpl.class.getResourceAsStream("ISOcoated_v2_300_eci.icc")) {
			return new ColorConverter(in.readAllBytes());
		}
	}

	@Test
	public void testProcessColorsHaveExpectedInkCharacteristics() throws Exception {
		final var converter = converter();
		final var red = converter.toCMYK(1, 0, 0);
		assertTrue(red[0] < .05f, "red C");
		assertTrue(red[1] > .85f, "red M");
		assertTrue(red[2] > .85f, "red Y");
		assertTrue(red[3] < .05f, "red K");

		final var blue = converter.toCMYK(0, 0, 1);
		assertTrue(blue[0] > .85f, "blue C");
		assertTrue(blue[1] > .70f, "blue M");
		assertTrue(blue[2] < .05f, "blue Y");
	}

	@Test
	public void testNeutralColorsUseBlackOnly() throws Exception {
		final var converter = converter();
		assertArrayEquals(new float[] { 0, 0, 0, 1 }, converter.toCMYK(0, 0, 0));
		assertArrayEquals(new float[] { 0, 0, 0, .5f }, converter.toCMYK(.5f, .5f, .5f));
		assertArrayEquals(new float[] { 0, 0, 0, 0 }, converter.toCMYK(1, 1, 1));
	}

	@Test
	public void testCachedResultIsEqualButNotShared() throws Exception {
		final var converter = converter();
		final var first = converter.toCMYK(.2f, .4f, .8f);
		final var second = converter.toCMYK(.2f, .4f, .8f);
		assertArrayEquals(first, second);
		assertNotSame(first, second);
	}

	@Test
	public void testNeutralRuleCanBeDisabledForGradientInterpolation() throws Exception {
		final var gray = converter().toCMYKNoNeutralRule(.5f, .5f, .5f);
		for (final var component : gray) {
			assertTrue(component > 0, "ICC gray must contain all four process components");
		}
	}
}
