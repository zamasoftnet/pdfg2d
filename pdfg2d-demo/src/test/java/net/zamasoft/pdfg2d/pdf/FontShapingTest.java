package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.demo.DemoUtils;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.TextShaper;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;

/**
 * Verifies that OpenType shaping applies GSUB {@code liga} ligatures and GPOS
 * {@code kern} pair kerning. Uses a tiny subset of a free font that carries an
 * "fi" ligature and a "//" kern pair.
 */
public class FontShapingTest {

	/** The glyphs one string shapes to: ids, source clusters and advances (with pair kerning). */
	private record Shaped(List<Integer> gids, List<Integer> clusters, List<Double> advances) {
		int length() {
			return this.gids.size();
		}
	}

	private static FontManager fontManager() throws Exception {
		final var fsm = new PDFFontSourceManager();
		final var face = new FontFace();
		face.src = new FileSource(DemoUtils.getResourceFile("shaping-test.otf"));
		face.fontFamily = FontFamilyList.create("Shaping");
		fsm.addFontFace(face);
		final var pdf = new PDFWriterImpl(new StreamFragmentedOutput(new ByteArrayOutputStream()),
				PDFParams.createDefault().withFontSourceManager(fsm));
		return pdf.getFontManager();
	}

	private static FontStyleImpl style() {
		return new FontStyleImpl(FontFamilyList.create("Shaping"), 100, Style.NORMAL, Weight.W_400,
				Direction.LTR, TextLayoutHandler.DEFAULT_FONT_POLICY);
	}

	private static Shaped shape(final FontManager fontManager, final String s) {
		final var text = s.toCharArray();
		final var shaped = new Shaped(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		final TextShaper shaper = fontManager.getTextShaper();
		shaper.setGlyphHandler(new GlyphHandler() {
			private FontMetrics metrics;
			private int cluster;

			@Override
			public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
				this.metrics = fontMetrics;
			}

			@Override
			public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
				// The pair kerning against the previous glyph lands on this glyph's advance
				var advance = this.metrics.getAdvance(gid);
				if (!shaped.gids().isEmpty()) {
					advance -= this.metrics.getKerning(shaped.gids().get(shaped.gids().size() - 1), gid);
				}
				shaped.gids().add(gid);
				shaped.clusters().add(this.cluster);
				shaped.advances().add(advance);
				this.cluster += clen;
			}

			@Override
			public void endTextRun() {
				// nothing to do
			}

			@Override
			public void control(final TextControl control) {
				// no controls in these strings
			}

			@Override
			public void flush() {
				// nothing buffered
			}

			@Override
			public void close() {
				// nothing to release
			}
		});
		shaper.fontStyle(style());
		shaper.characters(0, text, 0, text.length);
		shaper.close();
		return shaped;
	}

	@Test
	public void testGsubLigatureReducesGlyphCount() throws Exception {
		final var fontManager = fontManager();
		// "fi" forms one ligature glyph; "xx" (no ligature) stays two glyphs.
		final var fi = shape(fontManager, "fi");
		final var xx = shape(fontManager, "xx");
		assertEquals(2, xx.length(), "Control pair must remain two glyphs");
		assertEquals(1, fi.length(), "The fi pair must shape to a single ligature glyph");
		// The ligature's cluster still points at the first source character.
		assertEquals(0, fi.clusters().get(0));
	}

	@Test
	public void testGposPairKerningNarrowsAdvance() throws Exception {
		final var fontManager = fontManager();
		// "//" is kerned negative. The pair adjustment lands on the second glyph's advance, so the total
		// advance of "//" must be less than twice a single slash's advance.
		final var slashSlash = shape(fontManager, "//");
		assertEquals(2, slashSlash.length());
		final var pairTotal = slashSlash.advances().get(0) + slashSlash.advances().get(1);
		final var singleSlash = shape(fontManager, "/").advances().get(0);
		assertTrue(pairTotal < 2 * singleSlash - 1,
				"GPOS kern must narrow the // pair: " + pairTotal + " vs " + (2 * singleSlash));
	}

	@Test
	public void testLigaturesAreDisabledForControlText() throws Exception {
		final var fontManager = fontManager();
		// A string with no ligature pair keeps one glyph per character.
		final var word = shape(fontManager, "oi");
		assertEquals(2, word.length());
		assertEquals(List.of(0, 1), word.clusters());
	}
}
