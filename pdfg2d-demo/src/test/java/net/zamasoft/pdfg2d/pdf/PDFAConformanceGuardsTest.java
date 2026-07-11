package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.action.JavaScriptAction;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.V2EncryptionParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Regression tests for the PDF/A-1b and PDF/X-1a conformance guards:
 * features forbidden by ISO 19005-1 / ISO 15930-4 must be rejected up front,
 * and the emitted file structure must carry the required markers.
 */
public class PDFAConformanceGuardsTest {

	private File generate(final String name, final PDFParams params, final boolean withGroupImage) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
				gc.setFillPaint(RGBColor.create(0, 0, 1));
				gc.fill(new Rectangle2D.Double(50, 50, 100, 100));
				if (withGroupImage) {
					final var group = pdf.createGroupImage(200, 200);
					try (final var ggc = new PDFGC(group)) {
						ggc.setFillPaint(RGBColor.create(1, 0, 0));
						ggc.fill(new Rectangle2D.Double(0, 0, 100, 100));
					}
					gc.drawImage(group);
				}
			}
			pdf.close();
			builder.close();
		}
		return file;
	}

	@Test
	public void testXmpDeclaresConformanceLevelB() throws Exception {
		final var file = generate("pdfa_conformance_b.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFA1B), false);
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("pdfaid:part"), "PDF/A identification schema must be present");
		assertTrue(raw.contains(">B</pdfaid:conformance>"),
				"PDF/A-1b must declare conformance level B, not A");
		assertFalse(raw.contains(">A</pdfaid:conformance>"));
	}

	@Test
	public void testEncryptionRejectedForPdfX() {
		final var enc = new V2EncryptionParams();
		enc.setUserPassword("u");
		enc.setOwnerPassword("o");
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFX1A)
				.withEncryption(enc);
		assertThrows(IllegalArgumentException.class,
				() -> generate("pdfx_encrypted.pdf", params, false),
				"PDF/X forbids encryption");
	}

	@ParameterizedTest
	@EnumSource(value = PDFParams.Version.class, names = { "V_PDFA1B", "V_PDFX1A" })
	public void testJavaScriptOpenActionRejected(final PDFParams.Version version) {
		final var params = PDFParams.createDefault()
				.withVersion(version)
				.withOpenAction(new JavaScriptAction("app.alert('x');"));
		assertThrows(IllegalArgumentException.class,
				() -> generate("openaction_" + version + ".pdf", params, false),
				"JavaScript open actions are forbidden in PDF/A and PDF/X");
	}

	@ParameterizedTest
	@EnumSource(value = PDFParams.Version.class, names = { "V_PDFA1B", "V_PDFX1A" })
	public void testNoTransparencyGroupEmitted(final PDFParams.Version version) throws Exception {
		final var file = generate("no_transparency_group_" + version + ".pdf",
				PDFParams.createDefault().withVersion(version), true);
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertFalse(raw.contains("/Transparency"),
				version + " must not contain a transparency group XObject");
		assertTrue(raw.contains("/Subtype /Form"), "The Form XObject itself must still be emitted");
	}

	@Test
	public void testTransparencyGroupStillEmittedForPlainPdf() throws Exception {
		final var file = generate("transparency_group_17.pdf",
				PDFParams.createDefault().withVersion(PDFParams.Version.V_1_7), true);
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/Transparency"), "Plain PDF keeps the transparency group");
	}

	@ParameterizedTest
	@EnumSource(value = PDFParams.Version.class, names = { "V_1_4", "V_1_7", "V_PDFA1B", "V_PDFX1A" })
	public void testBinaryMarkerFollowsHeader(final PDFParams.Version version) throws Exception {
		final var file = generate("binary_marker_" + version + ".pdf",
				PDFParams.createDefault().withVersion(version), false);
		final var bytes = Files.readAllBytes(file.toPath());
		// Header line "%PDF-1.x" + CRLF, then "%" + 4 binary bytes
		final var header = new String(bytes, 0, 8, StandardCharsets.US_ASCII);
		assertTrue(header.startsWith("%PDF-1."), header);
		assertEquals('%', bytes[10] & 0xFF, "Comment marker expected after header line");
		for (var i = 11; i < 15; ++i) {
			assertTrue((bytes[i] & 0xFF) > 127,
					"Binary marker byte " + i + " must be > 127 but was " + (bytes[i] & 0xFF));
		}
	}
}
