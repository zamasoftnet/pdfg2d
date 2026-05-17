package net.zamasoft.pdfg2d.demo;

import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.ViewerPreferences;

/**
 * Demonstrates various viewer preferences.
 * <p>
 * This demo sets PDF viewer preferences such as full screen mode,
 * window centering, and display options like hiding the toolbar or menubar.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ViewerPreferencesDemo {
	/**
	 * Entry point. Generates {@code output/viewer-preferences.pdf}, a 10-page PDF
	 * with viewer preferences (page mode, print scaling, print range, etc.) set.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if an error occurs during PDF generation
	 */
	public static void main(final String[] args) throws Exception {
		final var params = createDemoParams();

		final var width = 300.0;
		final var height = 300.0;

		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "viewer-preferences.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final PDFWriter pdf = new PDFWriterImpl(builder, params);

			for (int i = 0; i < 10; ++i) {
				try (final var page = pdf.nextPage(width, height);
						final var gc = new PDFGC(page)) {
					page.setBleedBox(new Rectangle2D.Double(10, 10, 280, 280));

					gc.fill(new Rectangle2D.Double(10, 10, 280, 280));
				}
			}

			pdf.close();
			builder.close();
		}
	}

	static PDFParams createDemoParams() {
		final var params = PDFParams.createDefault()
				.withCompression(PDFParams.Compression.NONE)
				.withVersion(PDFParams.Version.V_1_7);

		final var meta = params.metaInfo();
		meta.setTitle("タイトル");

		final var prefs = params.viewerPreferences();
		prefs.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.THUMBS);
		prefs.setPickTrayByPDFSize(true);
		prefs.setPrintPageRange(new int[] { 2, 3, 5, 7, 8, 9 });
		prefs.setNumCopies(4);
		prefs.setViewArea(ViewerPreferences.AreaBox.BLEED);
		prefs.setViewClip(ViewerPreferences.AreaBox.BLEED);
		prefs.setPrintScaling(ViewerPreferences.PrintScaling.NONE);
		return params;
	}
}
