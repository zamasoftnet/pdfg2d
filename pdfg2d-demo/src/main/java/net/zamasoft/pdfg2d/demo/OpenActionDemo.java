package net.zamasoft.pdfg2d.demo;

import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.action.JavaScriptAction;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates how to create an Open Action in a PDF.
 * <p>
 * This demo configures the PDF to execute a specific action (like showing a
 * dialog)
 * when the document is opened by the viewer.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class OpenActionDemo {
	/**
	 * Entry point. Generates {@code output/open-action.pdf} which executes a
	 * JavaScript alert when the document is opened in a compatible viewer.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if an error occurs during PDF generation
	 */
	public static void main(final String[] args) throws Exception {
		final var js = new JavaScriptAction("app.alert('Hello');");
		final var params = PDFParams.createDefault()
				.withCompression(PDFParams.Compression.NONE)
				.withVersion(PDFParams.Version.V_1_7)
				.withOpenAction(js);

		// final var width = 300.0;
		// final var height = 300.0;
		// Note: width and height are effectively final in original code.
		// Just ensuring logical consistency if any change needed.
		final var width = 300.0;
		final var height = 300.0;

		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "open-action.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final PDFWriter pdf = new PDFWriterImpl(builder, params);

			try (final var gc = new PDFGC(pdf.nextPage(width, height))) {
				final var rect = new Rectangle2D.Double(10, 10, 280, 10);
				gc.draw(rect);
			}

			pdf.close();
			builder.close();
		}
	}
}
