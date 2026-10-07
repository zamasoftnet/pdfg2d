package net.zamasoft.pdfg2d.pdf.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontFace;

/**
 * Equivalence test for the persistent font index ({@link FontIndex}) (2026-08-01).
 *
 * <p>
 * Ensures that a FontSource built by directly parsing a real font (IPAex Mincho) matches a FontSource
 * reconstructed through put→save→reload→lookup in the index, for all metadata used for selection
 * and shaping and for the character→GID mapping.
 * </p>
 */
public class FontIndexTest {

	@TempDir
	Path tempDir;

	private static final File FONT = new File("../pdfg2d-demo/src/main/resources/ipaexm.ttf");

	private static List<FontSource> parseSources() throws Exception {
		final List<FontSource> list = new ArrayList<>();
		final FontFace face = new FontFace();
		FontLoader.readTTF(list, face, FontLoader.Type.CID_IDENTITY, FONT, 0, new HashMap<>());
		FontLoader.readTTF(list, face, FontLoader.Type.EMBEDDED, FONT, 0, new HashMap<>());
		return list;
	}

	@Test
	public void testRoundTripReconstructsEquivalentSources() throws Exception {
		final List<FontSource> parsed = parseSources();
		assertTrue(parsed.size() >= 2, "CID_IDENTITY+EMBEDDEDで最低2ソース");

		final File dbFile = Files.createTempFile(this.tempDir, "fonts", ".db").toFile();
		final FontIndex writeIndex = new FontIndex(dbFile);
		writeIndex.put(FONT, "key", 1, parsed);
		writeIndex.save();

		final FontIndex readIndex = new FontIndex(dbFile);
		final List<FontSource> restored = readIndex.lookup(FONT, "key");
		assertNotNull(restored, "鮮度一致でヒットする");
		assertEquals(parsed.size(), restored.size(), "ソース数と順序を保存する");

		for (int i = 0; i < parsed.size(); ++i) {
			final OpenTypeFontSource a = (OpenTypeFontSource) parsed.get(i);
			final OpenTypeFontSource b = (OpenTypeFontSource) restored.get(i);
			assertEquals(a.getClass(), b.getClass());
			assertEquals(a.getFontName(), b.getFontName());
			assertArrayEquals(a.getAliases(), b.getAliases());
			assertEquals(a.isItalic(), b.isItalic());
			assertEquals(a.getWeight(), b.getWeight());
			assertEquals(a.getWidthClass(), b.getWidthClass());
			assertEquals(a.getPanose(), b.getPanose());
			assertEquals(a.getUnitsPerEm(), b.getUnitsPerEm());
			assertEquals(a.getBBox(), b.getBBox());
			assertEquals(a.getAscent(), b.getAscent());
			assertEquals(a.getDescent(), b.getDescent());
			assertEquals(a.getSpaceAdvance(), b.getSpaceAdvance());
			assertEquals(a.getEmbeddingLicenseFlags(), b.getEmbeddingLicenseFlags());
			assertEquals(a.getDirection(), b.getDirection());
			assertEquals(a.getIndex(), b.getIndex());
			// Character→GID mapping: compare the entire BMP and representative characters from supplementary planes
			for (int c = 0; c <= 0xFFFF; ++c) {
				assertEquals(a.getCmapFormat().mapCharCode(c), b.getCmapFormat().mapCharCode(c), "code=" + c);
			}
			for (final int c : new int[] { 0x20B9F, 0x2000B, 0x10FFFF }) {
				assertEquals(a.getCmapFormat().mapCharCode(c), b.getCmapFormat().mapCharCode(c), "code=" + c);
			}
			assertEquals(a.canDisplay('あ'), b.canDisplay('あ'));
			assertEquals(a.canDisplay(0x1F600), b.canDisplay(0x1F600));
		}
	}

	@Test
	public void testStaleEntryMisses() throws Exception {
		final List<FontSource> parsed = parseSources();
		final File dbFile = Files.createTempFile(this.tempDir, "fonts", ".db").toFile();
		final FontIndex index = new FontIndex(dbFile);
		index.put(FONT, "key", 1, parsed);
		index.save();

		final FontIndex reloaded = new FontIndex(dbFile);
		// Changed scan conditions cause a miss (changes to face attributes do not leave stale values)
		assertNull(reloaded.lookup(FONT, "other-key"));
	}

	@Test
	public void testCorruptIndexIsIgnored() throws Exception {
		final File dbFile = Files.createTempFile(this.tempDir, "fonts", ".db").toFile();
		Files.write(dbFile.toPath(), new byte[] { 1, 2, 3, 4, 5 });
		final FontIndex index = new FontIndex(dbFile);
		assertNull(index.lookup(FONT, "key"), "壊れた索引は空として扱う");
	}
}
