package net.zamasoft.pdfg2d.pdf.font.cid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * A ligature glyph (fi, ffi, ...) maps to all its characters in ToUnicode
 * (2026-10-06). One code per glyph made "Office" extract as "Ofice" and
 * "Purifier" as "Puriier".
 */
class LigatureToUnicodeTest {
	private static Text text(final String chars, final int[] glyphIds, final byte[] clusters) {
		return (Text) Proxy.newProxyInstance(Text.class.getClassLoader(), new Class<?>[] { Text.class },
				(proxy, method, args) -> switch (method.getName()) {
				case "getChars" -> chars.toCharArray();
				case "getCharCount" -> chars.length();
				case "getGlyphIds" -> glyphIds;
				case "getGlyphCount" -> glyphIds.length;
				case "getClusterLengths" -> clusters;
				default -> throw new UnsupportedOperationException(method.getName());
				});
	}

	@Test
	void ligaturesKeepAllTheirCharacters() {
		// O ffi c e: the ffi ligature is glyph 9 and covers three characters
		final Map<Integer, int[]> clusters = new HashMap<>();
		CIDUtils.recordClusters(text("Office", new int[] { 5, 9, 3, 4 }, new byte[] { 1, 3, 1, 1 }), clusters);
		assertEquals(1, clusters.size());
		assertArrayEquals(new int[] { 'f', 'f', 'i' }, clusters.get(9));
	}

	@Test
	void surrogatePairIsOneCharacter() {
		final Map<Integer, int[]> clusters = new HashMap<>();
		CIDUtils.recordClusters(text("𠮷x", new int[] { 7, 8 }, new byte[] { 2, 1 }), clusters);
		assertFalse(clusters.containsKey(7));
	}
}
