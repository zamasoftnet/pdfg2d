package net.zamasoft.pdfg2d.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.demo.DemoUtils;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.pipeline.Item;
import net.zamasoft.pdfg2d.gc.text.pipeline.Shaper;
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

	private static Shaper shaper() throws Exception {
		final var fsm = new PDFFontSourceManager();
		final var face = new FontFace();
		face.src = new FileSource(DemoUtils.getResourceFile("shaping-test.otf"));
		face.fontFamily = FontFamilyList.create("Shaping");
		fsm.addFontFace(face);
		final var pdf = new PDFWriterImpl(new StreamFragmentedOutput(new ByteArrayOutputStream()),
				PDFParams.createDefault().withFontSourceManager(fsm));
		return new Shaper(pdf.getFontManager());
	}

	private static FontStyleImpl style() {
		return new FontStyleImpl(FontFamilyList.create("Shaping"), 100, Style.NORMAL, Weight.W_400,
				Direction.LTR, TextLayoutHandler.DEFAULT_FONT_POLICY);
	}

	private static net.zamasoft.pdfg2d.gc.text.pipeline.GlyphRun shape(final Shaper shaper, final String s) {
		final var text = s.toCharArray();
		final var runs = shaper.shape(text, new Item(0, text.length, (byte) 0, style()));
		// Merge (there should be a single font run for these ASCII strings).
		return runs.get(0);
	}

	@Test
	public void testGsubLigatureReducesGlyphCount() throws Exception {
		final var shaper = shaper();
		// "fi" forms one ligature glyph; "xx" (no ligature) stays two glyphs.
		final var fi = shape(shaper, "fi");
		final var xx = shape(shaper, "xx");
		assertEquals(2, xx.length, "Control pair must remain two glyphs");
		assertEquals(1, fi.length, "The fi pair must shape to a single ligature glyph");
		// The ligature's cluster still points at the first source character.
		assertEquals(0, fi.clusters[0]);
	}

	@Test
	public void testGposPairKerningNarrowsAdvance() throws Exception {
		final var shaper = shaper();
		// "//" is kerned negative; "/x" less so. Compare the first glyph's
		// advance (which carries the pair adjustment against the next glyph).
		final var slashSlash = shape(shaper, "//");
		assertEquals(2, slashSlash.length);
		// The pair adjustment lands on the second glyph's advance, so the total
		// advance of "//" must be less than twice a single slash's advance.
		final var pairTotal = slashSlash.xAdvances[0] + slashSlash.xAdvances[1];
		final var singleSlash = shape(shaper, "/").xAdvances[0];
		assertTrue(pairTotal < 2 * singleSlash - 1,
				"GPOS kern must narrow the // pair: " + pairTotal + " vs " + (2 * singleSlash));
	}

	@Test
	public void testLigaturesAreDisabledForControlText() throws Exception {
		final var shaper = shaper();
		// A string with no ligature pair keeps one glyph per character.
		final var word = shape(shaper, "oi");
		assertEquals(2, word.length);
		assertEquals(List.of(0, 1),
				List.of(word.clusters[0], word.clusters[1]));
	}
}
