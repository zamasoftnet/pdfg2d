package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.DefaultFontStore;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.pdf.font.cid.embedded.OpenTypeEmbeddedCIDFontSource;
import net.zamasoft.pdfg2d.pdf.font.util.MultimapUtils;

/** text-orientationによる縦/横font source選択を固定する。 */
public class TextOrientationFontSelectionTest {
	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");
	private static final FontPolicyList EMBEDDED = new FontPolicyList(
			new FontPolicyList.FontPolicy[] { FontPolicyList.FontPolicy.EMBEDDED });

	private static final class Manager extends PDFFontSourceManager {
		Manager() {
			super(false);
		}

		void add(final FontSource source) {
			this.allFonts.add(source);
			MultimapUtils.putDirect(this.nameToFonts,
					net.zamasoft.pdfg2d.gc.font.util.FontUtils.normalizeName(source.getFontName()), source);
		}
	}

	private static FontStyle style(final String family, final FontStyle.TextOrientation orientation) {
		return new FontStyleImpl(FontFamilyList.create(family), 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.TB, EMBEDDED, net.zamasoft.pdfg2d.gc.font.FontFeatureSet.EMPTY, true, true,
				orientation);
	}

	@Test
	public void uprightAndSidewaysChooseOppositePhysicalDirections() throws Exception {
		final var horizontal = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);
		final var vertical = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.TB);
		final var manager = new Manager();
		manager.add(horizontal);
		manager.add(vertical);

		final String family = horizontal.getFontName();
		final var mixed = manager.lookup(style(family, FontStyle.TextOrientation.MIXED));
		assertEquals(2, mixed.length);
		final var upright = manager.lookup(style(family, FontStyle.TextOrientation.UPRIGHT));
		assertEquals(1, upright.length);
		assertEquals(FontStyle.Direction.TB, upright[0].getDirection());
		final var sideways = manager.lookup(style(family, FontStyle.TextOrientation.SIDEWAYS));
		assertEquals(1, sideways.length);
		assertEquals(FontStyle.Direction.LTR, sideways[0].getDirection());

		assertTrue(Arrays.stream(mixed).anyMatch(s -> s.getDirection() == FontStyle.Direction.TB));
		assertTrue(Arrays.stream(mixed).anyMatch(s -> s.getDirection() == FontStyle.Direction.LTR));
	}

	@Test
	public void uprightAllowsLatinThroughVerticalCmap() throws Exception {
		final var vertical = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.TB);
		assertFalse(vertical.canDisplay('A'), "mixedではLatinを横fontへ送る");
		final var metrics = new FontMetricsImpl(new DefaultFontStore(), vertical,
				style(vertical.getFontName(), FontStyle.TextOrientation.UPRIGHT));
		assertTrue(metrics.canDisplay('A'), "uprightでは同じ縦fontのLatin cmapを使う");
	}
}
