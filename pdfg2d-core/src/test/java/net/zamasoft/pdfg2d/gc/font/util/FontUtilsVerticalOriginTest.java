package net.zamasoft.pdfg2d.gc.font.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.File;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/** Vertical outline placement in user space, including external transforms. */
class FontUtilsVerticalOriginTest {
	private static ShapedFont font() throws Exception {
		return (ShapedFont) new OpenTypeFontSource(new File("src/test/resources/pgothic-vert-subset.ttf"),
				0, FontStyle.Direction.TB).createFont();
	}

	private static TextImpl text(final ShapedFont font, final double size, final String chars) {
		final var style = new FontStyleImpl(FontFamilyList.SERIF, size, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_400, FontStyle.Direction.TB, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
		final var text = new TextImpl(0, style, new FontMetricsImpl(source -> font, font.getFontSource(), style));
		for (final char c : chars.toCharArray()) {
			text.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c));
		}
		text.pack();
		return text;
	}

	@Test
	void openingBracketStaysInsideItsAdvanceAtEveryScale() throws Exception {
		final var font = font();
		for (final double size : new double[] { 12, 36 }) {
			for (final double scale : new double[] { 0.5, 1, 2 }) {
				final var outer = AffineTransform.getTranslateInstance(40, 50);
				outer.scale(1.5 * scale, scale);
				final var original = new AffineTransform(outer);
				final var bracket = new GeneralPath();
				final var bracketText = text(font, size, "「");
				bracketText.addXAdvance(0, size * 0.2);
				FontUtils.addTextPath(bracket, font, bracketText, outer);
				final var bounds = outer.createInverse().createTransformedShape(bracket).getBounds2D();
				final double pen = 0.2 * size;
				assertTrue(bounds.getMinY() >= pen, "bracket must start after its pen");
				assertTrue(bounds.getMaxY() <= pen + 0.57 * size, "bracket must end within its advance");
				assertEquals(0.216, (bounds.getMinY() - pen) / size, 0.00001);
				final var ideograph = new GeneralPath();
				final var ideographText = text(font, size, "組");
				ideographText.addXAdvance(0, pen + 0.57 * size);
				FontUtils.addTextPath(ideograph, font, ideographText, outer);
				assertTrue(bracket.getBounds2D().getMaxY() < ideograph.getBounds2D().getMinY());
				assertEquals(original, outer, "the caller's transform must not be mutated");
			}
		}
	}

	@Test
	void consecutiveGlyphsUseIndependentOriginsAndKeepPenAdjustments() throws Exception {
		final var font = font();
		final var text = text(font, 36, "「A組");
		text.setLetterSpacing(2);
		text.addXAdvance(0, 3);
		text.addXAdvance(1, 4);
		text.addXAdvance(2, -1);
		final var path = new GeneralPath();
		FontUtils.addTextPath(path, font, text, new AffineTransform());
		final var expected = new GeneralPath();
		final double[] pens = { 3, 3 + 0.57 * 36 + 2 + 4, 3 + 0.57 * 36 + 2 + 4 + 36 + 2 - 1 };
		final int[] origins = { 450, 829, 880 };
		final int[] widths = { 1000, 637, 1000 };
		for (int i = 0; i < 3; ++i) {
			final var at = AffineTransform.getTranslateInstance(-widths[i] * 0.036 / 2,
					pens[i] + origins[i] * 0.036);
			at.scale(0.036, 0.036);
			expected.append(font.getShapeByGID(text.getGlyphIds()[i]).getPathIterator(at), false);
		}
		final var actualIterator = path.getPathIterator(null);
		final var expectedIterator = expected.getPathIterator(null);
		final double[] actualCoords = new double[6];
		final double[] expectedCoords = new double[6];
		while (!expectedIterator.isDone()) {
			assertTrue(!actualIterator.isDone());
			assertEquals(expectedIterator.currentSegment(expectedCoords), actualIterator.currentSegment(actualCoords));
			for (int i = 0; i < 6; ++i) {
				assertEquals(expectedCoords[i], actualCoords[i], 0.00001);
			}
			actualIterator.next();
			expectedIterator.next();
		}
		assertTrue(actualIterator.isDone());
	}

	@Test
	void mixedDashKerningIncludesOriginDifferenceInBothDirections() throws Exception {
		final var font = font();
		// U+2015 is absent from this fixture; U+2014 selects the documented GID 24.
		final int dash = font.toGID('—');
		final int rule = font.toGID('─');
		assertEquals(860, font.getVerticalOrigin(dash));
		assertEquals(880, font.getVerticalOrigin(rule));
		for (final int[] pair : new int[][] { { dash, rule }, { rule, dash }, { dash, dash }, { rule, rule } }) {
			final double oldGap = font.getAdvance(pair[0])
					+ font.getShapeByGID(pair[1]).getBounds2D().getMinY()
					- font.getShapeByGID(pair[0]).getBounds2D().getMaxY();
			final int difference = pair[0] == pair[1] ? 0 : pair[0] == dash ? 20 : -20;
			assertEquals(Math.max(0, Math.round(oldGap + difference)), font.getKerning(pair[0], pair[1]));
			if (pair[0] == dash && pair[1] == rule) {
				assertTrue(font.getKerning(pair[0], pair[1]) != Math.max(0, Math.round(oldGap)));
			}
		}
	}
}
