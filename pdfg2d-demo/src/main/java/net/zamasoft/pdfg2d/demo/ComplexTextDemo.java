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
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.SimpleLayoutGlyphHandler;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;

/**
 * Demonstrates complex text layout capabilities.
 * <p>
 * This demo shows support for various languages (Japanese, Thai, Korean, etc.),
 * vertical text layout, and font substitution/fallback mechanisms.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ComplexTextDemo {
	/**
	 * Entry point. Generates {@code output/text.pdf} and opens a Swing preview
	 * window showing the complex text layout.
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
			{
				final var face = new FontFace();
				face.src = new FileSource(DemoUtils.getResourceFile("KentenGeneric.otf"));
				face.fontFamily = FontFamilyList.create("Kenten Generic");
				fsm.addFontFace(face);
			}
			{
				final var face = new FontFace();
				face.src = new FileSource(DemoUtils.getResourceFile("UnDotum.ttf"));
				face.fontFamily = FontFamilyList.create("Hangul");
				fsm.addFontFace(face);
			}
			{
				final var face = new FontFace();
				face.src = new FileSource(DemoUtils.getResourceFile("FT Meuang BL-Regular.ttf"));
				face.fontFamily = FontFamilyList.create("FT Meuang");
				fsm.addFontFace(face);
			}

			final var finalParams = params.withFontSourceManager(fsm);

			final var width = 300.0;
			final var height = 300.0;

			try (final var out = new BufferedOutputStream(
					new FileOutputStream(new File(DemoUtils.getOutputDir(), "text.pdf")))) {
				final var builder = new StreamFragmentedOutput(out);
				final PDFWriter pdf = new PDFWriterImpl(builder, finalParams);

				try (final var gc = new PDFGC(pdf.nextPage(width, height))) {
					draw(gc);
				}

				pdf.close();
				builder.close();
			}

			final var fm = new FontManagerImpl(fsm);
			try {
				final var frame = new JFrame("Graphics") {
					private static final long serialVersionUID = 1L;

					@Override
					public void paint(final Graphics g) {
						super.paint(g);
						final var g2d = (Graphics2D) g;
						g2d.translate(0, 24);
						g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						final var gc = new G2DGC(g2d, fm);
						ComplexTextDemo.draw(gc);
					}
				};
				frame.setSize((int) width, (int) height);
				frame.setVisible(true);
			} finally {
				// fm does not need close? Check implementation.
				// The original code tried to close it.
				// If FontManagerImpl is closeable but FontManager is not, cast and close?
				if (fm instanceof AutoCloseable autoCloseable) {
					autoCloseable.close();
				}
			}
		}
	}

	static void draw(final GC gc) {
		{
			try (final var gcState = gc.begin()) {
				gc.transform(AffineTransform.getTranslateInstance(200, 0));
				final var lgh = new SimpleLayoutGlyphHandler();
				lgh.setGC(gc);
				try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("ja"), lgh)) {
					// Set text direction to top-to-bottom (vertical)
					tlf.setDirection(FontStyle.Direction.TB);
					tlf.setFontFamilies(FontFamilyList.create("IPAex明朝"));
					tlf.setFontSize(16);
					tlf.characters("盗人を捕らえてみれば我が子なり\n斬りたくもあり斬りたくもなし\n");

					// Change font style to oblique
					tlf.setFontStyle(FontStyle.Style.OBLIQUE);
					tlf.characters("盗人を捕らえてみれば我が子なり\n斬りたくもあり斬りたくもなし\n");
					// Reset font style to normal
					tlf.setFontStyle(FontStyle.Style.NORMAL);
					tlf.characters("The thief caught turn out to be one's own son.\n");
					tlf.setFontStyle(FontStyle.Style.OBLIQUE);
					tlf.characters("The thief caught turn out to be one's own son.\n");
					tlf.flush();
				}
			}
		}

		{
			try (final var gcState = gc.begin()) {
				gc.transform(AffineTransform.getTranslateInstance(100, 0));
				final var lgh = new SimpleLayoutGlyphHandler();
				lgh.setGC(gc);
				try (final var tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules("ja"), lgh)) {
					tlf.setDirection(FontStyle.Direction.TB);
					tlf.setFontFamilies(FontFamilyList.create("Kenten Generic"));
					tlf.setFontSize(16);
					tlf.characters("﹅﹆");
					tlf.flush();
				}
			}
		}
	}
}
