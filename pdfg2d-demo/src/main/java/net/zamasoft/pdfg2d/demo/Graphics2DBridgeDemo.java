package net.zamasoft.pdfg2d.demo;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.GeneralPath;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.AttributedString;

import javax.swing.JFrame;

import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.util.TextUtils;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates the {@link BridgeGraphics2D} adapter.
 * <p>
 * Bridges standard Java Graphics2D API to PDF output,
 * allowing AWT drawing commands to generate PDF content.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class Graphics2DBridgeDemo {
	/**
	 * Entry point. Generates a two-page {@code output/graphics2-d.pdf} and opens
	 * Swing preview windows for each page.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if an error occurs during PDF generation
	 */
	public static void main(final String[] args) throws Exception {
		final var width = 400.0;
		final var height = 400.0;
		final var x = 100;
		final var y = 100;

		final var params = PDFParams.createDefault();

		// Create multi-page PDF using BridgeGraphics2D
		try (final var out = new BufferedOutputStream(
				new FileOutputStream(new File(DemoUtils.getOutputDir(), "graphics2-d.pdf")))) {
			final var builder = new StreamFragmentedOutput(out);
			final PDFWriter pdf = new PDFWriterImpl(builder, params);

			{
				// First page: clipping and shapes
				try (final var gc = new PDFGC(pdf.nextPage(width, height))) {
					final var g = new BridgeGraphics2D(gc);
					draw1(g);
				}

				// Display in Swing frame
				final var frame = new JFrame("Graphics") {
					private static final long serialVersionUID = 1L;

					@Override
					public void paint(final Graphics g) {
						super.paint(g);
						Graphics2DBridgeDemo.draw1((Graphics2D) g);
					}

				};
				frame.setSize((int) width, (int) height);
				frame.setLocation(x, y);
				frame.setVisible(true);
			}

			{
				// Second page: text rendering
				try (final var gc = new PDFGC(pdf.nextPage(width, height))) {
					final var g = new BridgeGraphics2D(gc);
					draw2(g);
				}

				final var frame = new JFrame("Graphics") {
					private static final long serialVersionUID = 1L;

					@Override
					public void paint(final Graphics g) {
						super.paint(g);
						Graphics2DBridgeDemo.draw2((Graphics2D) g);
					}

				};
				frame.setSize((int) width, (int) height);
				frame.setLocation(x, y);
				frame.setVisible(true);
			}

			pdf.close();
			builder.close();
		}
	}

	/**
	 * Draws shapes with clipping and transparency.
	 */
	static void draw1(final Graphics2D g) {
		// Create complex clip path
		final var path = new GeneralPath();
		path.moveTo(150, 0);
		path.lineTo(20, 225);
		path.lineTo(0, 225);
		path.lineTo(0, 0);
		path.closePath();
		path.moveTo(150, 0);
		path.lineTo(280, 225);
		path.lineTo(300, 225);
		path.lineTo(300, 0);
		path.closePath();
		path.moveTo(0, 225);
		path.lineTo(300, 225);
		path.lineTo(300, 300);
		path.lineTo(0, 300);
		path.closePath();
		g.clip(path);

		// Draw shapes with dashed stroke
		g.setColor(Color.BLUE);
		g.setStroke(new BasicStroke(10, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1, new float[] { 10, 10 }, 0));
		g.drawOval(10, 10, 280, 280);

		// Draw semi-transparent rectangle
		g.setColor(Color.RED);
		final var comp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
		g.setComposite(comp);
		g.drawRect(20, 20, 260, 260);
	}

	/**
	 * Draws styled text with attributes.
	 */
	static void draw2(final Graphics2D g) {
		g.setFont(new Font("SansSerif", Font.PLAIN, 12));

		final var fm = g.getFontMetrics();
		System.out.println(fm.stringWidth("abc"));

		// Horizontal text with mixed attributes
		final var text = new AttributedString("盗人を捕らえてみれば我が子なり\n斬りたくもあり斬りたくもなし");
		text.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, 0, 2);
		text.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, 3, 7);
		text.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, 16, 23);
		g.drawString(text.getIterator(), 0, 12);

		// Vertical text layout
		text.addAttribute(TextUtils.WRITING_MODE, FontStyle.Direction.TB, 0, 30);
		g.drawString(text.getIterator(), 300 - 6, 0);
	}
}
