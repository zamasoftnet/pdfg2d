package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Integration tests for Optional Content Group (OCG) watermarks whose
 * visibility differs between screen and print — content that is invisible in
 * a viewer but appears when the user prints, and vice versa.
 * <p>
 * This is a core capability for the downstream HTML+CSS print layout product,
 * so both the catalog wiring ({@code /OCProperties} with usage application
 * dictionaries) and the per-OCG {@code /Usage} states are verified.
 * </p>
 */
public class PrintOnlyWatermarkTest {

	/** Creates a PDF whose watermark group carries the given OCG flags. */
	private File generate(final String name, final int ocgFlags) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withVersion(PDFParams.Version.V_1_5));

			final var page = pdf.nextPage(595, 842);
			try (final var gc = new PDFGC(page)) {
				// Normal, always-visible content
				gc.setFillPaint(RGBColor.create(0, 0, 1));
				gc.fill(new Rectangle2D.Double(50, 50, 200, 100));

				// Watermark drawn into an optional-content Form XObject
				final var group = pdf.createGroupImage(595, 842);
				group.setOCG(ocgFlags);
				try (final var wgc = new PDFGC(group)) {
					wgc.setFillPaint(RGBColor.create(1, 0.8f, 0.8f));
					wgc.fill(new Rectangle2D.Double(100, 300, 400, 200));
				}
				gc.drawImage(group);
			}
			pdf.close();
			builder.close();
		}
		return file;
	}

	@Test
	public void testPrintOnlyWatermark() throws Exception {
		final var file = generate("print_only_watermark.pdf", PDFGroupImage.VIEW_OFF);

		// The document must remain structurally valid and expose OCProperties.
		try (final var doc = Loader.loadPDF(file)) {
			final var oc = doc.getDocumentCatalog().getOCProperties();
			assertNotNull(oc, "Catalog must contain /OCProperties for the OCG watermark");
			assertTrue(oc.getGroupNames().length > 0, "At least one OCG must be registered");
		}

		// Object dictionaries are uncompressed, so the usage states can be
		// checked directly in the file bytes.
		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/ViewState /OFF"), "Watermark must be hidden on screen");
		assertTrue(raw.contains("/PrintState /ON"), "Watermark must be visible when printed");
		assertTrue(raw.contains("/OC "), "Form XObject must reference its OCG via /OC");
	}

	@Test
	public void testScreenOnlyContent() throws Exception {
		final var file = generate("screen_only_content.pdf", PDFGroupImage.PRINT_OFF);

		try (final var doc = Loader.loadPDF(file)) {
			assertNotNull(doc.getDocumentCatalog().getOCProperties());
		}

		final var raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains("/ViewState /ON"), "Content must be visible on screen");
		assertTrue(raw.contains("/PrintState /OFF"), "Content must be suppressed when printed");
	}
}
