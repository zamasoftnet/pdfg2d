package net.zamasoft.pdfg2d.demo;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import javax.swing.JFrame;

import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates transparency groups in PDF.
 * <p>
 * Creates nested group images with transparency effects
 * and renders them onto the PDF page.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class TransparencyGroupDemo {
	/**
	 * Entry point. Generates {@code output/group-image.pdf} and opens a Swing
	 * preview window showing the nested transparency groups.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if an error occurs during PDF generation
	 */
	public static void main(final String[] args) throws Exception {
		final var params = PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);

		final var width = 300.0;
		final var height = 300.0;

		// Create PDF with transparency groups
		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "group-image.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final PDFWriter pdf = new PDFWriterImpl(builder, params);

			try (final var page = pdf.nextPage(width, height);
					final var gc = new PDFGC(page)) {
				draw(gc);
			}

			// Display in Swing frame for comparison
			final var frame = new JFrame("Graphics") {
				private static final long serialVersionUID = 1L;

				@Override
				public void paint(final Graphics g) {
					super.paint(g);
					final var g2d = (Graphics2D) g;
					final var gc = new G2DGC(g2d, pdf.getFontManager());
					TransparencyGroupDemo.draw(gc);
				}

			};
			frame.setSize((int) width, (int) height);
			frame.setVisible(true);

			pdf.close();
			builder.close();
		}
	}

	/**
	 * Draws nested transparency groups.
	 * 
	 * @param gc graphics context
	 */
	static void draw(final GC gc) {
		gc.transform(AffineTransform.getRotateInstance(.1f));
		gc.setLineWidth(10);

		// Draw base rectangle
		{
			gc.setFillPaint(RGBColor.create(0, 1.0f, 0));
			gc.setStrokePaint(RGBColor.create(0, 1.0f, 1.0f));
			final var shape = new Rectangle2D.Double(50, 50, 100, 100);
			gc.fillDraw(shape);
		}

		Image gi1;
		{
			// Create first group image
			final var gc2 = gc.createGroupImage(300f, 300f);
			{
				gc2.setFillPaint(RGBColor.create(0, 0, 1.0f));
				gc2.setStrokePaint(RGBColor.create(1.0f, 0, 0));
				final var shape = new Rectangle2D.Double(100, 100, 100, 100);
				gc2.fillDraw(shape);
			}
			{
				gc2.setFillPaint(RGBColor.create(1.0f, 0, 0));
				gc2.setStrokePaint(RGBColor.create(0, 0, 1.0f));
				final var shape = new Rectangle2D.Double(130, 130, 100, 100);
				gc2.fillDraw(shape);
			}

			{
				// Create nested group image
				final var gc3 = gc2.createGroupImage(300f, 300f);
				{
					gc3.setFillPaint(RGBColor.create(1.0f, 0, 1.0f));
					gc3.setStrokePaint(RGBColor.create(1.0f, 1.0f, 0));
					final var shape = new Rectangle2D.Double(70, 70, 100, 100);
					gc3.fillDraw(shape);
				}
				gi1 = gc3.finish();
				gc2.setFillAlpha(.5f);
				gc2.drawImage(gi1);
			}
			final var gi = gc2.finish();
			gc.setFillAlpha(.5f);
			gc.drawImage(gi);

			// Draw nested group again without transparency
			gc.transform(AffineTransform.getTranslateInstance(100, 0));
			gc.setFillAlpha(1);
			gc.drawImage(gi1);
		}
	}
}
