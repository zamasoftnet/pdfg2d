package net.zamasoft.pdfg2d.font.otf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;

/** Source-font metrics measured independently with fontTools. */
class OpenTypeVerticalOriginTest {
	private static void check(final String file, final Direction direction, final int[][] expected) throws Exception {
		final var font = new OpenTypeFontSource(new File(file), 0, direction).createFont();
		for (final var row : expected) {
			final int gid = font.toGID(row[0]);
			assertEquals(row[1], gid, "GID for U+" + Integer.toHexString(row[0]));
			assertEquals(row[2], font.getAdvance(gid), "advance for GID " + gid);
			assertEquals(row[3], font.getVerticalOrigin(gid), "origin for GID " + gid);
			assertEquals(row[3], font.getVerticalOrigin(gid), "cached origin for GID " + gid);
		}
	}

	@Test
	void trueTypeUsesSourceBoundsAndTopSideBearing() throws Exception {
		check("src/test/resources/pgothic-vert-subset.ttf", Direction.TB, new int[][] {
				{ '「', 28, 570, 450 }, { '（', 26, 550, 430 }, { '【', 30, 550, 430 },
				{ '組', 6, 1000, 880 }, { '乙', 3, 1000, 880 }, { 'A', 21, 1000, 829 },
				{ '—', 24, 960, 860 }, { '─', 2, 1000, 880 },
				{ '…', 25, 1000, 880 }, { '、', 22, 500, 880 } });
	}

	@Test
	void fixtureContainsEmDashButNoHorizontalBarMapping() throws Exception {
		final var font = new OpenTypeFontSource(new File("src/test/resources/pgothic-vert-subset.ttf"),
				0, Direction.TB).createFont();
		// The supplied fixture omits U+2015; U+2014 selects the documented GID 24.
		assertEquals(0, font.toGID(0x2015));
		assertEquals(24, font.toGID(0x2014));
		assertEquals(860, font.getVerticalOrigin(24));
	}

	@Test
	void cffPrefersVorgAndFallsBackToNormalizedPath() throws Exception {
		for (final boolean vorg : new boolean[] { true, false }) {
			check("src/test/resources/sansjp-" + (vorg ? "vorg" : "novorg") + "-subset.otf",
					Direction.TB, new int[][] {
							{ '「', 28, 1000, vorg ? 700 : 730 }, { '（', 24, 1000, 880 },
							{ '組', 19, 1000, 880 }, { 'A', 3, 1000, 880 } });
		}
	}

	@Test
	void nonThousandUpmTruncatesInsteadOfRounding() throws Exception {
		check("../pdfg2d-demo/src/main/resources/ipaexm.ttf", Direction.TB, new int[][] {
				{ '「', 7497, 1000, 879 }, { '組', 2528, 1000, 879 }, { '‐', 12237, 1000, 859 } });
	}

	@Test
	void horizontalAndNoVerticalSubstitutionsUseConventionalOrigin() throws Exception {
		for (final var file : new String[] { "pgothic-vert-subset.ttf", "pgothic-novert-subset.ttf",
				"sansjp-vorg-subset.otf" }) {
			final var font = new OpenTypeFontSource(new File("src/test/resources/" + file), 0,
					file.contains("novert") ? Direction.TB : Direction.LTR).createFont();
			for (final int c : new int[] { '「', '（', '【', '組', 'A' }) {
				assertEquals(880, font.getVerticalOrigin(font.toGID(c)), file + " U+" + Integer.toHexString(c));
			}
		}
	}

	@Test
	void blankGlyphsUseConventionalOrigin() throws Exception {
		for (final var file : new String[] { "../pdfg2d-demo/src/main/resources/ipaexm.ttf",
				"src/test/resources/sansjp-vorg-subset.otf", "src/test/resources/sansjp-novorg-subset.otf" }) {
			final var font = new OpenTypeFontSource(new File(file), 0, Direction.TB).createFont();
			for (final int c : new int[] { ' ', '　' }) {
				assertEquals(880, font.getVerticalOrigin(font.toGID(c)), file);
			}
		}
	}
}
