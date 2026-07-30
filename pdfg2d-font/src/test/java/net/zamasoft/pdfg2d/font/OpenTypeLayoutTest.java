package net.zamasoft.pdfg2d.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.table.CmapTable;
import net.zamasoft.pdfg2d.font.table.GposTable;
import net.zamasoft.pdfg2d.font.table.GsubTable;
import net.zamasoft.pdfg2d.font.table.SinglePos;
import net.zamasoft.pdfg2d.font.table.Table;

/**
 * Parser tests for the OpenType advanced-typography tables used by shaping:
 * GSUB ligature substitution (with its retained coverage) and GPOS pair
 * kerning. The bundled test font carries an "fi" ligature and negative
 * kerning between two slashes.
 */
public class OpenTypeLayoutTest {

	private static OpenTypeFont font() throws Exception {
		return new FontFile(new File("src/test/resources/data/test.otf")).getFont();
	}

	@Test
	public void testGsubLigaturesAreParsedWithCoverage() throws Exception {
		try (final var otf = font()) {
			final var gsub = (GsubTable) otf.getTable(Table.GSUB);
			assertNotNull(gsub, "The test font must have a GSUB table");
			final var cmap = ((CmapTable) otf.getTable(Table.CMAP))
					.getCmapFormat(Table.PLATFORM_MICROSOFT, (short) 1);
			final int f = cmap.mapCharCode('f');
			final int i = cmap.mapCharCode('i');

			var found = false;
			for (final var ls : gsub.collectLigatures()) {
				final var index = ls.coverage().findGlyph(f);
				if (index < 0) {
					continue;
				}
				for (final var lig : ls.getLigatureSet(index).ligatures()) {
					if (lig.components().length == 1 && lig.components()[0] == i) {
						found = true;
					}
				}
			}
			assertTrue(found, "The fi ligature (f + i) must be discoverable through the retained coverage");
		}
	}

	@Test
	public void testGsubSingleSubstitutionsByFeatureTag() throws Exception {
		// The bundled CJK test font maps U+4E08 to GID 9512 and its jp78
		// (JIS78 variant) substitution to GID 61232 — both verified directly
		// against the font binary (2026-07-31).
		try (final var otf = font()) {
			final var gsub = (GsubTable) otf.getTable(Table.GSUB);
			assertNotNull(gsub, "The test font must have a GSUB table");
			final var cmap = ((CmapTable) otf.getTable(Table.CMAP))
					.getCmapFormat(Table.PLATFORM_MICROSOFT, (short) 1);
			final int base = cmap.mapCharCode(0x4E08);
			assertEquals(9512, base, "cmap GID of U+4E08");

			final var jp78 = gsub.collectSingleSubstitutions(0x6a703738); // 'jp78'
			assertTrue(!jp78.isEmpty(), "The test font must carry jp78 single substitutions");
			int gid = base;
			for (final var ss : jp78) {
				gid = ss.substitute(gid);
			}
			assertEquals(61232, gid, "jp78 variant GID of U+4E08");

			// A GPOS-only feature tag has no GSUB lookups — the plan is empty,
			// not an error (the caller composes tags without kind dispatch).
			assertTrue(gsub.collectSingleSubstitutions(0x70616c74).isEmpty(), // 'palt'
					"palt is a GPOS feature and must yield no GSUB substitutions");
		}
	}

	@Test
	public void testGposSinglePositionsByFeatureTag() throws Exception {
		// palt of U+3001 (cmap GID 1397): xPlacement=-19, xAdvance=-500 in a
		// 1000-UPM font — verified directly against the font binary
		// (2026-07-31). The full-width advance 1000 halves under palt.
		try (final var otf = font()) {
			final var gpos = (GposTable) otf.getTable(Table.GPOS);
			assertNotNull(gpos, "The test font must have a GPOS table");
			final var cmap = ((CmapTable) otf.getTable(Table.CMAP))
					.getCmapFormat(Table.PLATFORM_MICROSOFT, (short) 1);
			final int comma = cmap.mapCharCode(0x3001);
			assertEquals(1397, comma, "cmap GID of U+3001");

			final var palt = gpos.collectSinglePositions(0x70616c74); // 'palt'
			assertTrue(!palt.isEmpty(), "The test font must carry palt single positions");
			SinglePos.GlyphPosition pos = null;
			for (final var sp : palt) {
				final var p = sp.getPosition(comma);
				if (p != null) {
					pos = p;
				}
			}
			assertNotNull(pos, "U+3001 must be palt-covered");
			assertEquals(-19, pos.xPlacement(), "palt xPlacement of U+3001");
			assertEquals(-500, pos.xAdvance(), "palt xAdvance of U+3001");

			// A glyph outside the coverage yields null, and a GSUB-only tag
			// yields an empty plan.
			assertTrue(gpos.collectSinglePositions(0x6a703738).isEmpty(), // 'jp78'
					"jp78 is a GSUB feature and must yield no GPOS positions");
		}
	}

	@Test
	public void testGposPairKerningIsParsed() throws Exception {
		try (final var otf = font()) {
			final var gpos = (GposTable) otf.getTable(Table.GPOS);
			assertNotNull(gpos, "The test font must have a GPOS table");
			final var cmap = ((CmapTable) otf.getTable(Table.CMAP))
					.getCmapFormat(Table.PLATFORM_MICROSOFT, (short) 1);
			final int slash = cmap.mapCharCode('/');

			var kern = 0;
			for (final var pp : gpos.collectKernPairPos()) {
				kern += pp.getKerning(slash, slash);
			}
			assertTrue(kern < 0, "The // pair must be negatively kerned, was " + kern);
		}
	}
}
