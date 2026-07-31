package net.zamasoft.pdfg2d.gc.text;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * Tests for {@link TextImpl#addXAdvance} and the per-glyph adjustment
 * carrying in {@link TextImpl#split} (Japanese spacing S1a: layout-level
 * spacing is stored as xadvances so that measurement and drawing agree,
 * and line splitting must not drop or double-count adjustments).
 */
public class TextImplXAdvanceTest {

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

	private static TextImpl text(final int glyphs) {
		final TextImpl text = new TextImpl(0, new FontStyleImpl(FontFamilyList.SERIF, 10, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_400, FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE),
				new FixedMetrics());
		for (int i = 0; i < glyphs; ++i) {
			text.appendGlyph(new char[] { (char) ('a' + i) }, 0, (byte) 1, i + 1);
		}
		text.pack();
		return text;
	}

	@Test
	void addXAdvanceAccumulatesAndPreservesExisting() {
		final TextImpl text = text(3);
		text.addXAdvance(1, -5);
		text.addXAdvance(1, -1);
		text.addXAdvance(2, 2);
		assertArrayEquals(new double[] { 0, -6, 2 }, text.getXAdvances(false), 0.0001);
		assertEquals(30 - 6 + 2, text.getAdvance(), 0.0001);
	}

	@Test
	void splitCarriesAdjustmentsToBothParts() {
		final TextImpl text = text(4);
		text.addXAdvance(0, -5); // head側
		text.addXAdvance(3, -2); // tail側
		final TextImpl head = (TextImpl) text.split(2);
		// head: glyphs 0-1, adjustments [-5, 0]
		assertArrayEquals(new double[] { -5, 0 }, head.getXAdvances(false), 0.0001);
		assertEquals(20 - 5, head.getAdvance(), 0.0001);
		// tail: glyphs 2-3, adjustments [0, -2]
		assertArrayEquals(new double[] { 0, -2 }, text.getXAdvances(false), 0.0001);
		assertEquals(20 - 2, text.getAdvance(), 0.0001);
	}
}
