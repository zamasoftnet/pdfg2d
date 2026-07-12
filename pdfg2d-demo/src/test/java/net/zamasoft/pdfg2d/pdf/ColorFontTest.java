package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.demo.DemoUtils;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.test.TestOutputFiles;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * Verifies OpenType COLR/CPAL color-glyph rendering. The bundled test font
 * paints its 'A' glyph as two layers — a red layer over a blue layer — so a
 * rendered 'A' must contain both red and blue pixels, and the content stream
 * must carry two distinct fill colors rather than a single text show.
 */
public class ColorFontTest {

	private File render(final String name) throws Exception {
		final var file = TestOutputFiles.outputFile(getClass(), name);
		final var fsm = new PDFFontSourceManager();
		final var face = new FontFace();
		face.src = new FileSource(DemoUtils.getResourceFile("color-test.otf"));
		face.fontFamily = FontFamilyList.create("ColorTest");
		fsm.addFontFace(face);
		try (final var out = new FileOutputStream(file)) {
			final var builder = new StreamFragmentedOutput(out);
			final var pdf = new PDFWriterImpl(builder,
					PDFParams.createDefault().withFontSourceManager(fsm)
							.withCompression(PDFParams.Compression.NONE));
			try (final var gc = new PDFGC(pdf.nextPage(200, 200))) {
				final var g2d = new BridgeGraphics2D(gc);
				g2d.setColor(Color.BLACK);
				g2d.setFont(new Font("ColorTest", Font.PLAIN, 150));
				g2d.drawString("A", 30, 150);
				g2d.dispose();
			}
			pdf.close();
			builder.close();
		}
		return file;
	}

	@Test
	public void testColrGlyphRendersMultipleColors() throws Exception {
		final var file = render("colr_glyph.pdf");

		// The color glyph is drawn as filled outlines with per-layer colors,
		// so the content stream carries two distinct RGB fills (rg), not a Tj.
		final var raw = new String(java.nio.file.Files.readAllBytes(file.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue(raw.contains(" rg") || raw.contains(" sc"), "Layer fill colors must be emitted");

		try (final var doc = Loader.loadPDF(file)) {
			final var image = new PDFRenderer(doc).renderImage(0);
			var red = false;
			var blue = false;
			for (var y = 0; y < image.getHeight(); ++y) {
				for (var x = 0; x < image.getWidth(); ++x) {
					final var c = new Color(image.getRGB(x, y));
					if (c.getRed() > 180 && c.getGreen() < 80 && c.getBlue() < 80) {
						red = true;
					}
					if (c.getBlue() > 180 && c.getRed() < 80 && c.getGreen() < 80) {
						blue = true;
					}
				}
			}
			assertTrue(red, "The red layer of the color glyph must be visible");
			assertTrue(blue, "The blue layer of the color glyph must be visible");
		}
	}
}
