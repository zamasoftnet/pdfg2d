package net.zamasoft.pdfg2d.demo;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates embedding a raster image into a PDF.
 * <p>
 * This demo generates an in-memory image and draws it onto a PDF page.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class RasterImageDemo {
	/**
	 * Entry point. Generates {@code output/image.pdf} with a raster image drawn on
	 * the page.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if the PDF cannot be written
	 */
	public static void main(final String[] args) throws Exception {
		final var params = PDFParams.createDefault();

		final var width = 300.0;
		final var height = 300.0;

		// Create PDF output stream
		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "image.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final PDFWriter pdf = new PDFWriterImpl(builder, params);

			// Create a page and draw the image
			try (final var page = pdf.nextPage(width, height);
					final var gc = new PDFGC(page)) {
				final var image = pdf.addImage(createSampleImage());
				gc.drawImage(image);
			}

			pdf.close();
			builder.close();
		}
	}

	static BufferedImage createSampleImage() {
		final var image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = image.createGraphics();
		try {
			graphics.setPaint(new GradientPaint(0, 0, Color.ORANGE, 180, 180, Color.CYAN));
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.setColor(Color.BLACK);
			graphics.fillOval(30, 30, 120, 120);
			graphics.setColor(Color.WHITE);
			graphics.fillRect(72, 48, 36, 84);
		} finally {
			graphics.dispose();
		}
		return image;
	}
}
