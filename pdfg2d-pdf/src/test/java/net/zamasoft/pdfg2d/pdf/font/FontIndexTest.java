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
 * 永続フォント索引({@link FontIndex})の等価性テストです(2026-08-01)。
 *
 * <p>
 * 実フォント(IPAex明朝)を直接パースして構築したFontSourceと、
 * 索引にput→save→再読込→lookupで再構築したFontSourceが、選択・整形に
 * 使われる全メタデータと文字→GID写像で一致することを固定する。
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
			// 文字→GID写像: BMP全域+補助面の代表を突き合わせ
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
		// スキャン条件が変わればミス(face属性の変更が古い値で残らない)
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
