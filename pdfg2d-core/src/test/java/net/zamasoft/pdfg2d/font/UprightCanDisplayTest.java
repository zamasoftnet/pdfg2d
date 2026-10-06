package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.TextOrientation;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * Upright half-width characters in vertical text (2026-10-06). A vertical
 * OpenType source leaves them out of {@code canDisplay} (they are normally set
 * sideways with a horizontal font) but must offer them for
 * {@code text-orientation: upright}, also through a wrapper: fonts from the
 * font configuration are wrapped, and they used to lose upright digits and
 * letters to the next font in the list.
 */
class UprightCanDisplayTest {
	private static final File FONT = new File("src/test/resources/sansjp-vorg-subset.otf");

	private static FontStyle style(final TextOrientation orientation) {
		return new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				Direction.TB, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE, FontFeatureSet.EMPTY, true, true,
				orientation, FontSource.NORMAL_WIDTH_CLASS, null);
	}

	@Test
	void verticalSourceOffersHalfWidthOnlyUpright() throws Exception {
		final FontSource source = new OpenTypeFontSource(FONT, 0, Direction.TB);
		assertFalse(source.canDisplay('A'));
		assertTrue(source.canDisplayUpright('A'));
		assertTrue(source.canDisplay('組'));
	}

	@Test
	void wrapperDelegatesUpright() throws Exception {
		final FontSource wrapped = new FontSourceWrapper(new OpenTypeFontSource(FONT, 0, Direction.TB));
		assertFalse(wrapped.canDisplay('A'));
		assertTrue(wrapped.canDisplayUpright('A'));
	}

	@Test
	void metricsAskTheWrapperForUprightText() throws Exception {
		final FontSource wrapped = new FontSourceWrapper(new OpenTypeFontSource(FONT, 0, Direction.TB));
		assertTrue(new FontMetricsImpl(null, wrapped, style(TextOrientation.UPRIGHT)).canDisplay('A'));
		assertFalse(new FontMetricsImpl(null, wrapped, style(TextOrientation.MIXED)).canDisplay('A'));
	}
}
