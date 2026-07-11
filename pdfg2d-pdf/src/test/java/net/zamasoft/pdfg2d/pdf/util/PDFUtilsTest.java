package net.zamasoft.pdfg2d.pdf.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link PDFUtils} name encoding and unit conversion.
 */
public class PDFUtilsTest {

	@Test
	public void testMmToPt() {
		assertEquals(72.0, PDFUtils.mmToPt(25.4), 1e-9, "25.4mm is one inch = 72pt");
		assertEquals(0.0, PDFUtils.mmToPt(0), 1e-9);
	}

	@Test
	public void testEncodeNamePassesPlainAsciiThrough() throws Exception {
		final var b = PDFUtils.encodeName("Type1", "UTF-8");
		assertEquals("Type1", new String(b, StandardCharsets.US_ASCII));
	}

	@Test
	public void testEncodeNameEscapesDelimiters() throws Exception {
		final var b = PDFUtils.encodeName("a/b(c)", "UTF-8");
		assertEquals("a#2Fb#28c#29", new String(b, StandardCharsets.US_ASCII));
	}

	@Test
	public void testEncodeNameEscapesSpaceAndNonAscii() throws Exception {
		final var b = PDFUtils.encodeName("A B", "UTF-8");
		assertEquals("A#20B", new String(b, StandardCharsets.US_ASCII));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Simple", "With Space", "hash#name", "paren(name)", "日本語", "mixed 日本語/slash" })
	public void testEncodeDecodeRoundTrip(final String name) throws Exception {
		final var encoded = new String(PDFUtils.encodeName(name, "UTF-8"), StandardCharsets.US_ASCII);
		assertEquals(name, PDFUtils.decodeName(encoded, "UTF-8"));
	}
}
