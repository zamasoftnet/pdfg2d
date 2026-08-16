package net.zamasoft.pdfg2d.pdf.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.font.PDFFont;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.SystemEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/** Verifies that metric-only fonts do not become unreachable PDF font objects. */
public class FontFlowResourceUseTest {

	private static String render(final boolean useResource) throws Exception {
		final var buffer = new ByteArrayOutputStream();
		final var builder = new StreamFragmentedOutput(buffer);
		final var pdf = new PDFWriterImpl(builder, PDFParams.createDefault());
		try (final var page = pdf.nextPage(100, 100)) {
			final var source = new SystemEmbeddedCIDFontSource(new Font(Font.SERIF, Font.PLAIN, 12));
			final var font = (PDFFont) pdf.useFont(source);
			final var gid = font.toGID('A');
			font.getAdvance(gid); // The metrics path reserves the top-level font ref.
			if (useResource) {
				page.useResource("Font", font.getName());
			}
		}
		pdf.close();
		builder.close();
		return buffer.toString(StandardCharsets.ISO_8859_1);
	}

	@Test
	public void metricOnlyFontLeavesNoFontDictionaryOrProgram() throws Exception {
		final var pdf = render(false);
		assertFalse(pdf.contains("/Type /Font"));
		assertFalse(pdf.contains("/FontDescriptor"));
		assertFalse(pdf.contains("/FontFile"));
		assertTrue(pdf.matches("(?s).*\\d+ 0 obj\\s+null\\s+endobj.*"),
				"the reserved object number must remain a valid indirect object");
	}

	@Test
	public void resourceFontIsStillEmbedded() throws Exception {
		final var pdf = render(true);
		assertTrue(pdf.contains("/Type /Font"));
		assertTrue(pdf.contains("/FontDescriptor"));
		assertTrue(pdf.contains("/FontFile"));
	}
}
