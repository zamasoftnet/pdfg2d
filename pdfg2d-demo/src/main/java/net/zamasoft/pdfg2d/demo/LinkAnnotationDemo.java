package net.zamasoft.pdfg2d.demo;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;

import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.annot.LinkAnnot;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates how to create link annotations in a PDF.
 * <p>
 * This demo adds a clickable URI link to the PDF page.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class LinkAnnotationDemo {
	/**
	 * Entry point. Generates {@code output/annotation.pdf} containing a rotated
	 * rectangle with a clickable URI link annotation.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if an error occurs during PDF generation
	 */
	public static void main(final String[] args) throws Exception {
		final var params = PDFParams.createDefault()
				.withCompression(PDFParams.Compression.NONE)
				.withVersion(PDFParams.Version.V_1_7);

		final var width = 300.0;
		final var height = 300.0;

		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "annotation.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final PDFWriter pdf = new PDFWriterImpl(builder, params);

			try (final var page = pdf.nextPage(width, height);
					final var gc = new PDFGC(page)) {

				{
					var s = (Shape) new Rectangle2D.Double(10, 10, 280, 30);
					s = AffineTransform.getRotateInstance(.2).createTransformedShape(s);

					gc.draw(s);

					final var link = new LinkAnnot();
					link.setShape(s);
					link.setURI(URI.create("http://www.yahoo.co.jp/"));
					page.addAnnotation(link);
				}
			}

			pdf.close();
			builder.close();
		}
	}
}
