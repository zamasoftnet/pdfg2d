package net.zamasoft.pdfg2d.demo;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import javax.swing.JFrame;

import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates alpha transparency and line styles in PDF.
 * <p>
 * This demo draws shapes with different alpha values and stroke patterns
 * to illustrate how transparency and line dashes work in the PDF generation.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class TransparencyDemo {
	/**
	 * Entry point. Generates {@code output/alpha.pdf} and opens a Swing preview
	 * window showing the transparency and line-dash effects.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if an error occurs during PDF generation
	 */
	public static void main(final String[] args) throws Exception {
		final var params = PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);

		final var width = 300.0;
		final var height = 300.0;

		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "alpha.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder, params);

			try (final var gc = new PDFGC(pdf.nextPage(width, height))) {
				draw(gc);
			}

			final var frame = new JFrame("Graphics") {
				private static final long serialVersionUID = 1L;

				@Override
				public void paint(final Graphics g) {
					super.paint(g);
					final var g2d = (Graphics2D) g;
					final var gc = new G2DGC(g2d, pdf.getFontManager());
					TransparencyDemo.draw(gc);
				}

			};
			frame.setSize((int) width, (int) height);
			frame.setVisible(true);

			pdf.close();
			builder.close();
		}
	}

	static void draw(final GC gc) {
		gc.transform(AffineTransform.getTranslateInstance(100, 0));
		gc.transform(AffineTransform.getRotateInstance(.1f));
		gc.setLineWidth(10);
		gc.setLinePattern(new double[] { 10, 10 });
		{
			gc.setFillPaint(RGBColor.create(0, 1.0f, 0));
			gc.setStrokePaint(RGBColor.create(0, 0, 1.0f));
			final Shape shape = new Rectangle2D.Double(50, 50, 100, 100);
			gc.fillDraw(shape);
		}
		gc.transform(AffineTransform.getRotateInstance(.2f));
		{
			try (final var gcState = gc.begin()) {
				gc.setFillPaint(RGBAColor.create(0, 1.0f, 0, .5f));
				gc.setStrokePaint(RGBAColor.create(0, 0, 1.0f, .5f));
				final Shape shape = new Rectangle2D.Double(50, 50, 100, 100);
				gc.fillDraw(shape);
			}
		}
		gc.transform(AffineTransform.getRotateInstance(.2f));
		{
			gc.setLineWidth(20);
			gc.setFillPaint(RGBColor.create(0, 1.0f, 0));
			gc.setStrokePaint(RGBColor.create(0, 0, 1.0f));
			final Shape shape = new Rectangle2D.Double(50, 50, 100, 100);
			gc.fillDraw(shape);
		}
		gc.transform(AffineTransform.getRotateInstance(.2f));
		{
			gc.setLineWidth(10);
			gc.setFillPaint(RGBAColor.create(0, 1.0f, 0, .5f));
			gc.setStrokePaint(RGBAColor.create(0, 0, 1.0f, .5f));
			final Shape shape = new Rectangle2D.Double(50, 50, 100, 100);
			gc.fillDraw(shape);
		}
		gc.transform(AffineTransform.getRotateInstance(.2f));
		{
			gc.setLinePattern(new double[] { 10, 10 });
			gc.setFillPaint(RGBColor.create(0, 1.0f, 0));
			gc.setStrokePaint(RGBColor.create(0, 0, 1.0f));
			final Shape shape = new Rectangle2D.Double(50, 50, 100, 100);
			gc.fillDraw(shape);
		}
	}
}
