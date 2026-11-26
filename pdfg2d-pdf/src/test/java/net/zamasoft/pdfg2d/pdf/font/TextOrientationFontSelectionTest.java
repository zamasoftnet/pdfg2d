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

/** Ensures that text-orientation selects the appropriate font source for vertical or horizontal writing. */
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
				orientation, net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS, null);
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
		// the vertical source, then the horizontal one for what it lacks (2026-11-26)
		assertEquals(2, upright.length);
		assertEquals(FontStyle.Direction.TB, upright[0].getDirection());
		assertEquals(FontStyle.Direction.LTR, upright[1].getDirection());
		final var sideways = manager.lookup(style(family, FontStyle.TextOrientation.SIDEWAYS));
		assertEquals(1, sideways.length);
		assertEquals(FontStyle.Direction.LTR, sideways[0].getDirection());

		assertTrue(Arrays.stream(mixed).anyMatch(s -> s.getDirection() == FontStyle.Direction.TB));
		assertTrue(Arrays.stream(mixed).anyMatch(s -> s.getDirection() == FontStyle.Direction.LTR));
	}

	/**
	 * A family without a vertical source (an installed font read through Java under the embedded policy) gives its
	 * horizontal source to upright text, as to mixed (2026-11-26): every character of upright text was missing.
	 */
	@Test
	public void uprightTakesHorizontalSourceOfFamilyWithoutVertical() throws Exception {
		final var horizontal = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);
		final var manager = new Manager();
		manager.add(horizontal);

		final var upright = manager.lookup(style(horizontal.getFontName(), FontStyle.TextOrientation.UPRIGHT));
		assertEquals(1, upright.length);
		assertEquals(FontStyle.Direction.LTR, upright[0].getDirection());
	}

	/**
	 * The horizontal source comes after the vertical sources of every family of the list, so that a later family with
	 * a vertical source still sets upright text (codex/gate, 2026-11-26: Latin of a serif list went to Times-Roman).
	 */
	@Test
	public void horizontalSourceComesAfterLaterFamiliesVerticalOnes() throws Exception {
		final var horizontal = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);
		horizontal.setFontName("Horizontal Only");
		final var vertical = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.TB);
		vertical.setFontName("Vertical Too");
		final var manager = new Manager();
		manager.add(horizontal);
		manager.add(vertical);
		final var upright = manager.lookup(new FontStyleImpl(new FontFamilyList(new net.zamasoft.pdfg2d.gc.font.FontFamily("Horizontal Only"),
				new net.zamasoft.pdfg2d.gc.font.FontFamily("Vertical Too")), 12,
				FontStyle.Style.NORMAL, FontStyle.Weight.W_400, FontStyle.Direction.TB, EMBEDDED,
				net.zamasoft.pdfg2d.gc.font.FontFeatureSet.EMPTY, true, true, FontStyle.TextOrientation.UPRIGHT,
				net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS, null));
		assertEquals(2, upright.length);
		assertEquals(FontStyle.Direction.TB, upright[0].getDirection());
		assertEquals(FontStyle.Direction.LTR, upright[1].getDirection());
	}

	/**
	 * The same order through {@link FontManagerImpl} with document fonts present, which takes the families one by one
	 * ({@code candidates}): the horizontal source after every family's vertical ones, before the missing-glyph font.
	 */
	@Test
	public void fontManagerWithDocumentFontsKeepsTheOrder() throws Exception {
		final var horizontal = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.LTR);
		horizontal.setFontName("Horizontal Only");
		final var vertical = new OpenTypeEmbeddedCIDFontSource(FONT, 0, FontStyle.Direction.TB);
		vertical.setFontName("Vertical Too");
		final var global = new Manager();
		global.add(horizontal);
		global.add(vertical);
		final FontManagerImpl manager = new FontManagerImpl(global);
		manager.localdb = new Manager();
		final var list = manager.getFontListMetrics(new FontStyleImpl(
				new FontFamilyList(new net.zamasoft.pdfg2d.gc.font.FontFamily("Horizontal Only"),
						new net.zamasoft.pdfg2d.gc.font.FontFamily("Vertical Too")),
				12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400, FontStyle.Direction.TB, EMBEDDED,
				net.zamasoft.pdfg2d.gc.font.FontFeatureSet.EMPTY, true, true, FontStyle.TextOrientation.UPRIGHT,
				net.zamasoft.pdfg2d.font.FontSource.NORMAL_WIDTH_CLASS, null));
		assertEquals(vertical, list.getFontMetrics(0).getFontSource());
		assertEquals(horizontal, list.getFontMetrics(1).getFontSource());
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
