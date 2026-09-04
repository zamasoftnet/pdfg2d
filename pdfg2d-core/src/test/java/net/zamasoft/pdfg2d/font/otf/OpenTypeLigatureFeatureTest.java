package net.zamasoft.pdfg2d.font.otf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.font.table.GsubTable;
import net.zamasoft.pdfg2d.font.table.Table;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;

/** Feature-selection regressions for two-component OpenType ligatures. */
public class OpenTypeLigatureFeatureTest {
	private static final File FONT = new File("../pdfg2d-font/src/test/resources/data/test.otf");

	private static final int TAG_LIGA = FontFeatureSet.packTag("liga");
	private static final int TAG_CLIG = FontFeatureSet.packTag("clig");
	private static final int TAG_DLIG = FontFeatureSet.packTag("dlig");
	private static final int TAG_HLIG = FontFeatureSet.packTag("hlig");

	private record LigaturePair(int firstGid, int secondCharacter, int ligatureGid) {
	}

	private static Map<Long, Integer> pairs(final GsubTable gsub, final int tag) {
		final var pairs = new LinkedHashMap<Long, Integer>();
		for (final var subst : gsub.collectLigatures(tag)) {
			final var firstGlyphs = subst.coverage().getGlyphIds();
			for (int i = 0; i < firstGlyphs.length && i < subst.getLigatureSetCount(); ++i) {
				final int firstGid = firstGlyphs[i];
				for (final var ligature : subst.getLigatureSet(i).ligatures()) {
					if (ligature.components().length == 1) {
						final int secondGid = ligature.components()[0];
						final long key = ((long) firstGid << 32) | (secondGid & 0xFFFFFFFFL);
						pairs.put(key, ligature.ligGlyph());
					}
				}
			}
		}
		return pairs;
	}

	private static LigaturePair findDligOnlyPair(final OpenTypeFontSource source) {
		final var gsub = (GsubTable) source.getOpenTypeFont().getTable(Table.GSUB);
		final Set<Long> defaultPairs = new HashSet<>(pairs(gsub, TAG_LIGA).keySet());
		defaultPairs.addAll(pairs(gsub, TAG_CLIG).keySet());
		for (final var entry : pairs(gsub, TAG_DLIG).entrySet()) {
			final long key = entry.getKey();
			if (defaultPairs.contains(key)) {
				continue;
			}
			final int secondGid = (int) key;
			final Integer cid = source.getCmapFormat().getCharacterCode(secondGid);
			if (cid != null && source.getCmapFormat().mapCharCode(cid) == secondGid) {
				return new LigaturePair((int) (key >>> 32), cid, entry.getValue());
			}
		}
		throw new AssertionError("test.otf must contain a cmap-addressable dlig-only pair");
	}

	@Test
	public void dligIsDefaultOffAndCanBeEnabled() throws Exception {
		final var source = new OpenTypeFontSource(FONT, 0, Direction.LTR);
		final var font = source.createFont();
		final var pair = findDligOnlyPair(source);

		assertEquals(-1, font.getLigature(pair.firstGid(), pair.secondCharacter(), FontFeatureSet.EMPTY));
		final var dlig = FontFeatureSet.of(new int[] { TAG_DLIG }, new int[] { 1 });
		assertEquals(pair.ligatureGid(), font.getLigature(pair.firstGid(), pair.secondCharacter(), dlig));
	}

	@Test
	public void ligaIsDefaultOnAndCanBeDisabled() throws Exception {
		final var source = new OpenTypeFontSource(FONT, 0, Direction.LTR);
		final var font = source.createFont();
		final int f = font.toGID('f');
		final int expected = font.getLigature(f, 'i');
		assertTrue(expected >= 0, "test.otf must contain its documented fi liga pair");

		assertEquals(expected, font.getLigature(f, 'i', FontFeatureSet.EMPTY));
		final var ligaOff = FontFeatureSet.of(new int[] { TAG_LIGA }, new int[] { 0 });
		assertEquals(-1, font.getLigature(f, 'i', ligaOff));
	}

	@Test
	public void ligaturePolicyTreatsCligAsDefaultOnIndependentlyOfLiga() {
		final var ligaOff = FontFeatureSet.of(new int[] { TAG_LIGA }, new int[] { 0 });
		assertFalse(OpenTypeFont.isLigatureEnabled(TAG_LIGA, ligaOff));
		assertTrue(OpenTypeFont.isLigatureEnabled(TAG_CLIG, ligaOff),
				"unspecified clig must remain enabled when liga is disabled");
		assertFalse(OpenTypeFont.isLigatureEnabled(TAG_DLIG, ligaOff));
		assertFalse(OpenTypeFont.isLigatureEnabled(TAG_HLIG, ligaOff));

		final var optional = FontFeatureSet.of(new int[] { TAG_DLIG, TAG_HLIG }, new int[] { 1, 1 });
		assertTrue(OpenTypeFont.isLigatureEnabled(TAG_DLIG, optional));
		assertTrue(OpenTypeFont.isLigatureEnabled(TAG_HLIG, optional));
	}
}
