package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.ViewerPreferences;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Integration tests for the {@code /ViewerPreferences} catalog dictionary,
 * including the print-oriented entries (duplex, print scaling, page range,
 * number of copies) that matter for the downstream print layout product.
 */
public class PDFViewerPreferencesTest {

	@Test
	public void testPrintRelatedPreferences() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "viewer_prefs_print.pdf");

		final var vp = new ViewerPreferences();
		vp.setDuplex(ViewerPreferences.Duplex.FLIP_LONG_EDGE);
		vp.setPrintScaling(ViewerPreferences.PrintScaling.NONE);
		vp.setPrintPageRange(new int[] { 1, 1 });
		vp.setNumCopies(2);
		vp.setPickTrayByPDFSize(true);

		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_1_7)
				.withViewerPreferences(vp);

		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			try (final var page = pdf.nextPage(595, 842)) {
				// empty page
			}
			pdf.close();
			builder.close();
		}

		try (final var doc = Loader.loadPDF(file)) {
			final var prefs = doc.getDocumentCatalog().getViewerPreferences();
			assertNotNull(prefs, "ViewerPreferences must be present in the catalog");
			assertEquals("DuplexFlipLongEdge", prefs.getDuplex());
			assertEquals("None", prefs.getPrintScaling());
			assertEquals(2, prefs.getCOSObject().getInt(org.apache.pdfbox.cos.COSName.getPDFName("NumCopies")));
			assertTrue(prefs.getCOSObject().getBoolean(
					org.apache.pdfbox.cos.COSName.getPDFName("PickTrayByPDFSize"), false));
		}
	}

	@Test
	public void testWindowPreferences() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "viewer_prefs_window.pdf");

		final var vp = new ViewerPreferences();
		vp.setHideToolbar(true);
		vp.setHideMenubar(true);
		vp.setFitWindow(true);
		vp.setCenterWindow(true);

		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_1_7)
				.withViewerPreferences(vp);

		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			try (final var page = pdf.nextPage(595, 842)) {
				// empty page
			}
			pdf.close();
			builder.close();
		}

		try (final var doc = Loader.loadPDF(file)) {
			final var prefs = doc.getDocumentCatalog().getViewerPreferences();
			assertNotNull(prefs);
			assertTrue(prefs.hideToolbar());
			assertTrue(prefs.hideMenubar());
			assertTrue(prefs.fitWindow());
			assertTrue(prefs.centerWindow());
		}
	}

	@Test
	public void testVersionGatedPreferenceRejectsOldTarget() throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), "viewer_prefs_gate.pdf");

		final var vp = new ViewerPreferences();
		vp.setDuplex(ViewerPreferences.Duplex.SIMPLEX); // requires PDF 1.7

		final var params = PDFParams.createDefault()
				.withVersion(PDFParams.Version.V_1_4)
				.withViewerPreferences(vp);

		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);
			try (final var page = pdf.nextPage(595, 842)) {
				// empty page
			}
			assertThrows(UnsupportedOperationException.class, pdf::close,
					"Duplex on PDF 1.4 must be rejected");
			builder.close();
		}
	}
}
