package net.zamasoft.pdfg2d.demo;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import javax.swing.JFrame;

import net.zamasoft.zstream.resolver.protocol.file.FileSource;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.SimpleLayoutGlyphHandler;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates text outlining and stroking.
 * <p>
 * This demo shows how to render text with strokes (outlines) and fills color,
 * creating an effect where the character shapes are outlined.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class TextOutlineDemo {
	/**
	 * Entry point. Generates {@code output/text-stroke.pdf} and opens a Swing
	 * preview window showing the outlined text.
	 *
	 * @param args command-line arguments (not used)
	 * @throws Exception if a font or PDF output error occurs
	 */
	public static void main(final String[] args) throws Exception {
		final var params = PDFParams.createDefault().withCompression(PDFParams.Compression.NONE);

		try (final var fsm = new PDFFontSourceManager()) {
			{
				final var face = new FontFace();
				face.src = new FileSource(DemoUtils.getResourceFile("ipaexm.ttf"));
				face.fontFamily = FontFamilyList.create("IPAex明朝");
				fsm.addFontFace(face);
			}

			final var finalParams = params.withFontSourceManager(fsm);

			final var width = 300.0;
			final var height = 300.0;

			try (final var out = new BufferedOutputStream(
					new FileOutputStream(new File(DemoUtils.getOutputDir(), "text-stroke.pdf")))) {
				final var builder = new StreamFragmentedOutput(out);
				final var pdf = new PDFWriterImpl(builder, finalParams);

				try (final var gc = new PDFGC(pdf.nextPage(width, height))) {
					draw(gc);
				}

				final var frame = new JFrame("Graphics") {
					private static final long serialVersionUID = 1L;

					@Override
					public void paint(final Graphics g) {
						super.paint(g);
						final var g2d = (Graphics2D) g;
						g2d.translate(0, 24);
						g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						final var gc = new G2DGC(g2d, pdf.getFontManager());
						TextOutlineDemo.draw(gc);
					}
				};
				frame.setSize((int) width, (int) height);
				frame.setVisible(true);

				pdf.close();
				builder.close();
			}
		}
	}

	static void draw(final GC gc) {
		{
			gc.begin();
			gc.transform(AffineTransform.getTranslateInstance(250, 0));
			{
				gc.setStrokePaint(RGBColor.BLACK);
				gc.setFillPaint(RGBColor.WHITE);
				gc.setTextMode(GC.TextMode.FILL_STROKE);
				final var lgh = new SimpleLayoutGlyphHandler();
				lgh.setGC(gc);
				try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("ja"), lgh)) {
					tlf.setDirection(FontStyle.Direction.TB);
					tlf.setFontFamilies(FontFamilyList.create("IPAex明朝"));
					tlf.setFontSize(32);
					tlf.characters("白抜き");
					tlf.flush();
				}
			}
			gc.transform(AffineTransform.getTranslateInstance(-50, 0));
			{
				gc.setStrokePaint(RGBColor.BLACK);
				gc.setFillPaint(RGBColor.create(255, 0, 0));
				gc.setTextMode(GC.TextMode.FILL);
				final var lgh = new SimpleLayoutGlyphHandler();
				lgh.setGC(gc);
				try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("ja"), lgh)) {
					tlf.setDirection(FontStyle.Direction.TB);
					tlf.setFontFamilies(FontFamilyList.create("IPAex明朝"));
					tlf.setFontWeight(FontStyle.Weight.W_800);
					tlf.setFontSize(32);
					tlf.characters("太字");
					tlf.flush();
				}
			}
			gc.end();
		}
	}
}
