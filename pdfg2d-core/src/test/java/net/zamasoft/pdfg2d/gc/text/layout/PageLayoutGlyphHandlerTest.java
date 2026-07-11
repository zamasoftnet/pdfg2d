package net.zamasoft.pdfg2d.gc.text.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * Layout tests for {@link PageLayoutGlyphHandler}: line wrapping against the
 * line advance limit, justification by letter-spacing distribution, and the
 * float-comparison helper. Uses {@link RecorderGC} so the emitted drawText
 * commands (and their positions) can be asserted without a PDF backend.
 */
public class PageLayoutGlyphHandlerTest {

	/** Fixed-advance metrics: every glyph advances 10 units. */
	private static final class FixedMetrics implements FontMetrics {
		private static final long serialVersionUID = 1L;

		@Override
		public double getFontSize() {
			return 10;
		}

		@Override
		public double getXHeight() {
			return 5;
		}

		@Override
		public double getAscent() {
			return 8;
		}

		@Override
		public double getDescent() {
			return 2;
		}

		@Override
		public double getAdvance(final int gid) {
			return 10;
		}

		@Override
		public double getWidth(final int gid) {
			return 10;
		}

		@Override
		public double getSpaceAdvance() {
			return 10;
		}

		@Override
		public double getKerning(final int gid, final int sgid) {
			return 0;
		}

		@Override
		public FontSource getFontSource() {
			return null;
		}
	}

	private static FontStyle style() {
		return new FontStyleImpl(FontFamilyList.SERIF, 10, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
	}

	/** Emits {@code count} glyphs of 10 units each as a single text run. */
	private static void emitGlyphs(final PageLayoutGlyphHandler handler, final int count) {
		handler.startTextRun(0, style(), new FixedMetrics());
		for (var i = 0; i < count; ++i) {
			handler.glyph(i, new char[] { (char) ('a' + (i % 26)) }, 0, (byte) 1, 1);
		}
		handler.endTextRun();
	}

	private static List<RecorderGC.DrawText> drawTexts(final RecorderGC gc) {
		return gc.getPage().commands().stream()
				.filter(RecorderGC.DrawText.class::isInstance)
				.map(RecorderGC.DrawText.class::cast)
				.toList();
	}

	@Test
	public void testSingleLineFitsWithoutWrapping() throws Exception {
		final var gc = new RecorderGC(null);
		final var handler = new PageLayoutGlyphHandler(gc);
		handler.setLineAdvance(100);
		handler.setPageAdvance(1000);

		emitGlyphs(handler, 5); // 50 units, fits in 100
		handler.flush();
		handler.close();

		assertEquals(1, drawTexts(gc).size(), "5 glyphs of width 10 fit on one 100-unit line");
	}

	@Test
	public void testOverlongTextWrapsAtBreakOpportunity() throws Exception {
		final var gc = new RecorderGC(null);
		final var handler = new PageLayoutGlyphHandler(gc);
		handler.setLineAdvance(100);
		handler.setPageAdvance(1000);

		// Two units of 80 and 70 units; flush() marks the break opportunity
		// between them (this is how TextLayoutHandler drives the handler).
		emitGlyphs(handler, 8);
		handler.flush();
		emitGlyphs(handler, 7);
		handler.flush();
		handler.close();

		final var texts = drawTexts(gc);
		assertTrue(texts.size() >= 2, "80+70 units must wrap on a 100-unit line, got " + texts.size());
		// The second line must start below the first
		assertTrue(texts.get(texts.size() - 1).y() > texts.get(0).y(),
				"Wrapped line must advance in page direction");
	}

	@Test
	public void testJustifyStretchesLineByLetterSpacing() throws Exception {
		final var gc = new RecorderGC(null);
		final var handler = new PageLayoutGlyphHandler(gc);
		handler.setLineAdvance(100);
		handler.setPageAdvance(1000);
		handler.setAlign(PageLayoutGlyphHandler.Alignment.JUSTIFY);

		// The wrapped (non-last) line must be stretched toward the full
		// 100-unit measure by distributing extra letter spacing.
		emitGlyphs(handler, 8);
		handler.flush();
		emitGlyphs(handler, 7);
		handler.flush();
		handler.close();

		final var texts = drawTexts(gc);
		assertTrue(texts.size() >= 2);
		final var firstLine = (Text) texts.get(0).text();
		assertTrue(firstLine.getAdvance() > 80.5,
				"Justified line must be wider than its natural 80 units, got " + firstLine.getAdvance());
	}

	@Test
	public void testCenterAlignmentOffsetsLineStart() throws Exception {
		final var gc = new RecorderGC(null);
		final var handler = new PageLayoutGlyphHandler(gc);
		handler.setLineAdvance(100);
		handler.setPageAdvance(1000);
		handler.setAlign(PageLayoutGlyphHandler.Alignment.CENTER);

		emitGlyphs(handler, 4); // 40 units, centered in 100 -> starts at 30
		handler.flush();
		handler.close();

		final var texts = drawTexts(gc);
		assertEquals(1, texts.size());
		assertEquals(30.0, texts.get(0).x(), 0.5, "Centered 40-unit line in a 100-unit measure starts at 30");
	}

	@Test
	public void testCompareUsesTolerance() {
		assertEquals(0, PageLayoutGlyphHandler.compare(1.0, 1.05), "Values within 0.1 compare as equal");
		assertEquals(-1, PageLayoutGlyphHandler.compare(1.0, 2.0));
		assertEquals(1, PageLayoutGlyphHandler.compare(2.0, 1.0));
	}
}
