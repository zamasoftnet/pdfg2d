package net.zamasoft.pdfg2d.pdf.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.params.PDFParams.Compression;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** PDF object-level regression tests for horizontal/vertical subset sharing. */
public class OpenTypeEmbeddedFontSharingTest {
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");

	private static String render(final boolean useHorizontal, final boolean useVertical, final boolean verticalFirst)
			throws Exception {
		final var buffer = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(buffer);
		final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault().withCompression(Compression.NONE));
		try (final var page = pdf.nextPage(100, 100)) {
			final var horizontalSource = new OpenTypeEmbeddedCIDFontSource(FONT, 0, Direction.LTR);
			final var verticalSource = new OpenTypeEmbeddedCIDFontSource(FONT, 0, Direction.TB);
			final var horizontal = (PDFFont) pdf.useFont(horizontalSource);
			final var vertical = (PDFFont) pdf.useFont(verticalSource);
			if (verticalFirst) {
				vertical.toGID(0x3001); // IDEOGRAPHIC COMMA has a vertical alternate.
				horizontal.toGID('A');
			} else {
				horizontal.toGID('A');
				vertical.toGID(0x3001);
			}
			if (useHorizontal) {
				page.useResource("Font", horizontal.getName());
			}
			if (useVertical) {
				page.useResource("Font", vertical.getName());
			}
		}
		pdf.close();
		builder.close();
		return buffer.toString(StandardCharsets.ISO_8859_1);
	}

	private static int count(final String value, final String needle) {
		int count = 0;
		for (int from = 0; (from = value.indexOf(needle, from)) >= 0; from += needle.length()) {
			++count;
		}
		return count;
	}

	private static int descendantCount(final String pdf) {
		// CIDFontType0C (the FontFile3 subtype) has the descendant subtype as a
		// prefix, so remove those stream dictionaries from the textual count.
		return count(pdf, "/Subtype /CIDFontType0") - count(pdf, "/Subtype /CIDFontType0C");
	}

	@Test
	public void horizontalAndVerticalShareOnePhysicalProgram() throws Exception {
		final var pdf = render(true, true, false);
		assertEquals(2, count(pdf, "/Subtype /Type0"), "direction selection keeps two Type0 wrappers");
		assertEquals(1, descendantCount(pdf), "the descendant CIDFont is physical");
		assertEquals(1, count(pdf, "/FontFile3"), "the CFF subset is embedded once");
		assertEquals(2, count(pdf, "/ToUnicode"), "Unicode extraction remains direction-specific");
		assertTrue(pdf.contains("/Encoding /Identity-H"));
		assertTrue(pdf.contains("/Encoding /Identity-V"));
		assertTrue(pdf.contains("/W "));
		assertTrue(pdf.contains("/W2 "));

		// With uncompressed CMaps, verify that each Type0 wrapper maps only its
		// own CID and that changing registration order changes CID numbers, not
		// the extracted characters.
		assertEquals(1, count(pdf, "<0001> <0001> <0041>"));
		assertEquals(1, count(pdf, "<0002> <0002> <3001>"));
		final var reverse = render(true, true, true);
		assertEquals(1, count(reverse, "<0002> <0002> <0041>"));
		assertEquals(1, count(reverse, "<0001> <0001> <3001>"));
	}

	@Test
	public void unusedDirectionDoesNotEmitItsType0Wrapper() throws Exception {
		final var pdf = render(true, false, false);
		assertEquals(1, count(pdf, "/Subtype /Type0"));
		assertEquals(1, descendantCount(pdf));
		assertEquals(1, count(pdf, "/FontFile3"));
		assertTrue(pdf.contains("/Encoding /Identity-H"));
		assertFalse(pdf.contains("/Encoding /Identity-V"));
		assertTrue(pdf.matches("(?s).*\\d+ 0 obj\\s+null\\s+endobj.*"));
	}
}
