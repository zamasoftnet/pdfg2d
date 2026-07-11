package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Structural tests for PDF/VT-1 (ISO 16612-2) output. There is no open-source
 * PDF/VT validator, so the required identification and document-part
 * structures are checked directly; the PDF/X-4 base requirements are covered
 * by the veraPDF/X-4 tests elsewhere.
 */
public class PDFVTConformanceTest {

	@Test
	public void testDPartHierarchyAndIdentification() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "pdfvt1_basic.pdf");
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_PDFVT1));
			for (var p = 0; p < 3; ++p) {
				try (final var gc = new PDFGC(pdf.nextPage(595, 842))) {
					gc.setFillPaint(RGBColor.create(0, 0.4f, 0.8f));
					gc.fill(new Rectangle2D.Double(50, 50 + p * 20, 200, 100));
				}
			}
			pdf.close();
			builder.close();
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		// PDF/VT-1 is a PDF/X-4 conforming file with its own identification
		assertTrue(raw.contains("(PDF/X-4)"), "PDF/VT-1 must identify as PDF/X-4");
		assertTrue(raw.contains("(PDF/VT-1)"), "GTS_PDFVTVersion must identify PDF/VT-1");
		assertTrue(raw.contains("/DPartRoot"), "Catalog must carry the document part root");
		assertTrue(raw.contains("/DPartRootNode"), "DPartRoot must reference the root node");
		assertTrue(raw.contains("/DParts"), "Root DPart must have child parts");
		assertTrue(raw.contains("/DPart "), "Pages must reference their document part");
		assertTrue(raw.contains("/Start"), "Leaf DPart must span its pages");

		try (final var doc = Loader.loadPDF(file)) {
			assertEquals(3, doc.getNumberOfPages());
			assertEquals(1.6f, doc.getVersion(), 0.01f, "PDF/VT-1 is based on PDF 1.6 (via X-4)");
		}
	}

	@Test
	public void testPdfVtInheritsPdfXRestrictions() {
		// Encryption is forbidden because PDF/VT-1 is a PDF/X-4 file
		final var enc = new net.zamasoft.pdfg2d.pdf.params.V2EncryptionParams();
		enc.setUserPassword("u");
		enc.setOwnerPassword("o");
		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_PDFVT1)
				.withEncryption(enc);
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
			final var builder = new StreamFragmentedOutput(java.io.OutputStream.nullOutputStream());
			new PDFWriterImpl(builder, params);
		});
	}
}
